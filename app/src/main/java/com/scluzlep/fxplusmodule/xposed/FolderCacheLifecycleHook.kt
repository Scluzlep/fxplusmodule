package com.scluzlep.fxplusmodule.xposed

import android.app.Activity
import com.scluzlep.fxplusmodule.cache.PrefetchCacheManager
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Tracks live FX explorer tabs/windows and active image viewer sessions so cached
 * prefetch files are retained across same-folder views and evicted immediately when
 * the last window or viewer leaves that folder.
 */
object FolderCacheLifecycleHook {

    private val explorersLock = Any()
    private val trackedExplorers: MutableSet<Activity> =
        Collections.newSetFromMap(IdentityHashMap())

    @Volatile
    private var resolvedSymbols: ResolvedSymbols? = null

    fun install(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        resolvedSymbols = symbols
        val fl = symbols.folderLifecycle

        installer.install("folder:ExplorerActivity.onResume", fl.explorerOnResume) { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                registerExplorer(activity)
            }
            reconcileActiveFolders()
            result
        }

        installer.install("folder:ExplorerActivity.onDestroy", fl.explorerOnDestroy) { chain ->
            val activity = chain.thisObject as? Activity
            try {
                chain.proceed()
            } finally {
                if (activity != null) {
                    unregisterExplorer(activity)
                    reconcileActiveFolders()
                }
            }
        }

        installer.install("folder:WindowModel.setPath", fl.windowModelSetPathMethod) { chain ->
            val result = chain.proceed()
            reconcileActiveFolders()
            result
        }

        installer.install("folder:WindowModel.close", fl.windowModelCloseMethod) { chain ->
            val result = chain.proceed()
            reconcileActiveFolders()
            result
        }
    }

    fun clearOnHotReload() {
        resolvedSymbols = null
        synchronized(explorersLock) {
            trackedExplorers.clear()
        }
    }

    fun reconcileActiveFolders() {
        if (resolvedSymbols == null) return
        val manager = PrefetchCacheManager.peekInstance() ?: return
        val active = collectActiveFolders()
        val evicted = manager.retainOnlyFolders(active)
        if (evicted.isNotEmpty()) {
            ViewerPrefetchHook.onCacheKeysEvicted(evicted)
        }
    }

    fun collectActiveFolders(): Set<String> {
        val symbols = resolvedSymbols ?: return emptySet()
        val active = LinkedHashSet<String>()
        val explorers = synchronized(explorersLock) { trackedExplorers.toList() }

        for (activity in explorers) {
            if (activity.isFinishing || activity.isDestroyed) {
                unregisterExplorer(activity)
                continue
            }
            collectExplorerWindowFolders(symbols, activity, active)
        }

        active.addAll(ViewerPrefetchHook.getActiveViewerFolderKeys())
        return active
    }

    fun canonicalFolderKeyFromFxPath(symbols: ResolvedSymbols, fxPath: Any?): String? {
        if (fxPath == null) return null
        val fl = symbols.folderLifecycle
        val elementsField = fl.fxPathElementsField ?: return null
        val catalogClass = fl.dirCatalogClass ?: return null
        val elements = runCatching { elementsField.get(fxPath) as? Array<*> }.getOrNull()
        return canonicalFolderKeyFromElements(elements) { elem -> catalogClass.isInstance(elem) }
    }

    internal fun canonicalFolderKeyFromElements(
        elements: Array<*>?,
        isCatalog: (Any) -> Boolean
    ): String? {
        if (elements == null || elements.isEmpty()) return null
        val lastCatalogIdx = elements.indexOfLast { it != null && isCatalog(it) }
        if (lastCatalogIdx < 0) return null
        val catalog = elements[lastCatalogIdx] ?: return null
        val catalogId = "${catalog.javaClass.name}:$catalog"
        val rel = if (lastCatalogIdx + 1 < elements.size) {
            buildString {
                for (i in (lastCatalogIdx + 1) until elements.size) {
                    val seg = elements[i]?.toString()?.trim('/') ?: continue
                    if (seg.isEmpty()) continue
                    if (isNotEmpty()) append('/')
                    append(seg)
                }
            }
        } else {
            ""
        }
        return "$catalogId|$rel"
    }

    private fun registerExplorer(activity: Activity) {
        synchronized(explorersLock) {
            trackedExplorers.add(activity)
        }
    }

    private fun unregisterExplorer(activity: Activity) {
        synchronized(explorersLock) {
            trackedExplorers.remove(activity)
        }
    }

    private fun collectExplorerWindowFolders(
        symbols: ResolvedSymbols,
        activity: Activity,
        out: MutableSet<String>
    ) {
        val fl = symbols.folderLifecycle
        val wm = runCatching { fl.windowManagerField?.get(activity) }.getOrNull() ?: return
        val rawWindows = runCatching { fl.windowModelsField?.get(wm) as? List<*> }.getOrNull() ?: return
        val windows = snapshotListSafely(rawWindows)
        for (windowModel in windows) {
            val contentModel = runCatching {
                fl.windowModelCurrentContentMethod?.invoke(windowModel)
            }.getOrNull() ?: continue
            val fxPath = runCatching {
                fl.contentModelPathField?.get(contentModel)
            }.getOrNull() ?: continue
            val folderKey = canonicalFolderKeyFromFxPath(symbols, fxPath)
            if (!folderKey.isNullOrBlank()) {
                out.add(folderKey)
            }
        }
    }

    private fun snapshotListSafely(rawList: List<*>): List<Any> {
        repeat(3) {
            try {
                val copy = ArrayList<Any>(rawList.size)
                for (item in rawList) {
                    if (item != null) copy.add(item)
                }
                return copy
            } catch (_: ConcurrentModificationException) {
            } catch (_: IndexOutOfBoundsException) {
            }
        }
        return emptyList()
    }
}
