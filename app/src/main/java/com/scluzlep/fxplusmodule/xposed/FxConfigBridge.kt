package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import android.content.SharedPreferences
import com.scluzlep.fxplusmodule.config.ConfigContract
import com.scluzlep.fxplusmodule.config.ModuleConfig
import io.github.libxposed.api.XposedModule
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages FX-local `SharedPreferences("fxplusmodule_local", MODE_PRIVATE)` inside `nextapp.fx`,
 * notifying live listeners when settings change.
 */
object FxConfigBridge {

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(ModuleConfig, ModuleConfig) -> Unit>()

    @Volatile
    var current: ModuleConfig = ModuleConfig()
        private set

    @Volatile
    private var localPrefs: SharedPreferences? = null

    @Volatile
    private var localConfig: ModuleConfig = ModuleConfig()

    private val localListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadLocalLocked()
    }

    fun initRemote(@Suppress("UNUSED_PARAMETER") module: XposedModule) = Unit

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
            val loaded = ModuleConfig.fromSharedPreferences(prefs)
            localConfig = loaded
            val previous = current
            current = loaded
            transition = previous to loaded
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
                previousMs = maxOf(previous.updatedAtMs, localConfig.updatedAtMs)
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
            runCatching { localPrefs?.unregisterOnSharedPreferenceChangeListener(localListener) }
            localPrefs = null
        }
        listeners.clear()
    }

    private fun reloadLocalLocked() {
        val transition: Pair<ModuleConfig, ModuleConfig>
        synchronized(lock) {
            val prefs = localPrefs ?: return
            val loaded = runCatching { ModuleConfig.fromSharedPreferences(prefs) }
                .getOrDefault(localConfig)
            localConfig = loaded
            val previous = current
            current = loaded
            transition = previous to loaded
        }
        if (transition.first != transition.second) {
            notifyListeners(transition.first, transition.second)
        }
    }

    private fun notifyListeners(oldConfig: ModuleConfig, newConfig: ModuleConfig) {
        for (listener in listeners) {
            runCatching { listener(oldConfig, newConfig) }
        }
    }
}
