package com.scluzlep.fxplusmodule.xposed

import android.app.Activity
import android.content.Context
import android.media.ExifInterface
import android.view.View
import com.scluzlep.fxplusmodule.cache.PrefetchCacheManager
import com.scluzlep.fxplusmodule.cache.PrefetchItem
import io.github.libxposed.api.XposedInterface
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Field
import java.util.IdentityHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Integrates [PrefetchCacheManager] into `ImageViewerActivity` for `9012` and `9108`:
 * - Intercepts redundant `ee.a.T` / `qg.b.u` remote temp mirror downloads without touching `q5` / `m2`.
 * - Serves cached disk `.bin` bytes at 100% original resolution into `V1` / `c.read` and GIF loaders.
 * - Recovers EXIF orientation for remote JPEGs and `Android/data` local JPEGs.
 * - Schedules direction-aware adjacent prefetch (`COUNT_ONLY` `N+-k`, `CAPACITY_ONLY`, `BOTH`) and
 *   cancels pending transfers cleanly on rapid swipe / destroy / hot reload.
 */
object ViewerPrefetchHook {

    private class ViewerSession(
        val sessionId: String,
        val appContext: Context
    ) {
        @Volatile
        var rawItems: List<Any> = emptyList()

        @Volatile
        var prefetchItems: List<PrefetchItem> = emptyList()

        @Volatile
        var currentIndex: Int = -1

        @Volatile
        var lastDirection: Int = 1

        @Volatile
        var currentFolderKey: String? = null
    }

    private const val STREAM_BUFFER_BYTES = 65_536

    private val sessionsLock = Any()
    private val sessions = IdentityHashMap<Activity, ViewerSession>()
    private val activeCachedByIdentity = ConcurrentHashMap<String, File>()
    private val scopedStreamFile = ThreadLocal<Pair<Any, File>?>()

    @Volatile
    private var resolvedSymbols: ResolvedSymbols? = null

    @Volatile
    private var resolvedProgressBarField: Field? = null

    fun install(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        resolvedSymbols = symbols
        installTaskThreadFactory(symbols, installer)
        resolvedProgressBarField = resolveProgressBarField(symbols)
        val s = symbols.sibling
        val v = symbols.viewer

        installer.install("viewer:onCreate", v.viewerOnCreate) { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            if (activity != null && s.imageViewerActivityClass?.isInstance(activity) == true) {
                trackInitialViewerFolder(symbols, activity)
            }
            result
        }

        installer.install("viewer:mirrorRemoteTempFile", v.mirrorRemoteTempFile) { chain ->
            val activity = chain.thisObject as? Activity
            val viewerClass = s.imageViewerActivityClass
            if (activity == null || viewerClass == null || !viewerClass.isInstance(activity)) {
                return@install chain.proceed()
            }
            val item = chain.getArg(0) ?: return@install chain.proceed()
            val appCtx = activity.applicationContext ?: activity
            val remote = isRemoteFileItem(symbols, item)
            val prefetchItem = toPrefetchItem(symbols, appCtx, item, isRemote = remote)
            if (prefetchItem != null && updateSessionFolderKey(activity, appCtx, prefetchItem.folderKey)) {
                FolderCacheLifecycleHook.reconcileActiveFolders()
            }
            if (!FxConfigBridge.current.enableAdjacentPrefetch || !remote) {
                return@install chain.proceed()
            }

            val manager = PrefetchCacheManager.getOrCreate(appCtx) {
                FxConfigBridge.current
            }
            if (prefetchItem != null) {
                val cached = manager.peekCachedFile(prefetchItem)
                    ?: manager.awaitOrDownload(prefetchItem)
                if (cached != null && cached.exists() && cached.length() > 0L) {
                    recordCachedFile(symbols, item, cached)
                    ensureCachedDisplayVisible(symbols, activity)
                    installer.logDebug("Served remote image via single-flight cache: ${cached.name}")
                    return@install null
                }
            }
            chain.proceed()
        }

        installer.install("viewer:onDestroy", v.viewerOnDestroy) { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && s.imageViewerActivityClass?.isInstance(activity) == true) {
                clearSession(activity)
            }
            chain.proceed()
        }

        for ((idx, itemClass) in v.remoteItemClasses.withIndex()) {
            val openMethod = runCatching {
                val m = s.openInputStream ?: return@runCatching null
                itemClass.getDeclaredMethod(m.name, Context::class.java).apply { isAccessible = true }
            }.getOrNull() ?: continue
            installer.install("viewer:remoteStream#$idx:${itemClass.name}", openMethod) { chain ->
                val pair = scopedStreamFile.get()
                if (pair != null && pair.first === chain.thisObject && pair.second.exists()) {
                    return@install BufferedInputStream(FileInputStream(pair.second), STREAM_BUFFER_BYTES)
                }
                chain.proceed()
            }
        }

        when (symbols.profile) {
            FxProfile.FX_9012 -> install9012Hooks(symbols, installer)
            FxProfile.FX_9108 -> install9108Hooks(symbols, installer)
            FxProfile.STRUCTURAL_FALLBACK -> Unit
        }
    }

    private fun install9012Hooks(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val s = symbols.sibling
        val v = symbols.viewer

        installer.install("viewer:9012:V1", v.viewerOpenItemStream) { chain ->
            val item = chain.getArg(0)
            val cached = lookupCachedFile(symbols, item)
            if (cached != null && cached.exists() && cached.length() > 0L) {
                return@install BufferedInputStream(FileInputStream(cached), STREAM_BUFFER_BYTES)
            }
            chain.proceed()
        }

        val scopedViewerLoader = XposedInterface.Hooker { chain ->
            val holder = chain.getArg(1)
            val item = holder?.let { runCatching { s.itemHolderFileItemField?.get(it) }.getOrNull() }
            val cached = lookupCachedFile(symbols, item)
            if (item != null && cached != null && cached.exists()) {
                val prev = scopedStreamFile.get()
                scopedStreamFile.set(item to cached)
                try {
                    chain.proceed()
                } finally {
                    scopedStreamFile.set(prev)
                }
            } else {
                chain.proceed()
            }
        }
        installer.install("viewer:9012:j1", v.viewerLoadMedia, scopedViewerLoader)
        installer.install("viewer:9012:l1", v.viewerLoadGif, scopedViewerLoader)

        installer.install("viewer:9012:d2", v.viewerDecodeItem) { chain ->
            val ctx = chain.getArg(0) as? Context ?: return@install chain.proceed()
            val item = chain.getArg(1) ?: return@install chain.proceed()
            val rotDeg = (chain.getArg(2) as? Int) ?: 0
            if (rotDeg == 0) {
                val recovered = recoverJpegExifRotation(symbols, ctx, item)
                if (recovered != 0) {
                    return@install chain.proceed(arrayOf(ctx, item, recovered))
                }
            }
            chain.proceed()
        }

        installer.install("viewer:9012:siblingsLoaded", s.siblingCallbackLoadedMethod) { chain ->
            val result = chain.proceed()
            val cb = chain.thisObject
            val activity = cb?.let { runCatching { s.siblingCallbackActivityField?.get(it) as? Activity }.getOrNull() }
            val arr = chain.getArg(0) as? Array<*>
            val index = (chain.getArg(1) as? Int) ?: -1
            if (activity != null && arr != null && index in arr.indices) {
                onSiblingsReady(activity, arr.filterNotNull(), index)
            }
            result
        }

        installer.install("viewer:9012:swipeRequest", s.displayListenerRequestMethod) { chain ->
            val result = chain.proceed()
            val listener = chain.thisObject
            val activity = listener?.let { runCatching { s.displayListenerHolderField?.get(it) as? Activity }.getOrNull() }
            val newIndex = (chain.getArg(1) as? Int) ?: -1
            if (activity != null && newIndex >= 0) {
                onSwipeToIndex(activity, newIndex, commit = false)
            }
            result
        }

        installer.install("viewer:9012:swipeCommit", s.displayListenerCommitMethod) { chain ->
            val result = chain.proceed()
            val listener = chain.thisObject
            val activity = listener?.let { runCatching { s.displayListenerHolderField?.get(it) as? Activity }.getOrNull() }
            val newIndex = (chain.getArg(1) as? Int) ?: -1
            if (activity != null && newIndex >= 0) {
                onSwipeToIndex(activity, newIndex, commit = true)
            }
            result
        }
    }

    private fun install9108Hooks(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val s = symbols.sibling
        val v = symbols.viewer

        installer.install("viewer:9108:c.read", v.viewerStreamProviderRead) { chain ->
            val provider = chain.thisObject
            val item = provider?.let { runCatching { v.viewerStreamProviderItemField?.get(it) }.getOrNull() }
            val cached = lookupCachedFile(symbols, item)
            if (cached != null && cached.exists() && cached.length() > 0L) {
                return@install BufferedInputStream(FileInputStream(cached), STREAM_BUFFER_BYTES)
            }
            chain.proceed()
        }

        installer.install("viewer:9108:C", v.viewerLoadMediaAndBitmap) { chain ->
            val holder = chain.getArg(1)
            val item = holder?.let { runCatching { s.itemHolderFileItemField?.get(it) }.getOrNull() }
            val cached = lookupCachedFile(symbols, item)
            if (item != null && cached != null && cached.exists()) {
                val prev = scopedStreamFile.get()
                scopedStreamFile.set(item to cached)
                try {
                    chain.proceed()
                } finally {
                    scopedStreamFile.set(prev)
                }
            } else {
                chain.proceed()
            }
        }

        installer.install("viewer:9108:I", v.viewerDecodeItem) { chain ->
            val activity = chain.getArg(0) as? Context ?: return@install chain.proceed()
            val item = chain.getArg(1) ?: return@install chain.proceed()
            val rotDeg = (chain.getArg(2) as? Int) ?: 0
            if (rotDeg == 0) {
                val recovered = recoverJpegExifRotation(symbols, activity, item)
                if (recovered != 0) {
                    return@install chain.proceed(arrayOf(activity, item, recovered))
                }
            }
            chain.proceed()
        }

        installer.install("viewer:9108:l.e", s.displayListenerRequestMethod) { chain ->
            val result = chain.proceed()
            val listener = chain.thisObject
            val holder = listener?.let { runCatching { s.displayListenerHolderField?.get(it) }.getOrNull() }
            val activity = holder as? Activity
            val newIndex = (chain.getArg(1) as? Int) ?: -1
            if (activity != null && s.imageViewerActivityClass?.isInstance(activity) == true && newIndex >= 0) {
                onSwipeToIndex(activity, newIndex, commit = false)
            }
            result
        }

        installer.install("viewer:9108:ImageDisplay.k", s.displayCommitMethod) { chain ->
            val result = chain.proceed()
            val display = chain.thisObject
            if (display != null) {
                runCatching {
                    val listener = s.displayListenerField?.get(display)
                    val activity = listener?.let { s.displayListenerHolderField?.get(it) as? Activity }
                    val newIndex = s.displaySiblingIndexField?.getInt(
                        s.viewerCurrentDisplayField?.get(activity) ?: display
                    ) ?: -1
                    if (activity != null && newIndex >= 0) {
                        onSwipeToIndex(activity, newIndex, commit = true)
                    }
                }
            }
            result
        }
    }

    fun onSiblingsReady(activity: Activity, rawItems: List<Any>, index: Int) {
        val symbols = resolvedSymbols ?: return
        if (rawItems.isEmpty() || index !in rawItems.indices) return
        val appCtx = activity.applicationContext ?: activity
        val session = getOrCreateSession(activity, appCtx)
        val built = rawItems.mapNotNull { item ->
            val remote = isRemoteFileItem(symbols, item)
            toPrefetchItem(symbols, appCtx, item, isRemote = remote)
        }
        if (built.size != rawItems.size) {
            session.rawItems = rawItems
            session.prefetchItems = rawItems.map { item ->
                toPrefetchItem(symbols, appCtx, item, isRemote = isRemoteFileItem(symbols, item))
                    ?: PrefetchItem("unknown", item.toString(), 0L, 0L, isRemote = false)
            }
        } else {
            session.rawItems = rawItems
            session.prefetchItems = built
        }
        session.currentIndex = index
        session.lastDirection = 1
        val folderKey = session.prefetchItems.getOrNull(index)?.folderKey
        if (folderKey != null && session.currentFolderKey != folderKey) {
            session.currentFolderKey = folderKey
            FolderCacheLifecycleHook.reconcileActiveFolders()
        }

        if (FxConfigBridge.current.enableAdjacentPrefetch) {
            val manager = PrefetchCacheManager.getOrCreate(appCtx) { FxConfigBridge.current }
            manager.scheduleWindow(session.prefetchItems, index, session.lastDirection, session.sessionId)
        }
    }

    private fun onSwipeToIndex(activity: Activity, newIndex: Int, commit: Boolean) {
        val session = synchronized(sessionsLock) { sessions[activity] } ?: return
        val total = session.prefetchItems.size
        if (total <= 1 || newIndex !in 0 until total) return

        val prev = session.currentIndex
        val dir = inferDirection(prev, newIndex, total, session.lastDirection)
        if (commit || prev != newIndex) {
            session.lastDirection = dir
        }
        if (commit) {
            session.currentIndex = newIndex
        }
        val targetFolderKey = session.prefetchItems.getOrNull(newIndex)?.folderKey
        if (targetFolderKey != null && session.currentFolderKey != targetFolderKey) {
            session.currentFolderKey = targetFolderKey
            FolderCacheLifecycleHook.reconcileActiveFolders()
        }
        if (FxConfigBridge.current.enableAdjacentPrefetch) {
            val manager = PrefetchCacheManager.getOrCreate(session.appContext) { FxConfigBridge.current }
            manager.scheduleWindow(session.prefetchItems, newIndex, dir, session.sessionId)
        }
    }

    private fun inferDirection(prev: Int, next: Int, total: Int, fallback: Int): Int {
        if (prev < 0 || prev == next || total <= 1) return fallback
        if (prev == total - 1 && next == 0) return 1
        if (prev == 0 && next == total - 1) return -1
        return if (next > prev) 1 else -1
    }

    private fun getOrCreateSession(activity: Activity, appCtx: Context): ViewerSession {
        synchronized(sessionsLock) {
            return sessions.getOrPut(activity) {
                val sessionId = UUID.randomUUID().toString()
                PrefetchCacheManager.getOrCreate(appCtx) { FxConfigBridge.current }.beginSession(sessionId)
                ViewerSession(sessionId, appCtx)
            }
        }
    }

    private fun installTaskThreadFactory(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val taskThreadClassName = when (symbols.profile) {
            FxProfile.FX_9012 -> "i9.e"
            FxProfile.FX_9108 -> "bb.d"
            FxProfile.STRUCTURAL_FALLBACK -> null
        }
        if (taskThreadClassName == null) {
            PrefetchCacheManager.customThreadFactory = null
            return
        }
        val ctor = runCatching {
            val clazz = Class.forName(taskThreadClassName, false, symbols.classLoader)
            clazz.getDeclaredConstructor(
                Class::class.java,
                String::class.java,
                Runnable::class.java
            ).apply { isAccessible = true }
        }.onFailure { err ->
            installer.logWarn("Failed to resolve FX TaskThread $taskThreadClassName", err)
        }.getOrNull()
        if (ctor == null) {
            PrefetchCacheManager.customThreadFactory = null
            return
        }

        // Leave FX's UI interaction handler (a5 / X1) null so background SMB/network transfers run silently.
        PrefetchCacheManager.customThreadFactory = { name, runnable ->
            val thread = runCatching {
                ctor.newInstance(FxPlusModule::class.java, name, runnable) as? Thread
            }.onFailure { err ->
                installer.logWarn("Failed to instantiate FX TaskThread $taskThreadClassName", err)
            }.getOrNull() ?: Thread(runnable, name)
            thread.apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }

    private fun resolveProgressBarField(symbols: ResolvedSymbols): Field? {
        val fieldName = when (symbols.profile) {
            FxProfile.FX_9012 -> "R5"
            FxProfile.FX_9108 -> "N2"
            FxProfile.STRUCTURAL_FALLBACK -> return null
        }
        val viewerClass = symbols.sibling.imageViewerActivityClass ?: return null
        return runCatching {
            viewerClass.getDeclaredField(fieldName).apply { isAccessible = true }
        }.getOrNull()
    }

    private fun ensureCachedDisplayVisible(symbols: ResolvedSymbols, activity: Activity) {
        val progressField = resolvedProgressBarField
        val displayField = symbols.sibling.viewerCurrentDisplayField
        if (progressField == null && displayField == null) return
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            runCatching {
                (progressField?.get(activity) as? View)?.visibility = View.GONE
                (displayField?.get(activity) as? View)?.visibility = View.VISIBLE
            }
        }
    }

    private fun trackInitialViewerFolder(symbols: ResolvedSymbols, activity: Activity) {
        val s = symbols.sibling
        val appCtx = activity.applicationContext ?: activity
        val folderKey = runCatching {
            val holder = s.viewerCurrentHolderField?.get(activity)
            @Suppress("DEPRECATION")
            val intentItem = activity.intent?.extras?.get("nextapp.fx.intent.extra.ITEM")?.takeIf {
                s.fileItemClass == null || s.fileItemClass.isInstance(it)
            }
            val item = s.viewerInitialItemField?.get(activity)
                ?: holder?.let { s.itemHolderFileItemField?.get(it) }
                ?: intentItem
            if (item != null) {
                toPrefetchItem(symbols, appCtx, item, isRemote = isRemoteFileItem(symbols, item))?.folderKey
            } else {
                val uri = holder?.let { s.itemHolderUriField?.get(it) as? android.net.Uri }
                    ?: activity.intent?.data
                uri?.let {
                    PrefetchItem.defaultFolderKey("uri:${it.scheme ?: "none"}", it.path ?: it.toString())
                }
            }
        }.getOrNull()
        if (updateSessionFolderKey(activity, appCtx, folderKey)) {
            FolderCacheLifecycleHook.reconcileActiveFolders()
        }
    }

    private fun updateSessionFolderKey(
        activity: Activity,
        appCtx: Context,
        folderKey: String?
    ): Boolean {
        if (folderKey.isNullOrBlank()) return false
        val session = getOrCreateSession(activity, appCtx)
        if (session.currentFolderKey == folderKey) return false
        session.currentFolderKey = folderKey
        return true
    }

    fun getActiveViewerFolderKeys(): Set<String> {
        val result = LinkedHashSet<String>()
        synchronized(sessionsLock) {
            for ((activity, session) in sessions) {
                if (activity.isFinishing || activity.isDestroyed) continue
                session.currentFolderKey?.takeIf { it.isNotBlank() }?.let { result.add(it) }
            }
        }
        return result
    }

    fun onCacheKeysEvicted(evictedKeys: Set<String>) {
        if (evictedKeys.isEmpty()) return
        for (key in evictedKeys) {
            activeCachedByIdentity.remove(key)
        }
    }

    private fun clearSession(activity: Activity) {
        val session = synchronized(sessionsLock) { sessions.remove(activity) } ?: return
        for (item in session.prefetchItems) {
            activeCachedByIdentity.remove(item.cacheKey)
        }
        PrefetchCacheManager.peekInstance()?.endSession(session.sessionId)
        FolderCacheLifecycleHook.reconcileActiveFolders()
    }

    fun clearTransientStateOnHotReload() {
        PrefetchCacheManager.customThreadFactory = null
        resolvedProgressBarField = null
        scopedStreamFile.remove()
        activeCachedByIdentity.clear()
        synchronized(sessionsLock) {
            sessions.clear()
        }
        resolvedSymbols = null
    }

    fun peekCachedFileForItem(symbols: ResolvedSymbols, item: Any?): File? {
        return lookupCachedFile(symbols, item)
    }

    private fun lookupCachedFile(symbols: ResolvedSymbols, item: Any?): File? {
        if (item == null) return null
        val key = computeIdentityKey(symbols, item) ?: return null
        activeCachedByIdentity[key]?.takeIf { it.exists() && it.length() > 0L }?.let { return it }
        val mgr = PrefetchCacheManager.peekInstance() ?: return null
        val size = runCatching { (symbols.sibling.getSize?.invoke(item) as? Number)?.toLong() ?: -1L }.getOrDefault(-1L)
        return mgr.peekCachedFileByKey(key, size)?.also { activeCachedByIdentity[key] = it }
    }

    private fun recordCachedFile(symbols: ResolvedSymbols, item: Any, file: File) {
        val key = computeIdentityKey(symbols, item) ?: return
        activeCachedByIdentity[key] = file
    }

    private fun computeIdentityKey(symbols: ResolvedSymbols, item: Any): String? {
        val s = symbols.sibling
        return runCatching {
            val catalog = s.getCatalog?.invoke(item)
            val catalogId = if (catalog != null) "${catalog.javaClass.name}:$catalog" else "none"
            val path = s.getPath?.invoke(item)?.toString() ?: return null
            val mtime = (s.getLastModified?.invoke(item) as? Number)?.toLong() ?: 0L
            val size = (s.getSize?.invoke(item) as? Number)?.toLong() ?: -1L
            PrefetchCacheManager.computeCacheKey(catalogId, path, mtime, size)
        }.getOrNull()
    }

    private fun toPrefetchItem(
        symbols: ResolvedSymbols,
        appCtx: Context,
        item: Any,
        isRemote: Boolean
    ): PrefetchItem? {
        val s = symbols.sibling
        return runCatching {
            val catalog = s.getCatalog?.invoke(item)
            val catalogId = if (catalog != null) "${catalog.javaClass.name}:$catalog" else "none"
            val itemFxPath = s.getPath?.invoke(item) ?: return null
            val path = itemFxPath.toString()
            val mtime = (s.getLastModified?.invoke(item) as? Number)?.toLong() ?: 0L
            val size = (s.getSize?.invoke(item) as? Number)?.toLong() ?: -1L
            val mime = s.getMimeType?.invoke(item) as? String
            val opener: (() -> InputStream?)? = if (isRemote && s.openInputStream != null) {
                { s.openInputStream.invoke(item, appCtx) as? InputStream }
            } else {
                null
            }
            val parentFxPath = runCatching {
                symbols.folderLifecycle.fxPathParentMethod?.invoke(itemFxPath)
            }.getOrNull()
            val folderKey = FolderCacheLifecycleHook.canonicalFolderKeyFromFxPath(symbols, parentFxPath)
                ?: PrefetchItem.defaultFolderKey(catalogId, path)
            PrefetchItem(
                catalogIdentity = catalogId,
                path = path,
                lastModifiedMs = mtime,
                sizeBytes = size,
                mimeType = mime,
                isRemote = isRemote,
                opener = opener,
                folderKey = folderKey
            )
        }.getOrNull()
    }

    private fun isRemoteFileItem(symbols: ResolvedSymbols, item: Any): Boolean {
        val s = symbols.sibling
        return runCatching {
            val catalog = s.getCatalog?.invoke(item) ?: return false
            val typeObj = s.getCatalogType?.invoke(catalog) ?: return false
            val m = s.isRemoteMethod ?: return false
            if (java.lang.reflect.Modifier.isStatic(m.modifiers)) {
                (m.invoke(null, typeObj) as? Boolean) ?: false
            } else {
                (m.invoke(typeObj) as? Boolean) ?: false
            }
        }.getOrDefault(false)
    }

    private fun recoverJpegExifRotation(symbols: ResolvedSymbols, ctx: Context, item: Any): Int {
        val mime = runCatching { symbols.sibling.getMimeType?.invoke(item) as? String }
            .getOrNull()?.lowercase(Locale.ROOT)
        if (mime != "image/jpeg" && mime != "image/jpg") return 0

        val cached = lookupCachedFile(symbols, item)
        if (cached != null && cached.exists()) {
            return runCatching {
                FileInputStream(cached).use { readExifRotation(it) }
            }.getOrDefault(0)
        }

        // Local FileCatalog JPEG inside Android/(data|obb) fails direct FileInputStream; use item's stream.
        if (AndroidDataAccessHook.isScopedNode(symbols, item)) {
            return runCatching {
                val stream = symbols.sibling.openInputStream?.invoke(item, ctx) as? InputStream ?: return 0
                stream.use { readExifRotation(it) }
            }.getOrDefault(0)
        }
        return 0
    }

    private fun readExifRotation(stream: InputStream): Int {
        val exif = ExifInterface(stream)
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }
}
