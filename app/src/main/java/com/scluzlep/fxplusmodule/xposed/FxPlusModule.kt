package com.scluzlep.fxplusmodule.xposed

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import com.scluzlep.fxplusmodule.cache.PrefetchCacheManager
import com.scluzlep.fxplusmodule.config.ConfigContract
import com.scluzlep.fxplusmodule.config.ModuleConfig
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Executable

/**
 * Main libxposed API 102 module entry for `com.scluzlep.fxplusmodule`.
 * Supports dual FX profiles (`9012` / `9108`) with structural fallback, live config
 * synchronization via `RemotePreferences` + `fxplusmodule_local`, and full hot-reload
 * handle replacement and executor cleanup.
 */
class FxPlusModule : XposedModule() {

    private val activeHandles = linkedMapOf<String, XposedInterface.HookHandle>()

    @Volatile
    private var isSystemServerProcess: Boolean = false

    @Volatile
    private var currentPackageName: String = ""

    @Volatile
    private var currentProcessName: String = ""

    @Volatile
    private var configListener: ((ModuleConfig, ModuleConfig) -> Unit)? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        isSystemServerProcess = param.isSystemServer
        currentProcessName = param.processName
        if (param.isSystemServer) return
        log(Log.INFO, TAG, "Loaded API 102 module in process=${param.processName}")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (isSystemServerProcess) return
        if (param.isFirstPackage) {
            currentPackageName = param.packageName
        }

        if (param.packageName == ConfigContract.MODULE_PACKAGE && param.isFirstPackage) {
            installSelfStatusHook(param.classLoader, oldById = null)
            return
        }

        if (param.packageName == ConfigContract.TARGET_FX_PACKAGE && param.isFirstPackage) {
            setupTargetHooks(
                classLoader = param.classLoader,
                appInfo = param.applicationInfo,
                oldById = null
            )
        }
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        configListener?.let { FxConfigBridge.removeListener(it) }
        configListener = null
        FxConfigBridge.detach()
        ViewerPrefetchHook.clearTransientStateOnHotReload()
        PrefetchCacheManager.shutdownShared()
        PrivilegedStorageClient.resetOnHotReload()
        param.setSavedInstanceState(currentPackageName.ifBlank { null })
        log(Log.INFO, TAG, "Hot reloading initiated for pkg=$currentPackageName")
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        currentProcessName = param.processName
        if (param.isSystemServer) return

        val app = currentApplicationByReflection()
        val pkgName = (param.savedInstanceState as? String)?.takeIf { it.isNotBlank() }
            ?: app?.packageName
            ?: param.processName.substringBefore(':')
        currentPackageName = pkgName

        val appClassLoader = app?.classLoader ?: javaClass.classLoader ?: ClassLoader.getSystemClassLoader()
        val oldById = linkedMapOf<String, XposedInterface.HookHandle>()
        param.oldHookHandles.forEachIndexed { index, handle ->
            val key = handle.id ?: "legacy#$index:${handle.executable.toGenericString()}"
            oldById[key] = handle
        }

        if (pkgName == ConfigContract.MODULE_PACKAGE) {
            installSelfStatusHook(appClassLoader, oldById)
            unhookRemaining(oldById)
            return
        }

        if (pkgName == ConfigContract.TARGET_FX_PACKAGE) {
            setupTargetHooks(
                classLoader = appClassLoader,
                appInfo = app?.applicationInfo,
                oldById = oldById
            )
            app?.applicationContext?.let { ctx ->
                attachHostContext(ctx)
            }
        }
        unhookRemaining(oldById)
        log(Log.INFO, TAG, "Hot reload completed for pkg=$pkgName process=$currentProcessName")
    }

    private fun installSelfStatusHook(
        classLoader: ClassLoader,
        oldById: MutableMap<String, XposedInterface.HookHandle>?
    ) {
        runCatching {
            val clazz = Class.forName("${ConfigContract.MODULE_PACKAGE}.xposed.ModuleStatus", false, classLoader)
            val method = clazz.getDeclaredMethod("isSelfHooked")
            installOrReplaceHook(
                id = "self:ModuleStatus#isSelfHooked",
                executable = method,
                oldById = oldById
            ) { true }
        }.onFailure { err ->
            log(Log.WARN, TAG, "Failed to install ModuleStatus.isSelfHooked hook", err)
        }
    }

    private fun setupTargetHooks(
        classLoader: ClassLoader,
        appInfo: android.content.pm.ApplicationInfo?,
        oldById: MutableMap<String, XposedInterface.HookHandle>?
    ) {
        FxConfigBridge.initRemote(this)
        val listener: (ModuleConfig, ModuleConfig) -> Unit = { _, newConfig ->
            PrefetchCacheManager.peekInstance()?.onConfigUpdated(newConfig)
        }
        configListener = listener
        FxConfigBridge.addListener(listener)

        val currentApp = currentApplicationByReflection()
        currentApp?.applicationContext?.let { attachHostContext(it) }

        val versionCode = FxSymbolResolver.detectVersionCode(currentApp, appInfo)
        val symbols = FxSymbolResolver.resolve(classLoader, versionCode)

        val installer = object : FxHookInstaller {
            override fun install(id: String, executable: Executable?, hooker: XposedInterface.Hooker) {
                if (executable == null) {
                    if (FxConfigBridge.current.debugLogging) {
                        log(Log.DEBUG, TAG, "Skipping unresolved hook target: $id")
                    }
                    return
                }
                runCatching {
                    installOrReplaceHook(id, executable, oldById, hooker)
                }.onFailure { err ->
                    log(Log.WARN, TAG, "Hook install failed for $id", err)
                }
            }

            override fun logInfo(message: String) {
                log(Log.INFO, TAG, message)
            }

            override fun logWarn(message: String, error: Throwable?) {
                if (error != null) {
                    log(Log.WARN, TAG, message, error)
                } else {
                    log(Log.WARN, TAG, message)
                }
            }

            override fun logDebug(message: String) {
                if (FxConfigBridge.current.debugLogging) {
                    log(Log.DEBUG, TAG, message)
                }
            }
        }

        // Lifecycle hooks ensure FX Context attaches early for local prefs & disk cache.
        installer.install(
            "app:Application.onCreate",
            runCatching { Application::class.java.getDeclaredMethod("onCreate") }.getOrNull()
        ) { chain ->
            val res = chain.proceed()
            (chain.thisObject as? Application)?.applicationContext?.let { attachHostContext(it) }
            res
        }

        installer.install(
            "app:Activity.onCreate",
            runCatching { Activity::class.java.getDeclaredMethod("onCreate", android.os.Bundle::class.java) }.getOrNull()
        ) { chain ->
            (chain.thisObject as? Activity)?.applicationContext?.let { attachHostContext(it) }
            chain.proceed()
        }

        SiblingSwipeHook.install(symbols, installer)
        ViewerPrefetchHook.install(symbols, installer)
        RemoteThumbnailHook.install(symbols, installer)
        AndroidDataAccessHook.install(symbols, installer)
        FxSettingsInjectorHook.install(symbols, installer)
        BonusLimitsHook.install(symbols, installer)

        log(
            Log.INFO,
            TAG,
            "Installed FX Plus hooks profile=${symbols.profile} version=$versionCode handles=${activeHandles.size}"
        )
    }

    private fun attachHostContext(context: Context) {
        val appCtx = context.applicationContext ?: context
        FxConfigBridge.attachContext(appCtx)
        AndroidDataAccessHook.attachContext(appCtx)
        PrefetchCacheManager.getOrCreate(appCtx) { FxConfigBridge.current }
    }

    private fun installOrReplaceHook(
        id: String,
        executable: Executable,
        oldById: MutableMap<String, XposedInterface.HookHandle>?,
        hooker: XposedInterface.Hooker
    ) {
        if (activeHandles.containsKey(id)) return
        val oldHandle = oldById?.remove(id)
        val handle = if (oldHandle != null) {
            runCatching {
                oldHandle.replaceHook(hooker)
            }.getOrElse {
                runCatching { oldHandle.unhook() }
                hook(executable)
                    .setId(id)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(hooker)
            }
        } else {
            hook(executable)
                .setId(id)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(hooker)
        }
        activeHandles[id] = handle
    }

    private fun unhookRemaining(oldById: MutableMap<String, XposedInterface.HookHandle>) {
        for (handle in oldById.values) {
            runCatching { handle.unhook() }
        }
        oldById.clear()
    }

    private fun currentApplicationByReflection(): Application? {
        return runCatching {
            val atClass = Class.forName("android.app.ActivityThread")
            atClass.getMethod("currentApplication").invoke(null) as? Application
        }.getOrNull()
    }

    companion object {
        private const val TAG = "FxPlusModule"
    }
}
