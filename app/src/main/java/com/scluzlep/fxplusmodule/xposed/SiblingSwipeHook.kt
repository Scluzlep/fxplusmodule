package com.scluzlep.fxplusmodule.xposed

import android.app.Activity
import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationTargetException
import java.util.Locale

/**
 * Unlocks FX's 4 MiB remote sibling image swipe cap for both `9012` (`k0.b`) and
 * `9108` (`b0.run`) while preserving per-folder sort order via `DirectoryOptionStore`
 * and never hooking `DirectoryCatalog$b.b` / `a0.f.j` globally.
 */
object SiblingSwipeHook {

    fun install(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val s = symbols.sibling
        val enumerate = s.enumerateSiblings
        when (enumerate?.parameterTypes?.size) {
            6 -> {
                installer.install("sibling:enumerate:6arg", enumerate) { chain ->
                    if (!FxConfigBridge.current.unlockLargeImageSwipe) {
                        return@install chain.proceed()
                    }
                    handle9012Enumerate(symbols, installer, chain)
                }
            }
            0 -> {
                installer.install("sibling:enumerate:0arg", enumerate) { chain ->
                    if (!FxConfigBridge.current.unlockLargeImageSwipe) {
                        val res = chain.proceed()
                        notify9108AfterVanilla(symbols, chain.thisObject)
                        return@install res
                    }
                    handle9108Enumerate(symbols, installer, chain)
                }
            }
            else -> {
                installer.logWarn("SiblingSwipeHook skipped: unsupported enumerateSiblings signature")
            }
        }
    }

    private fun handle9012Enumerate(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        chain: XposedInterface.Chain
    ): Any? {
        val s = symbols.sibling
        val dir = chain.getArg(0) ?: return chain.proceed()
        val ctx = chain.getArg(1) as? Context ?: return chain.proceed()
        var sortOrder = chain.getArg(2)
        var descending = (chain.getArg(3) as? Boolean) ?: false
        val currentItem = chain.getArg(4)
        val callback = chain.getArg(5) ?: return chain.proceed()

        val activity = runCatching { s.siblingCallbackActivityField?.get(callback) as? Activity }.getOrNull()
        val folderSort = resolveFolderSort(s, dir)
        if (folderSort != null) {
            sortOrder = folderSort.first
            descending = folderSort.second
            if (activity != null) {
                runCatching {
                    s.viewerSortOrderField?.set(activity, sortOrder)
                    s.viewerSortDescField?.setBoolean(activity, descending)
                }
            }
        }

        val supportedMimes = runCatching {
            @Suppress("UNCHECKED_CAST")
            s.supportedMimesField?.get(null) as? Collection<String>
        }.getOrNull().orEmpty()

        try {
            val isRemote = isRemoteDirectory(s, dir)
            val raw = s.listChildren?.invoke(dir, ctx, 1) as? Array<*> ?: return chain.proceed()
            if (sortOrder != null) {
                runCatching { s.sortItems?.invoke(null, raw, sortOrder, descending, false) }
            }

            val maxBytes = FxConfigBridge.current.maxSwipeImageSizeBytes()
            val fileItemClass = s.fileItemClass ?: return chain.proceed()
            val filtered = filterImageSiblings(
                raw = raw,
                fileItemClass = fileItemClass,
                supportedMimes = supportedMimes,
                isRemote = isRemote,
                maxBytes = maxBytes,
                currentItem = currentItem,
                getMime = { item -> s.getMimeType?.invoke(item) as? String },
                getSize = { item -> (s.getSize?.invoke(item) as? Number)?.toLong() ?: -1L },
                getPath = { item -> s.getPath?.invoke(item)?.toString() }
            )

            val index = findCurrentIndex(filtered, currentItem) { item ->
                s.getPath?.invoke(item)?.toString()
            }

            if (index == -1) {
                val failMethod = s.siblingCallbackFailMethod
                    ?: runCatching {
                        s.siblingCallbackClass?.getDeclaredMethod("b")?.apply { isAccessible = true }
                    }.getOrNull()
                failMethod?.invoke(callback)
                return null
            }

            val typedArray = java.lang.reflect.Array.newInstance(fileItemClass, filtered.size)
            for (i in filtered.indices) {
                java.lang.reflect.Array.set(typedArray, i, filtered[i])
            }
            s.siblingCallbackLoadedMethod?.invoke(callback, typedArray, index)
            installer.logDebug("9012 siblings resolved count=${filtered.size} index=$index remote=$isRemote maxBytes=$maxBytes")
            return null
        } catch (ite: InvocationTargetException) {
            val cause = ite.targetException ?: ite
            if (FxHookSupport.isFxCancelException(symbols, cause)) {
                return null
            }
            if (symbols.androidData.userExceptionClass?.isInstance(cause) == true) {
                Log.w("nextapp.fx", "ImageViewer: Unable to retrieve sibling files", cause)
                return null
            }
            installer.logWarn("SiblingSwipeHook 9012 fallback on error", cause)
            return chain.proceed()
        } catch (t: Throwable) {
            installer.logWarn("SiblingSwipeHook 9012 fallback", t)
            return chain.proceed()
        }
    }

    private fun handle9108Enumerate(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        chain: XposedInterface.Chain
    ): Any? {
        val s = symbols.sibling
        val runner = chain.thisObject ?: return chain.proceed()
        val dir = s.runnerDirField?.get(runner) ?: return chain.proceed()
        val ctx = s.runnerCtxField?.get(runner) as? Context ?: return chain.proceed()
        var sortOrder = s.runnerSortOrderField?.get(runner)
        var descending = runCatching { s.runnerSortDescField?.getBoolean(runner) ?: false }.getOrDefault(false)
        val currentItem = s.runnerCurrentItemField?.get(runner)
        val holder = s.runnerCallbackHolderField?.get(runner) ?: return chain.proceed()
        val activity = s.itemHolderUriField?.get(holder) as? Activity
        val display = s.itemHolderFileItemField?.get(holder)

        val folderSort = resolveFolderSort(s, dir)
        if (folderSort != null) {
            sortOrder = folderSort.first
            descending = folderSort.second
            if (activity != null) {
                runCatching {
                    s.viewerSortOrderField?.set(activity, sortOrder)
                    s.viewerSortDescField?.setBoolean(activity, descending)
                }
            }
        }

        val supportedMimes = runCatching {
            @Suppress("UNCHECKED_CAST")
            s.supportedMimesField?.get(null) as? Collection<String>
        }.getOrNull().orEmpty()

        try {
            val isRemote = isRemoteDirectory(s, dir)
            val raw = s.listChildren?.invoke(dir, ctx, 1) as? Array<*> ?: return chain.proceed()
            if (sortOrder != null) {
                runCatching { s.sortItems?.invoke(null, raw, sortOrder, descending, false) }
            }

            val maxBytes = FxConfigBridge.current.maxSwipeImageSizeBytes()
            val fileItemClass = s.fileItemClass ?: return chain.proceed()
            val filtered = filterImageSiblings(
                raw = raw,
                fileItemClass = fileItemClass,
                supportedMimes = supportedMimes,
                isRemote = isRemote,
                maxBytes = maxBytes,
                currentItem = currentItem,
                getMime = { item -> s.getMimeType?.invoke(item) as? String },
                getSize = { item -> (s.getSize?.invoke(item) as? Number)?.toLong() ?: -1L },
                getPath = { item -> s.getPath?.invoke(item)?.toString() }
            )

            val index = findCurrentIndex(filtered, currentItem) { item ->
                s.getPath?.invoke(item)?.toString()
            }
            if (index == -1 || activity == null || display == null) {
                return null
            }

            val typedArray = java.lang.reflect.Array.newInstance(fileItemClass, filtered.size)
            for (i in filtered.indices) {
                java.lang.reflect.Array.set(typedArray, i, filtered[i])
            }
            s.viewerSiblingItemsField?.set(activity, typedArray)
            s.displaySiblingIndexField?.setInt(display, index)
            s.displaySiblingCountField?.setInt(display, filtered.size)
            s.viewerSiblingsLoadedField?.setBoolean(activity, true)

            ViewerPrefetchHook.onSiblingsReady(activity, filtered, index)
            installer.logDebug("9108 siblings resolved count=${filtered.size} index=$index remote=$isRemote maxBytes=$maxBytes")
            return null
        } catch (ite: InvocationTargetException) {
            val cause = ite.targetException ?: ite
            if (FxHookSupport.isFxCancelException(symbols, cause)) {
                return null
            }
            if (symbols.androidData.userExceptionClass?.isInstance(cause) == true) {
                Log.w("nextapp.fx", "ImageViewer: Unable to retrieve sibling files", cause)
                return null
            }
            installer.logWarn("SiblingSwipeHook 9108 fallback on error", cause)
            return chain.proceed()
        } catch (t: Throwable) {
            installer.logWarn("SiblingSwipeHook 9108 fallback", t)
            return chain.proceed()
        }
    }

    private fun notify9108AfterVanilla(symbols: ResolvedSymbols, runner: Any?) {
        if (runner == null) return
        val s = symbols.sibling
        runCatching {
            val holder = s.runnerCallbackHolderField?.get(runner) ?: return
            val activity = s.itemHolderUriField?.get(holder) as? Activity ?: return
            val display = s.itemHolderFileItemField?.get(holder) ?: return
            val arr = s.viewerSiblingItemsField?.get(activity) as? Array<*> ?: return
            val idx = s.displaySiblingIndexField?.getInt(display) ?: return
            if (idx in arr.indices) {
                val items = arr.filterNotNull()
                ViewerPrefetchHook.onSiblingsReady(activity, items, idx)
            }
        }
    }

    private fun resolveFolderSort(s: SiblingBindings, dir: Any): Pair<Any, Boolean>? {
        return runCatching {
            val pair = s.getSortOptions?.invoke(null, dir) ?: return null
            val order = s.sortPairOrderField?.get(pair) ?: return null
            val desc = s.sortPairDescField?.getBoolean(pair) ?: false
            order to desc
        }.getOrNull()
    }

    fun isRemoteDirectory(s: SiblingBindings, dir: Any): Boolean {
        return runCatching {
            val catalog = s.getCatalog?.invoke(dir) ?: return false
            val typeObj = s.getCatalogType?.invoke(catalog) ?: return false
            val method = s.isRemoteMethod ?: return false
            if (java.lang.reflect.Modifier.isStatic(method.modifiers)) {
                (method.invoke(null, typeObj) as? Boolean) ?: false
            } else {
                (method.invoke(typeObj) as? Boolean) ?: false
            }
        }.getOrDefault(false)
    }

    private inline fun filterImageSiblings(
        raw: Array<*>,
        fileItemClass: Class<*>,
        supportedMimes: Collection<String>,
        isRemote: Boolean,
        maxBytes: Long,
        currentItem: Any?,
        getMime: (Any) -> String?,
        getSize: (Any) -> Long,
        getPath: (Any) -> String?
    ): List<Any> {
        val currentPath = currentItem?.let { runCatching { getPath(it) }.getOrNull() }
        val result = ArrayList<Any>(raw.size)
        var hasCurrent = false

        for (entry in raw) {
            if (entry == null || !fileItemClass.isInstance(entry)) continue
            val mime = runCatching { getMime(entry) }.getOrNull()?.lowercase(Locale.ROOT) ?: continue
            if (mime !in supportedMimes) continue

            val sameAsCurrent = entry === currentItem ||
                (currentPath != null && runCatching { getPath(entry) }.getOrNull() == currentPath)

            if (isRemote && maxBytes < Long.MAX_VALUE && !sameAsCurrent) {
                val sz = runCatching { getSize(entry) }.getOrDefault(-1L)
                if (sz > maxBytes) continue
            }
            if (sameAsCurrent) {
                hasCurrent = true
            }
            result.add(entry)
        }

        if (currentItem != null && !hasCurrent && fileItemClass.isInstance(currentItem)) {
            result.add(0, currentItem)
        }
        return result
    }

    private inline fun findCurrentIndex(
        items: List<Any>,
        currentItem: Any?,
        getPath: (Any) -> String?
    ): Int {
        if (currentItem == null) return -1
        val refIdx = items.indexOfFirst { it === currentItem }
        if (refIdx >= 0) return refIdx
        val eqIdx = items.indexOf(currentItem)
        if (eqIdx >= 0) return eqIdx
        val targetPath = runCatching { getPath(currentItem) }.getOrNull() ?: return -1
        return items.indexOfFirst { runCatching { getPath(it) }.getOrNull() == targetPath }
    }
}
