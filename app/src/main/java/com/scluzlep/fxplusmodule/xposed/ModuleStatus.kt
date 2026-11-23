package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.scluzlep.fxplusmodule.config.ConfigContract
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class TargetProcessInfo(
    val uid: Int,
    val pid: Int,
    val processName: String,
    val state: String,
    val loadedVersionCode: Long
)

data class FxPackageInfo(
    val installed: Boolean = false,
    val versionName: String = "",
    val versionCode: Long = 0L,
    val compatibilityLabel: String = "未安装"
)

data class XposedStatusSnapshot(
    val serviceBound: Boolean = false,
    val selfHooked: Boolean = false,
    val apiVersion: Int = 0,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val frameworkVersionCode: Long = 0L,
    val scope: List<String> = emptyList(),
    val runningTargets: List<TargetProcessInfo> = emptyList(),
    val fxPackage: FxPackageInfo = FxPackageInfo(),
    val lastActionMessage: String = ""
) {
    val isActive: Boolean
        get() = serviceBound || selfHooked

    val fxInScope: Boolean
        get() = ConfigContract.TARGET_FX_PACKAGE in scope

    val fxTargets: List<TargetProcessInfo>
        get() = runningTargets.filter { isFxProcess(it.processName) }

    val staleTargetCount: Int
        get() = fxTargets.count { it.state == "STALE" }

    companion object {
        fun isFxProcess(processName: String): Boolean {
            return processName == ConfigContract.TARGET_FX_PACKAGE ||
                processName.startsWith("${ConfigContract.TARGET_FX_PACKAGE}:")
        }
    }
}

object ModuleStatus {
    private val initialized = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var boundService: XposedService? = null

    @Volatile
    private var serviceBindListener: (() -> Unit)? = null

    private val _status = MutableStateFlow(XposedStatusSnapshot(selfHooked = isSelfHooked()))
    val status: StateFlow<XposedStatusSnapshot> = _status.asStateFlow()

    /**
     * Hooked inside the module's own process when self-hooked by LSPosed.
     */
    @JvmStatic
    fun isSelfHooked(): Boolean = false

    @JvmStatic
    fun isActive(): Boolean = _status.value.isActive || isSelfHooked()

    fun setOnServiceBindListener(listener: (() -> Unit)?) {
        serviceBindListener = listener
        if (boundService != null) {
            listener?.invoke()
        }
    }

    fun getRemotePreferences(name: String = ConfigContract.PREFS_NAME_REMOTE): SharedPreferences? {
        val service = boundService ?: return null
        return runCatching { service.getRemotePreferences(name) }.getOrNull()
    }

    fun init(context: Context? = null) {
        if (context != null) {
            appContext = context.applicationContext
        }
        if (!initialized.compareAndSet(false, true)) {
            refresh(context)
            return
        }
        refresh(context)
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    boundService = service
                    refresh(appContext, "已连接 libxposed API ${service.apiVersion} 服务")
                    serviceBindListener?.invoke()
                }

                override fun onServiceDied(service: XposedService) {
                    if (boundService === service) {
                        boundService = null
                    }
                    refresh(appContext, "Xposed 服务连接已断开")
                }
            })
        }
    }

    fun refresh(
        context: Context? = appContext,
        actionMessage: String = _status.value.lastActionMessage
    ) {
        if (context != null) {
            appContext = context.applicationContext
        }
        val fxInfo = queryFxPackageInfo(appContext)
        val service = boundService
        val selfHooked = isSelfHooked()
        if (service == null) {
            _status.value = XposedStatusSnapshot(
                serviceBound = false,
                selfHooked = selfHooked,
                fxPackage = fxInfo,
                lastActionMessage = actionMessage
            )
            return
        }

        val scopeList = runCatching { service.scope.toList() }.getOrDefault(emptyList())
        val targets = runCatching {
            service.runningTargets.map { target ->
                TargetProcessInfo(
                    uid = target.uid,
                    pid = target.pid,
                    processName = target.processName,
                    state = target.state.name,
                    loadedVersionCode = target.loadedVersionCode
                )
            }
        }.getOrDefault(emptyList())

        _status.value = XposedStatusSnapshot(
            serviceBound = true,
            selfHooked = selfHooked,
            apiVersion = runCatching { service.apiVersion }.getOrDefault(0),
            frameworkName = runCatching { service.frameworkName }.getOrDefault(""),
            frameworkVersion = runCatching { service.frameworkVersion }.getOrDefault(""),
            frameworkVersionCode = runCatching { service.frameworkVersionCode }.getOrDefault(0L),
            scope = scopeList,
            runningTargets = targets,
            fxPackage = fxInfo,
            lastActionMessage = actionMessage
        )
    }

    fun requestFxScope() {
        val service = boundService
        if (service == null) {
            refresh(actionMessage = "未连接 Xposed 服务，请在管理器中启用模块")
            return
        }
        val existingScope = runCatching { service.scope }.getOrDefault(emptyList())
        if (ConfigContract.TARGET_FX_PACKAGE in existingScope) {
            refresh(actionMessage = "FX 作用域已启用")
            return
        }
        refresh(actionMessage = "正在申请 FX 作用域...")
        runCatching {
            service.requestScope(
                listOf(ConfigContract.TARGET_FX_PACKAGE),
                object : XposedService.OnScopeEventListener {
                    override fun onScopeRequestApproved(approved: List<String>) {
                        val granted = approved.contains(ConfigContract.TARGET_FX_PACKAGE)
                        refresh(
                            actionMessage = if (granted) {
                                "已申请并授予 FX 作用域"
                            } else {
                                "作用域请求已完成"
                            }
                        )
                    }

                    override fun onScopeRequestFailed(message: String) {
                        val detail = message.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
                        refresh(actionMessage = "申请作用域失败$detail")
                    }
                }
            )
        }.onFailure { err ->
            refresh(actionMessage = "申请作用域失败：${err.message}")
        }
    }

    fun hotReloadFx(reloadAllIfNoneStale: Boolean = true) {
        val service = boundService
        if (service == null) {
            refresh(actionMessage = "未连接 Xposed 服务，请在管理器中启用模块")
            return
        }

        val runningFx = runCatching {
            service.runningTargets.filter { XposedStatusSnapshot.isFxProcess(it.processName) }
        }.getOrElse { err ->
            refresh(actionMessage = "读取 FX 进程失败：${err.message}")
            return
        }

        if (runningFx.isEmpty()) {
            val inScope = runCatching {
                ConfigContract.TARGET_FX_PACKAGE in service.scope
            }.getOrDefault(false)
            val msg = if (inScope) {
                "FX 当前未运行，打开 FX 即生效"
            } else {
                "FX 未运行且未在作用域内，请先申请作用域"
            }
            refresh(actionMessage = msg)
            return
        }

        val candidates = runningFx.filter { it.state == HookedTarget.State.STALE }.ifEmpty {
            if (reloadAllIfNoneStale) runningFx else emptyList()
        }
        if (candidates.isEmpty()) {
            refresh(actionMessage = "FX 进程已是最新模块状态")
            return
        }

        val total = candidates.size
        val remaining = AtomicInteger(total)
        val okCount = AtomicInteger(0)
        val notes = mutableListOf<String>()
        refresh(actionMessage = "正在热重载 FX...")

        for (target in candidates) {
            runCatching {
                service.hotReloadModule(target, null) { completedTarget, result ->
                    if (result.status() == HotReloadResult.Status.SUCCEEDED) {
                        okCount.incrementAndGet()
                    } else {
                        synchronized(notes) {
                            val msg = result.message().orEmpty()
                            notes += if (msg.isBlank()) {
                                "${completedTarget.processName}: ${result.status().name}"
                            } else {
                                "${completedTarget.processName}: ${result.status().name} $msg"
                            }
                        }
                    }
                    if (remaining.decrementAndGet() == 0) {
                        val summary = if (notes.isEmpty()) {
                            "FX 热重载完成：${okCount.get()}/$total 成功"
                        } else {
                            "FX 热重载 ${okCount.get()}/$total 成功 · ${notes.take(2).joinToString("; ")}"
                        }
                        refresh(actionMessage = summary)
                    }
                }
            }.onFailure { err ->
                synchronized(notes) {
                    notes += "${target.processName}: ${err.message}"
                }
                if (remaining.decrementAndGet() == 0) {
                    refresh(
                        actionMessage = "FX 热重载 ${okCount.get()}/$total 成功 · ${notes.take(2).joinToString("; ")}"
                    )
                }
            }
        }
    }

    fun queryFxPackageInfo(context: Context?): FxPackageInfo {
        if (context == null) return FxPackageInfo()
        return runCatching {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(ConfigContract.TARGET_FX_PACKAGE, 0)
            val vCode = PackageInfoCompat.getLongVersionCode(info)
            val vName = info.versionName.orEmpty()
            val label = when (vCode) {
                ConfigContract.FX_VERSION_CODE_9012 -> "已验证版本 9012"
                ConfigContract.FX_VERSION_CODE_9108 -> "已验证版本 9108"
                else -> "未验证版本 $vCode"
            }
            FxPackageInfo(
                installed = true,
                versionName = vName,
                versionCode = vCode,
                compatibilityLabel = label
            )
        }.getOrElse { err ->
            if (err is PackageManager.NameNotFoundException) {
                FxPackageInfo(installed = false, compatibilityLabel = "未安装")
            } else {
                FxPackageInfo(installed = false, compatibilityLabel = "无法检测")
            }
        }
    }
}
