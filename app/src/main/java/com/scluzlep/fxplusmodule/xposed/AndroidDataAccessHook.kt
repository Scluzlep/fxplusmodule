package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import io.github.libxposed.api.XposedInterface
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Unlocks `Android/data` and `Android/obb` browsing, creation, streaming, deletion,
 * renaming, and moving in FX's `FileCatalog` (`9012`: `a`/`b`/`c`; `9108`: `ec.a`/`ec.d`/`ec.e`)
 * strictly for paths matching `^/(storage/emulated/[0-9]+|sdcard)/Android/(data|obb)(/.*)?$`.
 * Operates exclusively on FUSE mount paths via [PrivilegedStorageClient] (Root first, Shizuku fallback).
 */
object AndroidDataAccessHook {

    private const val STREAM_BUFFER_BYTES = 65_536

    @Volatile
    private var appContext: Context? = null

    fun attachContext(context: Context) {
        appContext = context.applicationContext ?: context
    }

    fun isScopedNode(symbols: ResolvedSymbols, node: Any?): Boolean {
        if (!FxConfigBridge.current.enableAndroidDataAccess) return false
        val path = extractNodeAbsPath(symbols.androidData, node) ?: return false
        return PrivilegedStorageClient.isScopedRestrictedPath(path)
    }

    fun install(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val ad = symbols.androidData
        if (ad.fileDirListChildren == null) {
            installer.logWarn("AndroidDataAccessHook skipped: missing FileCatalog bindings")
            return
        }

        installer.install("androidData:listChildren", ad.fileDirListChildren) { chain ->
            handleListChildren(symbols, installer, chain)
        }

        installer.install("androidData:dirClearCache", ad.fileDirClearCache) { chain ->
            val res = chain.proceed()
            val dirPath = extractNodeAbsPath(ad, chain.thisObject)
            if (dirPath != null && PrivilegedStorageClient.isScopedRestrictedPath(dirPath)) {
                PrivilegedStorageClient.invalidatePath(dirPath)
            }
            res
        }

        installer.install("androidData:dirStatVolume", ad.fileDirStatVolume) { chain ->
            val dirPath = extractNodeAbsPath(ad, chain.thisObject)
            if (FxConfigBridge.current.enableAndroidDataAccess &&
                dirPath != null &&
                PrivilegedStorageClient.isScopedRestrictedPath(dirPath)
            ) {
                runCatching { chain.proceed() }.getOrNull()
            } else {
                chain.proceed()
            }
        }

        installer.install("androidData:fileStatSize", ad.fileFileStatSize) { chain ->
            val node = chain.thisObject
            val ctx = (chain.getArg(0) as? Context) ?: appContext
            val path = extractNodeAbsPath(ad, node)
            if (!FxConfigBridge.current.enableAndroidDataAccess ||
                node == null ||
                ctx == null ||
                !PrivilegedStorageClient.isScopedRestrictedPath(path)
            ) {
                return@install chain.proceed()
            }
            val stat = PrivilegedStorageClient.stat(ctx, path!!)
            if (stat != null && stat.exists && stat.isFile) {
                ad.fileFileCachedSizeField?.setLong(node, stat.size)
                return@install null
            }
            chain.proceed()
        }

        installer.install("androidData:getLastModified", ad.fileNodeGetLastModified) { chain ->
            val node = chain.thisObject
            val path = extractNodeAbsPath(ad, node)
            if (!FxConfigBridge.current.enableAndroidDataAccess ||
                !PrivilegedStorageClient.isScopedRestrictedPath(path)
            ) {
                return@install chain.proceed()
            }
            val cached = PrivilegedStorageClient.peekCachedStat(path)
            if (cached != null && cached.exists) {
                return@install cached.lastModifiedMs
            }
            val ctx = appContext
            if (ctx != null) {
                val stat = PrivilegedStorageClient.stat(ctx, path!!)
                if (stat != null && stat.exists) {
                    return@install stat.lastModifiedMs
                }
            }
            chain.proceed()
        }

        installer.install("androidData:isReadOnly", ad.fileNodeIsReadOnly) { chain ->
            val node = chain.thisObject
            val path = extractNodeAbsPath(ad, node)
            if (!FxConfigBridge.current.enableAndroidDataAccess ||
                !PrivilegedStorageClient.isScopedRestrictedPath(path)
            ) {
                return@install chain.proceed()
            }
            val cached = PrivilegedStorageClient.peekCachedStat(path)
            if (cached != null && cached.exists) {
                return@install !cached.canWrite
            }
            val ctx = appContext
            if (ctx != null) {
                val stat = PrivilegedStorageClient.stat(ctx, path!!)
                if (stat != null && stat.exists) {
                    return@install !stat.canWrite
                }
                if (PrivilegedStorageClient.hasAnyBackend(ctx, path)) {
                    return@install false
                }
            }
            chain.proceed()
        }

        if (ad.fileNodeSetLastModified != null) {
            installer.install("androidData:setLastModified", ad.fileNodeSetLastModified) { chain ->
                val path = extractNodeAbsPath(ad, chain.thisObject)
                if (FxConfigBridge.current.enableAndroidDataAccess &&
                    PrivilegedStorageClient.isScopedRestrictedPath(path)
                ) {
                    PrivilegedStorageClient.invalidatePath(path)
                    return@install null
                }
                chain.proceed()
            }
        }

        installer.install("androidData:childAvailable", ad.fileDirChildAvailable) { chain ->
            val node = chain.thisObject
            val ctx = (chain.getArg(0) as? Context) ?: appContext
            val rawName = chain.getArg(1)?.toString()
            val dirPath = extractNodeAbsPath(ad, node)
            if (!FxConfigBridge.current.enableAndroidDataAccess ||
                ctx == null ||
                dirPath == null ||
                rawName == null
            ) {
                return@install chain.proceed()
            }
            val sanitizedName = rawName.replace('/', '_')
            val childPath = "$dirPath/$sanitizedName"
            if (!PrivilegedStorageClient.isScopedRestrictedPath(childPath)) {
                return@install chain.proceed()
            }
            val stat = PrivilegedStorageClient.stat(ctx, childPath, useCache = false)
                ?: return@install chain.proceed()
            // Inverted boolean contract: returns true when target child DOES NOT exist.
            !stat.exists
        }

        installer.install("androidData:mkdir", ad.fileDirMkdir) { chain ->
            handleMkdir(symbols, chain)
        }

        installer.install("androidData:openInput", ad.fileFileOpenInput) { chain ->
            val node = chain.thisObject
            val ctx = (chain.getArg(0) as? Context) ?: appContext
            val filePath = extractNodeAbsPath(ad, node)
            if (!FxConfigBridge.current.enableAndroidDataAccess ||
                ctx == null ||
                !PrivilegedStorageClient.isScopedRestrictedPath(filePath)
            ) {
                return@install chain.proceed()
            }
            if (!PrivilegedStorageClient.hasAnyBackend(ctx, filePath!!)) {
                return@install chain.proceed()
            }
            try {
                BufferedInputStream(PrivilegedStorageClient.openRead(ctx, filePath), STREAM_BUFFER_BYTES)
            } catch (ioe: IOException) {
                throw FxHookSupport.buildUserException(
                    ad.userExceptionClass,
                    ioe.message ?: "Read failed",
                    FxUserErrorKind.READ
                )
            }
        }

        installer.install("androidData:openOutputInternal", ad.fileFileOpenOutputInternal) { chain ->
            val node = chain.thisObject
            val ctx = (chain.getArg(0) as? Context) ?: appContext
            val append = (chain.getArg(1) as? Boolean) ?: false
            val filePath = extractNodeAbsPath(ad, node)
            if (!FxConfigBridge.current.enableAndroidDataAccess ||
                ctx == null ||
                !PrivilegedStorageClient.isScopedRestrictedPath(filePath)
            ) {
                return@install chain.proceed()
            }
            if (!PrivilegedStorageClient.hasAnyBackend(ctx, filePath!!)) {
                return@install chain.proceed()
            }
            try {
                BufferedOutputStream(
                    PrivilegedStorageClient.openWrite(ctx, filePath, append),
                    STREAM_BUFFER_BYTES
                )
            } catch (ioe: IOException) {
                throw FxHookSupport.buildUserException(
                    ad.userExceptionClass,
                    ioe.message ?: "Write failed",
                    FxUserErrorKind.WRITE
                )
            }
        }

        val deleteHooker = XposedInterface.Hooker { chain ->
            handleDelete(symbols, chain)
        }
        installer.install("androidData:dirDelete", ad.fileDirDeleteRecursive, deleteHooker)
        installer.install("androidData:fileDelete", ad.fileFileDelete, deleteHooker)
        installer.install("androidData:nodeDeleteSingle", ad.fileNodeDeleteSingle, deleteHooker)

        installer.install("androidData:rename", ad.fileNodeRename) { chain ->
            handleRename(symbols, chain)
        }

        installer.install("androidData:moveTo", ad.fileNodeMoveTo) { chain ->
            handleMoveTo(symbols, installer, chain)
        }
    }

    private fun handleListChildren(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        chain: XposedInterface.Chain
    ): Any? {
        val ad = symbols.androidData
        val s = symbols.sibling
        val dirNode = chain.thisObject ?: return chain.proceed()
        val ctx = (chain.getArg(0) as? Context) ?: appContext ?: return chain.proceed()
        val flags = (chain.getArg(1) as? Int) ?: 0
        val dirPath = extractNodeAbsPath(ad, dirNode) ?: return chain.proceed()

        if (!FxConfigBridge.current.enableAndroidDataAccess ||
            !PrivilegedStorageClient.isScopedRestrictedPath(dirPath)
        ) {
            return chain.proceed()
        }

        val entries = PrivilegedStorageClient.listEntries(ctx, dirPath)
            ?: return chain.proceed()

        val includeHidden = (flags and 2) != 0
        val dirsOnly = (flags and 8) != 0
        val metricsCopy = (flags and 16) != 0

        val catalog = ad.fileNodeCatalogField?.get(dirNode) ?: return chain.proceed()
        val dirFxPath = ad.fileNodePathField?.get(dirNode) ?: return chain.proceed()
        val parentMetrics = runCatching { ad.fileNodeMetricsField?.getLong(dirNode) ?: -1L }.getOrDefault(-1L)
        val itemBaseClass = s.fileItemBaseClass ?: return chain.proceed()

        val resultNodes = ArrayList<Any>(entries.size)
        val rawFiles = ArrayList<File>(entries.size)

        for (entry in entries) {
            val childFile = File(entry.path)
            rawFiles.add(childFile)
            if (!includeHidden) {
                val hiddenByStore = runCatching {
                    (ad.hiddenFileStoreIsHidden?.invoke(null, entry.path) as? Boolean) ?: false
                }.getOrDefault(false)
                if (entry.isHidden || hiddenByStore) continue
            }
            if (dirsOnly && !entry.isDir) continue

            val childFxPath = ad.fxPathChildCtor?.newInstance(dirFxPath, entry.name) ?: continue
            val childNode = if (entry.isDir) {
                createDirectoryNode(symbols, catalog, childFxPath, childFile)
            } else {
                createFileNode(symbols, catalog, childFxPath, childFile, entry.size)
            } ?: continue

            if (metricsCopy && parentMetrics >= 0L) {
                runCatching { ad.fileNodeMetricsField?.setLong(childNode, parentMetrics) }
            }
            resultNodes.add(childNode)
        }

        runCatching {
            ad.fileDirCachedFilesField?.set(dirNode, rawFiles.toTypedArray())
        }

        val typedArray = java.lang.reflect.Array.newInstance(itemBaseClass, resultNodes.size)
        for (i in resultNodes.indices) {
            java.lang.reflect.Array.set(typedArray, i, resultNodes[i])
        }
        installer.logDebug("Listed ${resultNodes.size} entries in $dirPath")
        return typedArray
    }

    private fun handleMkdir(symbols: ResolvedSymbols, chain: XposedInterface.Chain): Any? {
        val ad = symbols.androidData
        val dirNode = chain.thisObject ?: return chain.proceed()
        val ctx = (chain.getArg(0) as? Context) ?: appContext ?: return chain.proceed()
        val name = chain.getArg(1)?.toString() ?: return chain.proceed()
        val overwrite = (chain.getArg(2) as? Boolean) ?: false
        val dirPath = extractNodeAbsPath(ad, dirNode) ?: return chain.proceed()
        val targetPath = "$dirPath/$name"

        if (!FxConfigBridge.current.enableAndroidDataAccess ||
            !PrivilegedStorageClient.isScopedRestrictedPath(targetPath)
        ) {
            return chain.proceed()
        }
        if (!PrivilegedStorageClient.hasAnyBackend(ctx, targetPath)) {
            return chain.proceed()
        }

        val stat = PrivilegedStorageClient.stat(ctx, targetPath, useCache = false)
        if (stat != null && stat.exists) {
            if (!overwrite) {
                throw FxHookSupport.buildUserException(
                    ad.userExceptionClass,
                    "Exists: $name",
                    FxUserErrorKind.EXISTS
                )
            }
        } else {
            if (!PrivilegedStorageClient.mkdirs(ctx, targetPath)) {
                throw FxHookSupport.buildUserException(
                    ad.userExceptionClass,
                    "Mkdir failed: $name",
                    FxUserErrorKind.WRITE
                )
            }
        }

        val catalog = ad.fileNodeCatalogField?.get(dirNode) ?: return chain.proceed()
        val dirFxPath = ad.fileNodePathField?.get(dirNode) ?: return chain.proceed()
        val childFxPath = ad.fxPathChildCtor?.newInstance(dirFxPath, name) ?: return chain.proceed()
        return createDirectoryNode(symbols, catalog, childFxPath, File(targetPath))
            ?: chain.proceed()
    }

    private fun handleDelete(symbols: ResolvedSymbols, chain: XposedInterface.Chain): Any? {
        val ad = symbols.androidData
        val node = chain.thisObject ?: return chain.proceed()
        val ctx = (chain.getArg(0) as? Context) ?: appContext ?: return chain.proceed()
        val path = extractNodeAbsPath(ad, node) ?: return chain.proceed()

        if (!FxConfigBridge.current.enableAndroidDataAccess ||
            !PrivilegedStorageClient.isScopedRestrictedPath(path)
        ) {
            return chain.proceed()
        }
        if (!PrivilegedStorageClient.hasAnyBackend(ctx, path)) {
            return chain.proceed()
        }
        if (PrivilegedStorageClient.isRestrictedRoot(path)) {
            throw FxHookSupport.buildUserException(
                ad.userExceptionClass,
                "Protected directory: $path",
                FxUserErrorKind.ACCESS
            )
        }

        val isDir = ad.fileDirNodeClass?.isInstance(node) == true
        val deleted = PrivilegedStorageClient.deleteEntry(ctx, path, recursive = isDir)
        if (!deleted) {
            val st = PrivilegedStorageClient.stat(ctx, path, useCache = false)
            if (st != null && st.exists) {
                throw FxHookSupport.buildUserException(
                    ad.userExceptionClass,
                    "Delete failed: $path",
                    FxUserErrorKind.WRITE
                )
            }
        }
        return null
    }

    private fun handleRename(symbols: ResolvedSymbols, chain: XposedInterface.Chain): Any? {
        val ad = symbols.androidData
        val node = chain.thisObject ?: return chain.proceed()
        val ctx = (chain.getArg(0) as? Context) ?: appContext ?: return chain.proceed()
        val newName = chain.getArg(1)?.toString() ?: return chain.proceed()
        val srcPath = extractNodeAbsPath(ad, node) ?: return chain.proceed()
        val parentPath = srcPath.substringBeforeLast('/', "")
        if (parentPath.isEmpty()) return chain.proceed()
        val dstPath = "$parentPath/$newName"

        if (!FxConfigBridge.current.enableAndroidDataAccess ||
            !PrivilegedStorageClient.isScopedRestrictedPath(srcPath)
        ) {
            return chain.proceed()
        }
        if (!PrivilegedStorageClient.hasAnyBackend(ctx, srcPath)) {
            return chain.proceed()
        }
        if (PrivilegedStorageClient.isRestrictedRoot(srcPath)) {
            throw FxHookSupport.buildUserException(
                ad.userExceptionClass,
                "Protected directory: $srcPath",
                FxUserErrorKind.ACCESS
            )
        }

        val oldName = srcPath.substringAfterLast('/')
        if (!oldName.equals(newName, ignoreCase = true)) {
            val existing = PrivilegedStorageClient.stat(ctx, dstPath, useCache = false)
            if (existing != null && existing.exists) {
                throw FxHookSupport.buildUserException(
                    ad.userExceptionClass,
                    "Target already exists: $newName",
                    FxUserErrorKind.EXISTS
                )
            }
        }
        if (!PrivilegedStorageClient.renameEntry(ctx, srcPath, dstPath)) {
            throw FxHookSupport.buildUserException(
                ad.userExceptionClass,
                "Rename failed: $newName",
                FxUserErrorKind.WRITE
            )
        }
        return null
    }

    private fun handleMoveTo(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        chain: XposedInterface.Chain
    ): Any? {
        val ad = symbols.androidData
        val node = chain.thisObject ?: return chain.proceed()
        val ctx = (chain.getArg(0) as? Context) ?: appContext ?: return chain.proceed()
        val dstParentFxPath = chain.getArg(1) ?: return chain.proceed()
        val srcPath = extractNodeAbsPath(ad, node) ?: return chain.proceed()
        val name = srcPath.substringAfterLast('/')

        val dstCatalog = runCatching {
            val fcClass = ad.fileCatalogClass ?: return@runCatching null
            ad.fxPathFindElement?.invoke(dstParentFxPath, fcClass)
        }.getOrNull() ?: ad.fileNodeCatalogField?.get(node) ?: return chain.proceed()

        val dstDirFile = runCatching {
            ad.fileCatalogResolveFile?.invoke(dstCatalog, dstParentFxPath) as? File
        }.getOrNull() ?: return chain.proceed()
        val dstPath = File(dstDirFile, name).absolutePath

        val srcScoped = PrivilegedStorageClient.isScopedRestrictedPath(srcPath)
        val dstScoped = PrivilegedStorageClient.isScopedRestrictedPath(dstPath)
        if (!FxConfigBridge.current.enableAndroidDataAccess || (!srcScoped && !dstScoped)) {
            return chain.proceed()
        }

        if (srcScoped && dstScoped) {
            if (!PrivilegedStorageClient.hasAnyBackend(ctx, srcPath)) {
                return chain.proceed()
            }
            if (!PrivilegedStorageClient.renameEntry(ctx, srcPath, dstPath)) {
                throw FxHookSupport.buildUserException(
                    ad.userExceptionClass,
                    "Move failed: $name",
                    FxUserErrorKind.WRITE
                )
            }
            return true
        }

        // Cross-boundary move between normal storage and Android/(data|obb): try Root mv first.
        if (PrivilegedStorageClient.moveCrossingBoundaryWithRoot(srcPath, dstPath)) {
            return true
        }

        // Fallback for Shizuku-only mode: stream copy + delete across FUSE boundary.
        val copied = runCatching {
            copyAndRemoveAcrossBoundary(ctx, srcPath, dstPath, srcScoped)
        }.getOrDefault(false)
        if (!copied) {
            installer.logWarn("Cross-boundary move failed: $srcPath -> $dstPath")
            return chain.proceed()
        }
        return true
    }

    private fun copyAndRemoveAcrossBoundary(
        ctx: Context,
        srcPath: String,
        dstPath: String,
        srcIsScoped: Boolean
    ): Boolean {
        val isDir: Boolean
        if (srcIsScoped) {
            val st = PrivilegedStorageClient.stat(ctx, srcPath, useCache = false) ?: return false
            if (!st.exists) return false
            isDir = st.isDir
        } else {
            val f = File(srcPath)
            if (!f.exists()) return false
            isDir = f.isDirectory
        }

        if (isDir) {
            val dstCreated = if (srcIsScoped) {
                val dstFile = File(dstPath)
                dstFile.isDirectory || dstFile.mkdirs()
            } else {
                PrivilegedStorageClient.mkdirs(ctx, dstPath)
            }
            if (!dstCreated) return false

            val childNames: List<String> = if (srcIsScoped) {
                PrivilegedStorageClient.listEntries(ctx, srcPath)?.map { it.name } ?: return false
            } else {
                File(srcPath).list()?.toList() ?: return false
            }
            for (child in childNames) {
                if (!copyAndRemoveAcrossBoundary(ctx, "$srcPath/$child", "$dstPath/$child", srcIsScoped)) {
                    return false
                }
            }
            return if (srcIsScoped) {
                PrivilegedStorageClient.deleteEntry(ctx, srcPath, recursive = true)
            } else {
                File(srcPath).deleteRecursively()
            }
        } else {
            val input: InputStream = if (srcIsScoped) {
                PrivilegedStorageClient.openRead(ctx, srcPath)
            } else {
                FileInputStream(File(srcPath))
            }
            val output: OutputStream = if (srcIsScoped) {
                val dstFile = File(dstPath)
                dstFile.parentFile?.mkdirs()
                FileOutputStream(dstFile)
            } else {
                PrivilegedStorageClient.openWrite(ctx, dstPath, append = false)
            }
            input.use { ins ->
                output.use { outs ->
                    val buf = ByteArray(STREAM_BUFFER_BYTES)
                    while (true) {
                        val n = ins.read(buf)
                        if (n == -1) break
                        outs.write(buf, 0, n)
                    }
                    outs.flush()
                }
            }
            return if (srcIsScoped) {
                PrivilegedStorageClient.deleteEntry(ctx, srcPath, recursive = false)
            } else {
                File(srcPath).delete()
            }
        }
    }

    private fun createDirectoryNode(
        symbols: ResolvedSymbols,
        catalog: Any,
        fxPath: Any,
        javaFile: File
    ): Any? {
        val ad = symbols.androidData
        return runCatching {
            ad.fileDirCtor9012?.newInstance(catalog, fxPath, javaFile)
                ?: ad.fileCatalogOpenDir?.invoke(catalog, fxPath)
        }.getOrNull()
    }

    private fun createFileNode(
        symbols: ResolvedSymbols,
        catalog: Any,
        fxPath: Any,
        javaFile: File,
        size: Long
    ): Any? {
        val ad = symbols.androidData
        return runCatching {
            val node = ad.fileFileCtor?.newInstance(catalog, fxPath, javaFile) ?: return null
            ad.fileFileCachedSizeField?.setLong(node, size)
            node
        }.getOrNull()
    }

    private fun extractNodeAbsPath(ad: AndroidDataBindings, node: Any?): String? {
        if (node == null) return null
        val f = runCatching { ad.fileNodeJavaFileField?.get(node) as? File }.getOrNull()
        return f?.absolutePath
    }
}
