package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import android.content.SharedPreferences
import com.scluzlep.fxplusmodule.config.ConfigContract
import com.scluzlep.fxplusmodule.config.ModuleConfig
import io.github.libxposed.api.XposedModule
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bridges libxposed `RemotePreferences("fxplusmodule")` and FX-local
 * `SharedPreferences("fxplusmodule_local", MODE_PRIVATE)` inside `nextapp.fx`,
 * resolving conflicts deterministically by monotonic `updatedAtMs`.
 */
object FxConfigBridge {

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(ModuleConfig, ModuleConfig) -> Unit>()

    @Volatile
    var current: ModuleConfig = ModuleConfig()
        private set

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    @Volatile
    private var localPrefs: SharedPreferences? = null

    @Volatile
    private var remoteConfig: ModuleConfig = ModuleConfig()

    @Volatile
    private var localConfig: ModuleConfig = ModuleConfig()

    private val remoteListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadRemoteLocked()
    }

    private val localListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadLocalLocked()
    }

    fun initRemote(module: XposedModule) {
        val prefs = runCatching {
            module.getRemotePreferences(ConfigContract.PREFS_NAME_REMOTE)
        }.getOrNull() ?: return

        val transition: Pair<ModuleConfig, ModuleConfig>
        synchronized(lock) {
            if (remotePrefs !== prefs) {
                runCatching { remotePrefs?.unregisterOnSharedPreferenceChangeListener(remoteListener) }
                remotePrefs = prefs
                runCatching { prefs.registerOnSharedPreferenceChangeListener(remoteListener) }
            }
            remoteConfig = runCatching { ModuleConfig.fromSharedPreferences(prefs) }
                .getOrDefault(ModuleConfig())
            transition = recomputeLocked()
        }
        if (transition.first != transition.second) {
            notifyListeners(transition.first, transition.second)
        }
    }

    fun attachContext(context: Context) {
        val appCtx = context.applicationContext ?: context
        val prefs = runCatching {
            appCtx.getSharedPreferences(ConfigContract.PREFS_NAME_LOCAL, Context.MODE_PRIVATE)
        }.getOrNull() ?: return

        val transition: Pair<ModuleConfig, ModuleConfig>
        synchronized(lock) {
            if (localPrefs !== prefs) {
                runCatching { localPrefs?.unregisterOnSharedPreferenceChangeListener(localListener) }
                localPrefs = prefs
                runCatching { prefs.registerOnSharedPreferenceChangeListener(localListener) }
            }
            localConfig = ModuleConfig.fromSharedPreferences(prefs)
            transition = recomputeLocked()
        }
        if (transition.first != transition.second) {
            notifyListeners(transition.first, transition.second)
        }
    }

    fun updateFromFxDialog(context: Context, transform: (ModuleConfig) -> ModuleConfig): ModuleConfig {
        attachContext(context)
        val updated: ModuleConfig
        val previous: ModuleConfig
        synchronized(lock) {
            previous = current
            val candidate = transform(previous)
            val stamp = ModuleConfig.nextTimestamp(
                previousMs = maxOf(previous.updatedAtMs, remoteConfig.updatedAtMs, localConfig.updatedAtMs)
            )
            updated = candidate.sanitized().copy(updatedAtMs = stamp)
            localConfig = updated
            current = updated
            localPrefs?.let { prefs ->
                runCatching { ModuleConfig.saveToSharedPreferences(prefs, updated, commitSync = true) }
            }
        }
        if (previous != updated) {
            notifyListeners(previous, updated)
        }
        return updated
    }

    fun addListener(listener: (oldConfig: ModuleConfig, newConfig: ModuleConfig) -> Unit) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: (oldConfig: ModuleConfig, newConfig: ModuleConfig) -> Unit) {
        listeners.remove(listener)
    }

    fun detach() {
        synchronized(lock) {
            runCatching { remotePrefs?.unregisterOnSharedPreferenceChangeListener(remoteListener) }
            runCatching { localPrefs?.unregisterOnSharedPreferenceChangeListener(localListener) }
            remotePrefs = null
            localPrefs = null
        }
        listeners.clear()
    }

    private fun reloadRemoteLocked() {
        val transition: Pair<ModuleConfig, ModuleConfig>
        synchronized(lock) {
            val prefs = remotePrefs ?: return
            remoteConfig = runCatching { ModuleConfig.fromSharedPreferences(prefs) }
                .getOrDefault(remoteConfig)
            transition = recomputeLocked()
        }
        if (transition.first != transition.second) {
            notifyListeners(transition.first, transition.second)
        }
    }

    private fun reloadLocalLocked() {
        val transition: Pair<ModuleConfig, ModuleConfig>
        synchronized(lock) {
            val prefs = localPrefs ?: return
            localConfig = runCatching { ModuleConfig.fromSharedPreferences(prefs) }
                .getOrDefault(localConfig)
            transition = recomputeLocked()
        }
        if (transition.first != transition.second) {
            notifyListeners(transition.first, transition.second)
        }
    }

    private fun recomputeLocked(): Pair<ModuleConfig, ModuleConfig> {
        val merged = ModuleConfig.mergeByTimestamp(remoteConfig, localConfig)
        if (remoteConfig.updatedAtMs > localConfig.updatedAtMs) {
            localConfig = merged
            localPrefs?.let { prefs ->
                runCatching { ModuleConfig.saveToSharedPreferences(prefs, merged, commitSync = false) }
            }
        }
        val previous = current
        current = merged
        return previous to merged
    }

    private fun notifyListeners(oldConfig: ModuleConfig, newConfig: ModuleConfig) {
        for (listener in listeners) {
            runCatching { listener(oldConfig, newConfig) }
        }
    }
}
