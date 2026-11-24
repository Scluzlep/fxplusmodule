package com.scluzlep.fxplusmodule.xposed

import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Dynamic DexKit 2.3.0 symbol resolver for FX Explorer versions outside the static
 * `9012`/`9108` contracts. Supports both classic unmerged classes (`9012`-style) and
 * R8-merged synthetic workers/closures (`9108`-style).
 */
object FxDexKitResolver {

    private const val NATIVE_LIB_NAME = "dexkit"
    private const val NATIVE_CACHE_DIR = "fxplus_dexkit_native"
    private const val GDRIVE_DEFAULT_CLIENT_ID =
        "907044817113-8d3ccj149pvkhj2ru91scjaahpr8v1e1.apps.googleusercontent.com"
    private const val GDRIVE_TOKEN_URL = "https://accounts.google.com/o/oauth2/token"

    private val EXCLUDED_LIBRARY_PACKAGES = arrayOf(
        "androidx",
        "android.support",
        "com.google.android.material",
        "com.google.android.gms",
        "com.android.billingclient",
        "kotlin",
        "kotlinx",
        "org.mortbay",
        "com.intel"
    )

    private val nativeLoadLock = Any()

    @Volatile
    private var nativeLoaded: Boolean = false

    private class ScanContext(
        val bridge: DexKitBridge,
        val dalvik: DalvikCodeInspector?,
        val onLog: ((Int, String, Throwable?) -> Unit)?
    ) {
        val classes = linkedMapOf<String, String>()
        val methods = linkedMapOf<String, String>()
        val fields = linkedMapOf<String, String>()
        val stringLists = linkedMapOf<String, List<String>>()
        val ints = linkedMapOf<String, Int>()

        var taskThreadClass: ClassData? = null
        var dirCatalogClass: ClassData? = null
        var fileCatalogClass: ClassData? = null
        var fxPathClass: ClassData? = null
        var dirItemClass: ClassData? = null
        var fileItemBaseClass: ClassData? = null
        var fileItemClass: ClassData? = null
        var viewerClass: ClassData? = null
        var viewerBaseClass: ClassData? = null
        var imageDisplayClass: ClassData? = null
        var settingsClass: ClassData? = null
        var homeContentViewClass: ClassData? = null
        var uiThemeClass: ClassData? = null
        var dimClass: ClassData? = null
        var viewerSupportHandlerField: FieldData? = null
    }

    fun scanAndBuildManifest(
        classLoader: ClassLoader,
        versionCode: Long,
        fingerprint: String,
        targetAppInfo: ApplicationInfo?,
        moduleAppInfo: ApplicationInfo?,
        onLog: ((Int, String, Throwable?) -> Unit)? = null
    ): FxSymbolManifest? {
        if (!ensureNativeLibraryLoaded(targetAppInfo, moduleAppInfo, onLog)) {
            return null
        }

        val bridge = createBridgeSafely(classLoader, targetAppInfo, onLog) ?: return null
        val dalvik = runCatching { DalvikCodeInspector.fromAppInfo(targetAppInfo) }.getOrNull()
        return runCatching {
            bridge.use { activeBridge ->
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                runCatching { activeBridge.setThreadNum(threads) }
                val ctx = ScanContext(activeBridge, dalvik, onLog)

                runSubsystem(ctx, "TaskThread") { scanTaskThread(ctx) }
                runSubsystem(ctx, "CoreAndroidData") { scanCoreAndAndroidData(ctx) }
                runSubsystem(ctx, "ViewerAndSiblings") { scanViewerAndSiblings(ctx) }
                runSubsystem(ctx, "HardwareRenderGif") { scanHardwareRenderAndGif(ctx) }
                runSubsystem(ctx, "SettingsTheme") { scanSettingsAndTheme(ctx) }
                runSubsystem(ctx, "HomeMenu") { scanHomeMenu(ctx) }
                runSubsystem(ctx, "GoogleDrive") { scanGoogleDrive(ctx) }
                runSubsystem(ctx, "RemoteThumb") { scanRemoteThumb(ctx) }
                runSubsystem(ctx, "FolderLifecycle") { scanFolderLifecycle(ctx) }
                runSubsystem(ctx, "BonusLimits") { scanBonusLimits(ctx) }

                val manifest = FxSymbolManifest(
                    schemaVersion = DexSymbolCache.SCHEMA_VERSION,
                    fingerprint = fingerprint,
                    classes = ctx.classes.toMap(),
                    methods = ctx.methods.toMap(),
                    fields = ctx.fields.toMap(),
                    stringLists = ctx.stringLists.toMap(),
                    ints = ctx.ints.toMap()
                )
                if (manifest.isUsable()) manifest else null
            }
        }.onFailure { err ->
            onLog?.invoke(Log.WARN, "DexKit scan aborted for v$versionCode: ${err.message}", err)
        }.getOrNull()
    }

    private fun runSubsystem(ctx: ScanContext, name: String, block: () -> Unit) {
        runCatching(block).onFailure { err ->
            ctx.onLog?.invoke(Log.WARN, "DexKit subsystem '$name' failed: ${err.message}", err)
        }
    }

    internal fun ensureNativeLibraryLoaded(
        targetAppInfo: ApplicationInfo?,
        moduleAppInfo: ApplicationInfo?,
        onLog: ((Int, String, Throwable?) -> Unit)? = null
    ): Boolean {
        if (nativeLoaded) return true
        synchronized(nativeLoadLock) {
            if (nativeLoaded) return true

            // Stage (a): standard JNI lookup on library search path.
            val stageA = runCatching {
                System.loadLibrary(NATIVE_LIB_NAME)
                true
            }.getOrDefault(false)
            if (stageA) {
                nativeLoaded = true
                return true
            }

            // Stage (b): direct nativeLibraryDir inside module APK install folder.
            val natDir = moduleAppInfo?.nativeLibraryDir?.trim()?.takeIf { it.isNotEmpty() }
            if (natDir != null) {
                val directSo = File(natDir, "lib$NATIVE_LIB_NAME.so")
                if (directSo.isFile && directSo.canRead()) {
                    val stageB = runCatching {
                        System.load(directSo.absolutePath)
                        true
                    }.onFailure { err ->
                        onLog?.invoke(Log.WARN, "DexKit stage (b) load failed at ${directSo.absolutePath}", err)
                    }.getOrDefault(false)
                    if (stageB) {
                        nativeLoaded = true
                        return true
                    }
                }
            }

            // Stage (c): extract matching ABI libdexkit.so from module sourceDir APK into host cache.
            val moduleApkPath = moduleAppInfo?.sourceDir?.trim()?.takeIf { it.isNotEmpty() }
            if (moduleApkPath != null) {
                val moduleApk = File(moduleApkPath)
                if (moduleApk.isFile && moduleApk.canRead()) {
                    val stageC = extractAndLoadFromModuleApk(moduleApk, targetAppInfo, onLog)
                    if (stageC) {
                        nativeLoaded = true
                        return true
                    }
                }
            }

            onLog?.invoke(Log.WARN, "Unable to load libdexkit.so across all 3 stages", null)
            return false
        }
    }

    private fun extractAndLoadFromModuleApk(
        moduleApk: File,
        targetAppInfo: ApplicationInfo?,
        onLog: ((Int, String, Throwable?) -> Unit)?
    ): Boolean {
        val hostCacheBase = targetAppInfo?.dataDir?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { File(it, "cache") }
            ?: runCatching { File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp") }.getOrNull()
            ?: return false

        val modVer = buildModuleVersionTag(moduleApk)
        val outDir = File(File(hostCacheBase, NATIVE_CACHE_DIR), modVer)
        val outSo = File(outDir, "lib$NATIVE_LIB_NAME.so")

        if (outSo.isFile && outSo.length() > 0L) {
            val loadedCached = runCatching {
                System.load(outSo.absolutePath)
                true
            }.getOrDefault(false)
            if (loadedCached) return true
        }

        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        if (abis.isEmpty()) return false

        return runCatching {
            if (!outDir.exists()) {
                outDir.mkdirs()
            }
            ZipFile(moduleApk).use { zip ->
                val matchedEntry = abis.firstNotNullOfOrNull { abi ->
                    zip.getEntry("lib/$abi/lib$NATIVE_LIB_NAME.so")
                } ?: return@use false

                val tmpFile = File(outDir, "lib$NATIVE_LIB_NAME.so.tmp.${System.nanoTime()}")
                zip.getInputStream(matchedEntry).use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                tmpFile.setReadable(true, false)
                if (outSo.exists()) {
                    outSo.delete()
                }
                if (!tmpFile.renameTo(outSo)) {
                    tmpFile.copyTo(outSo, overwrite = true)
                    tmpFile.delete()
                }
                outSo.setReadable(true, false)
                System.load(outSo.absolutePath)
                true
            }
        }.onFailure { err ->
            onLog?.invoke(Log.WARN, "DexKit stage (c) extract/load failed: ${err.message}", err)
        }.getOrDefault(false)
    }

    internal fun buildModuleVersionTag(moduleApk: File): String {
        return "v${moduleApk.length()}_${moduleApk.lastModified()}"
    }

    private fun createBridgeSafely(
        classLoader: ClassLoader,
        targetAppInfo: ApplicationInfo?,
        onLog: ((Int, String, Throwable?) -> Unit)?
    ): DexKitBridge? {
        val sourceDir = targetAppInfo?.sourceDir?.trim()?.takeIf { it.isNotEmpty() }
        val noSplits = targetAppInfo?.splitSourceDirs.isNullOrEmpty()

        if (sourceDir != null && noSplits) {
            val fromApk = runCatching { DexKitBridge.create(sourceDir) }
                .onFailure { err ->
                    onLog?.invoke(Log.DEBUG, "DexKitBridge.create(sourceDir) failed, trying ClassLoader: ${err.message}", null)
                }
                .getOrNull()
            if (fromApk != null) return fromApk
        }

        return runCatching { DexKitBridge.create(classLoader, true) }
            .onFailure { err ->
                onLog?.invoke(Log.WARN, "DexKitBridge.create(classLoader) failed: ${err.message}", err)
            }
            .getOrNull()
    }

    // -------------------------------------------------------------------------
    // Subsystem 1: TaskThread
    // -------------------------------------------------------------------------
    private fun scanTaskThread(ctx: ScanContext) {
        val threadCls = ctx.bridge.findClass {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                superClass = "java.lang.Thread"
                methods {
                    add {
                        usingStrings(listOf("Task Thread: "), StringMatchType.StartsWith)
                    }
                }
            }
        }.firstOrNull() ?: return

        ctx.taskThreadClass = threadCls
        val ctor = threadCls.methods.firstOrNull { m ->
            m.isConstructor && m.paramTypeNames == listOf(
                "java.lang.Class",
                "java.lang.String",
                "java.lang.Runnable"
            )
        }
        putMethod(ctx, FxSymbolKeys.CTR_TASK_THREAD, ctor)
    }

    // -------------------------------------------------------------------------
    // Subsystem 2: Core FileCatalog, FxPath, FileDir/FileFile, Exceptions
    // -------------------------------------------------------------------------
    private fun scanCoreAndAndroidData(ctx: ScanContext) {
        val dirCatalog = ctx.bridge.getClassData("nextapp.xf.dir.DirectoryCatalog")
        ctx.dirCatalogClass = dirCatalog
        putClass(ctx, FxSymbolKeys.CLS_DIR_CATALOG, dirCatalog)

        val getTypeMtd = dirCatalog?.methods?.firstOrNull { !it.isConstructor && it.methodName == "getType" }
        putMethod(ctx, FxSymbolKeys.MTD_GET_CATALOG_TYPE, getTypeMtd)

        val fileCatalog = ctx.bridge.getClassData("nextapp.fx.dirimpl.file.FileCatalog")
        ctx.fileCatalogClass = fileCatalog
        putClass(ctx, FxSymbolKeys.CLS_FILE_CATALOG, fileCatalog)

        val fxPath = resolveFxPathClass(ctx, fileCatalog)
        ctx.fxPathClass = fxPath
        putClass(ctx, FxSymbolKeys.CLS_FX_PATH, fxPath)

        if (fxPath != null) {
            val childCtor = fxPath.methods.firstOrNull { m ->
                m.isConstructor && m.paramTypeNames == listOf(fxPath.name, "java.lang.String")
            }
            putMethod(ctx, FxSymbolKeys.CTR_FX_PATH_CHILD, childCtor)

            val findElemMtd = fxPath.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramTypeNames == listOf("java.lang.Class") &&
                    m.returnTypeName == "int"
            }
            putMethod(ctx, FxSymbolKeys.MTD_FX_PATH_FIND_ELEMENT, findElemMtd)

            val elementsField = fxPath.fields.firstOrNull { f ->
                !Modifier.isStatic(f.modifiers) && f.typeName.endsWith("[]")
            }
            putField(ctx, FxSymbolKeys.FLD_FX_PATH_ELEMENTS, elementsField)

            val parentMtd = fxPath.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramCount == 0 &&
                    m.returnTypeName == fxPath.name &&
                    m.methodName != "clone"
            }
            putMethod(ctx, FxSymbolKeys.MTD_FX_PATH_PARENT, parentMtd)
        }

        // UserException: Log.d(..., "INTERNAL ERROR", this) inside constructor
        val userEx = ctx.bridge.findClass {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                superClass = "java.lang.Exception"
                methods {
                    add {
                        name = "<init>"
                        usingStrings(listOf("INTERNAL ERROR"), StringMatchType.Equals)
                    }
                }
            }
        }.firstOrNull()
        putClass(ctx, FxSymbolKeys.CLS_USER_EXCEPTION, userEx)

        // DirItem interface & FileItemBase / FileItem interfaces
        val dirItem = dirCatalog?.methods
            ?.firstOrNull { m -> m.paramCount == 1 && (fxPath == null || m.paramTypeNames[0] == fxPath.name) }
            ?.returnType
        ctx.dirItemClass = dirItem
        putClass(ctx, FxSymbolKeys.CLS_DIR_ITEM, dirItem)

        val listChildrenMtd = dirItem?.methods?.firstOrNull { m ->
            !m.isConstructor &&
                m.paramTypeNames == listOf("android.content.Context", "int") &&
                m.returnTypeName.endsWith("[]")
        }
        putMethod(ctx, FxSymbolKeys.MTD_LIST_CHILDREN, listChildrenMtd)

        val fileItemBase = listChildrenMtd?.returnTypeName?.removeSuffix("[]")
            ?.let { ctx.bridge.getClassData(it) }
        ctx.fileItemBaseClass = fileItemBase
        putClass(ctx, FxSymbolKeys.CLS_FILE_ITEM_BASE, fileItemBase)

        if (fileItemBase != null) {
            putMethod(ctx, FxSymbolKeys.MTD_GET_PATH, fileItemBase.methods.firstOrNull { it.methodName == "getPath" && it.paramCount == 0 })
            putMethod(ctx, FxSymbolKeys.MTD_GET_NAME, fileItemBase.methods.firstOrNull { it.methodName == "getName" && it.paramCount == 0 })
            putMethod(ctx, FxSymbolKeys.MTD_GET_LAST_MODIFIED, fileItemBase.methods.firstOrNull { it.methodName == "getLastModified" && it.paramCount == 0 })

            val dirCatalogIfaces = dirCatalog?.interfaces?.mapTo(hashSetOf()) { it.name }.orEmpty()
            val getCatalogMtd = fileItemBase.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramCount == 0 &&
                    (m.returnTypeName in dirCatalogIfaces || m.returnTypeName == dirCatalog?.name)
            }
            putMethod(ctx, FxSymbolKeys.MTD_GET_CATALOG, getCatalogMtd)
        }

        val fileItem = dirItem?.methods?.firstOrNull { m ->
            !m.isConstructor &&
                m.paramTypeNames == listOf("android.content.Context", "java.lang.CharSequence") &&
                m.returnTypeName != "boolean" &&
                m.returnTypeName != "void" &&
                m.returnTypeName != dirItem.name
        }?.returnType
        ctx.fileItemClass = fileItem
        putClass(ctx, FxSymbolKeys.CLS_FILE_ITEM, fileItem)

        if (fileItem != null) {
            putMethod(ctx, FxSymbolKeys.MTD_GET_SIZE, fileItem.methods.firstOrNull { it.methodName == "getSize" && it.paramCount == 0 })
            putMethod(ctx, FxSymbolKeys.MTD_OPEN_INPUT_STREAM, fileItem.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramTypeNames == listOf("android.content.Context") &&
                    m.returnTypeName == "java.io.InputStream"
            })
            putMethod(ctx, FxSymbolKeys.MTD_GET_MIME_TYPE, fileItem.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramCount == 0 &&
                    m.returnTypeName == "java.lang.String" &&
                    m.methodName != "getName" &&
                    m.methodName != "toString"
            })
        }

        if (fileCatalog != null && fxPath != null) {
            putField(
                ctx,
                FxSymbolKeys.FLD_FILE_CATALOG_SAF_URI,
                fileCatalog.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.net.Uri" }
            )

            val openDirMtd = fileCatalog.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramTypeNames == listOf(fxPath.name) &&
                    m.returnTypeName != "java.io.File" &&
                    m.returnTypeName != "void" &&
                    dirCatalog != null &&
                    (m.returnTypeName == dirItem?.name || m.returnType?.interfaces?.any { it.name == dirItem?.name } == true)
            } ?: fileCatalog.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramTypeNames == listOf(fxPath.name) &&
                    m.returnTypeName != "java.io.File" &&
                    m.returnTypeName != "void"
            }
            putMethod(ctx, FxSymbolKeys.MTD_FILE_CATALOG_OPEN_DIR, openDirMtd)

            val resolveFileMtd = fileCatalog.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramTypeNames == listOf(fxPath.name) &&
                    m.returnTypeName == "java.io.File"
            }
            putMethod(ctx, FxSymbolKeys.MTD_FILE_CATALOG_RESOLVE_FILE, resolveFileMtd)

            val fileDirNode = resolveFileDirNodeClass(ctx, fileCatalog, openDirMtd, dirItem)
            putClass(ctx, FxSymbolKeys.CLS_FILE_DIR_NODE, fileDirNode)

            val fileNodeBase = fileDirNode?.superClass
            putClass(ctx, FxSymbolKeys.CLS_FILE_NODE_BASE, fileNodeBase)

            if (fileDirNode != null) {
                populateFileDirBindings(ctx, fileDirNode, fileCatalog, fxPath)
            }

            if (fileNodeBase != null) {
                populateFileNodeBaseBindings(ctx, fileNodeBase, fileCatalog, fxPath)

                val fileFileNode = ctx.bridge.findClass {
                    findFirst = true
                    searchPackages(fileNodeBase.name.substringBeforeLast('.', ""))
                    matcher {
                        superClass = fileNodeBase.name
                        methods {
                            add {
                                name = "<init>"
                                paramTypes(fileCatalog.name, fxPath.name, "java.io.File")
                            }
                        }
                    }
                }.firstOrNull { it.name != fileDirNode?.name }
                    ?: ctx.bridge.findClass {
                        excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
                        matcher {
                            superClass = fileNodeBase.name
                        }
                    }.firstOrNull { cls ->
                        cls.name != fileDirNode?.name &&
                            cls.methods.any { m ->
                                m.isConstructor &&
                                    m.paramTypeNames == listOf(fileCatalog.name, fxPath.name, "java.io.File")
                            }
                    }

                putClass(ctx, FxSymbolKeys.CLS_FILE_FILE_NODE, fileFileNode)
                if (fileFileNode != null) {
                    populateFileFileBindings(ctx, fileFileNode, fileCatalog, fxPath)
                }
            }
        }

        // HiddenFileStore
        val hiddenStore = ctx.bridge.getClassData("nextapp.fx.dir.optionstore.HiddenFileStore")
        putMethod(
            ctx,
            FxSymbolKeys.MTD_HIDDEN_FILE_STORE_IS_HIDDEN,
            hiddenStore?.methods?.firstOrNull { m ->
                !m.isConstructor &&
                    Modifier.isStatic(m.modifiers) &&
                    m.paramTypeNames == listOf("java.lang.String") &&
                    m.returnTypeName == "boolean"
            }
        )
    }

    private fun resolveFxPathClass(ctx: ScanContext, fileCatalog: ClassData?): ClassData? {
        fileCatalog ?: return null
        for (f in fileCatalog.fields) {
            if (Modifier.isStatic(f.modifiers)) continue
            val t = f.type ?: continue
            val hasChildCtor = t.methods.any { m ->
                m.isConstructor && m.paramTypeNames == listOf(t.name, "java.lang.String")
            }
            if (hasChildCtor) return t
        }
        return null
    }

    private fun resolveFileDirNodeClass(
        ctx: ScanContext,
        fileCatalog: ClassData,
        openDirMtd: MethodData?,
        dirItem: ClassData?
    ): ClassData? {
        val pkg = fileCatalog.name.substringBeforeLast('.', "nextapp.fx.dirimpl.file")
        val retType = openDirMtd?.returnType
        if (retType != null && !Modifier.isInterface(retType.modifiers)) {
            return retType
        }
        val fromOpenDir = openDirMtd?.invokes
            ?.filter { it.isConstructor && it.declaredClassName != fileCatalog.name && it.declaredClassName != ctx.fxPathClass?.name }
            ?.mapNotNull { it.declaredClass }
            ?.firstOrNull { cls ->
                dirItem == null || cls.interfaces.any { it.name == dirItem.name }
            }
        if (fromOpenDir != null) return fromOpenDir

        return ctx.bridge.findClass {
            findFirst = true
            searchPackages(pkg)
            matcher {
                if (dirItem != null) {
                    interfaces { add { className = dirItem.name } }
                }
            }
        }.firstOrNull()
    }

    private fun populateFileDirBindings(
        ctx: ScanContext,
        fileDirNode: ClassData,
        fileCatalog: ClassData,
        fxPath: ClassData
    ) {
        val listMtd = fileDirNode.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context", "int") &&
                m.returnTypeName.endsWith("[]")
        }
        putMethod(ctx, FxSymbolKeys.MTD_FILE_DIR_LIST_CHILDREN, listMtd)

        // Discover CancelException thrown directly by FileDir.listChildren
        if (listMtd != null) {
            val taskPkg = ctx.taskThreadClass?.name?.substringBeforeLast('.', "")
            val cancelCls = listMtd.invokes
                .filter { it.isConstructor && it.paramCount == 0 }
                .mapNotNull { it.declaredClass }
                .firstOrNull { cls ->
                    (taskPkg == null || cls.name.startsWith("$taskPkg.")) &&
                        cls.superClass?.name == "java.lang.Exception"
                }
                ?: listMtd.invokes
                    .filter { it.isConstructor && it.paramCount == 0 }
                    .mapNotNull { it.declaredClass }
                    .firstOrNull { it.superClass?.name == "java.lang.Exception" && !it.name.startsWith("java.") }
            putClass(ctx, FxSymbolKeys.CLS_CANCEL_EXCEPTION, cancelCls)
        }

        putMethod(ctx, FxSymbolKeys.MTD_FILE_DIR_MKDIR, fileDirNode.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context", "java.lang.CharSequence", "boolean")
        })
        putMethod(ctx, FxSymbolKeys.MTD_FILE_DIR_CHILD_AVAILABLE, fileDirNode.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context", "java.lang.CharSequence") &&
                m.returnTypeName == "boolean"
        })
        putMethod(ctx, FxSymbolKeys.MTD_FILE_DIR_DELETE_RECURSIVE, fileDirNode.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context", "boolean") &&
                m.returnTypeName == "void"
        })
        val catalogStatMtd = fileCatalog.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context") &&
                m.returnTypeName == "void" &&
                m.invokes.any { inv -> inv.declaredClassName == "android.os.StatFs" }
        }
        putMethod(
            ctx,
            FxSymbolKeys.MTD_FILE_DIR_STAT_VOLUME,
            fileDirNode.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramTypeNames == listOf("android.content.Context") &&
                    m.returnTypeName == "void" &&
                    (
                        (catalogStatMtd != null && m.invokes.any { inv -> inv.descriptor == catalogStatMtd.descriptor }) ||
                            m.invokes.any { inv -> inv.declaredClassName == "android.os.StatFs" }
                        )
            }
        )

        val cachedFilesFld = fileDirNode.fields.firstOrNull { f ->
            !Modifier.isStatic(f.modifiers) && f.typeName == "java.io.File[]"
        }
        putField(ctx, FxSymbolKeys.FLD_FILE_DIR_CACHED_FILES, cachedFilesFld)

        val clearCacheMtd = fileDirNode.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramCount == 0 &&
                m.returnTypeName == "void" &&
                cachedFilesFld != null &&
                m.usingFields.any { uf -> uf.usingType.isWrite() && uf.field.descriptor == cachedFilesFld.descriptor }
        } ?: fileDirNode.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramCount == 0 &&
                m.returnTypeName == "void" &&
                Modifier.isSynchronized(m.modifiers)
        }
        putMethod(ctx, FxSymbolKeys.MTD_FILE_DIR_CLEAR_CACHE, clearCacheMtd)

        val dirCtor9012 = fileDirNode.methods.firstOrNull { m ->
            m.isConstructor && m.paramTypeNames == listOf(fileCatalog.name, fxPath.name, "java.io.File")
        }
        putMethod(ctx, FxSymbolKeys.CTR_FILE_DIR_9012, dirCtor9012)
    }

    private fun populateFileNodeBaseBindings(
        ctx: ScanContext,
        base: ClassData,
        fileCatalog: ClassData,
        fxPath: ClassData
    ) {
        putField(ctx, FxSymbolKeys.FLD_FILE_NODE_CATALOG, base.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == fileCatalog.name })
        putField(ctx, FxSymbolKeys.FLD_FILE_NODE_PATH, base.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == fxPath.name })
        putField(ctx, FxSymbolKeys.FLD_FILE_NODE_JAVA_FILE, base.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "java.io.File" })
        putField(ctx, FxSymbolKeys.FLD_FILE_NODE_METRICS, base.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "long" })

        putMethod(ctx, FxSymbolKeys.MTD_FILE_NODE_DELETE_SINGLE, base.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context", "boolean") &&
                m.returnTypeName == "void"
        })
        putMethod(ctx, FxSymbolKeys.MTD_FILE_NODE_RENAME, base.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context", "java.lang.String")
        })
        putMethod(ctx, FxSymbolKeys.MTD_FILE_NODE_MOVE_TO, base.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context", fxPath.name)
        })
        putMethod(ctx, FxSymbolKeys.MTD_FILE_NODE_GET_LAST_MODIFIED, base.methods.firstOrNull { it.methodName == "getLastModified" && it.paramCount == 0 })
        putMethod(ctx, FxSymbolKeys.MTD_FILE_NODE_IS_READ_ONLY, base.methods.firstOrNull { it.methodName == "isReadOnly" && it.paramCount == 0 })
        putMethod(ctx, FxSymbolKeys.MTD_FILE_NODE_SET_LAST_MODIFIED, base.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context", "long")
        })
    }

    private fun populateFileFileBindings(
        ctx: ScanContext,
        fileNode: ClassData,
        fileCatalog: ClassData,
        fxPath: ClassData
    ) {
        putMethod(ctx, FxSymbolKeys.CTR_FILE_FILE, fileNode.methods.firstOrNull { m ->
            m.isConstructor && m.paramTypeNames == listOf(fileCatalog.name, fxPath.name, "java.io.File")
        })

        val cachedSizeFld = fileNode.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "long" }
        putField(ctx, FxSymbolKeys.FLD_FILE_FILE_CACHED_SIZE, cachedSizeFld)

        putMethod(ctx, FxSymbolKeys.MTD_FILE_FILE_STAT_SIZE, fileNode.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context") &&
                (cachedSizeFld == null || m.usingFields.any { uf -> uf.usingType.isWrite() && uf.field.descriptor == cachedSizeFld.descriptor })
        })
        putMethod(ctx, FxSymbolKeys.MTD_FILE_FILE_OPEN_INPUT, fileNode.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context") &&
                m.returnTypeName == "java.io.InputStream"
        })
        putMethod(ctx, FxSymbolKeys.MTD_FILE_FILE_OPEN_OUTPUT_INTERNAL, fileNode.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context", "boolean") &&
                m.returnTypeName == "java.io.OutputStream"
        })
        putMethod(ctx, FxSymbolKeys.MTD_FILE_FILE_DELETE, fileNode.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context", "boolean") &&
                m.returnTypeName == "void"
        })
    }

    // -------------------------------------------------------------------------
    // Subsystem 3: Viewer, Prefetch & Sibling Swipe
    // -------------------------------------------------------------------------
    private fun scanViewerAndSiblings(ctx: ScanContext) {
        val viewer = ctx.bridge.getClassData("nextapp.fx.ui.viewer.image.ImageViewerActivity")
        ctx.viewerClass = viewer
        putClass(ctx, FxSymbolKeys.CLS_IMAGE_VIEWER_ACTIVITY, viewer)

        val viewerBase = viewer?.superClass
        ctx.viewerBaseClass = viewerBase
        putClass(ctx, FxSymbolKeys.CLS_VIEWER_BASE, viewerBase)

        val imageDisplay = ctx.bridge.getClassData("nextapp.fx.ui.viewer.image.ImageDisplay")
        ctx.imageDisplayClass = imageDisplay
        putClass(ctx, FxSymbolKeys.CLS_IMAGE_DISPLAY, imageDisplay)

        val dirOptStore = ctx.bridge.getClassData("nextapp.fx.dir.optionstore.DirectoryOptionStore")
        putClass(ctx, FxSymbolKeys.CLS_DIRECTORY_OPTION_STORE, dirOptStore)

        // Viewer base hooks
        if (viewerBase != null) {
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_ON_DESTROY, viewerBase.methods.firstOrNull { it.methodName == "onDestroy" && it.paramCount == 0 })
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_OPEN_STREAM, viewerBase.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramCount == 0 &&
                    m.returnTypeName == "java.io.InputStream"
            })
            val mirrorMtd = viewerBase.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.returnTypeName == "void" &&
                    m.paramCount >= 1 &&
                    ctx.fileItemClass != null &&
                    m.paramTypeNames[0] == ctx.fileItemClass?.name &&
                    (m.paramCount == 1 || (m.paramCount == 2 && m.paramTypeNames[1] == "long"))
            }
            putMethod(ctx, FxSymbolKeys.MTD_MIRROR_REMOTE_TEMP_FILE, mirrorMtd)
        }

        if (viewer != null) {
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_ON_CREATE, viewer.methods.firstOrNull { it.methodName == "onCreate" })
            putField(ctx, FxSymbolKeys.FLD_VIEWER_PROGRESS_BAR, viewer.fields.singleOrNull { it.typeName == "android.widget.ProgressBar" })
            putField(ctx, FxSymbolKeys.FLD_VIEWER_CURRENT_DISPLAY, imageDisplay?.let { d -> viewer.fields.firstOrNull { it.typeName == d.name } })
            putField(ctx, FxSymbolKeys.FLD_VIEWER_SIBLING_ITEMS, ctx.fileItemClass?.let { fi -> viewer.fields.firstOrNull { it.typeName == "${fi.name}[]" } })
            putField(ctx, FxSymbolKeys.FLD_VIEWER_INITIAL_ITEM, ctx.fileItemClass?.let { fi -> viewer.fields.firstOrNull { it.typeName == fi.name } })
        }

        // ViewerSupportActivity handler & error method (e.g. nextapp.fx.ui.activitysupport.j or af.h)
        val supportCls = viewerBase?.superClass
        if (supportCls != null) {
            val handlerFld = supportCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.os.Handler" }
                ?: supportCls.superClass?.fields?.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.os.Handler" }
            ctx.viewerSupportHandlerField = handlerFld
            putField(ctx, FxSymbolKeys.FLD_VIEWER_SUPPORT_HANDLER, handlerFld)

            val showErrMtd = findShowErrorMethod(supportCls)
                ?: supportCls.superClass?.let { findShowErrorMethod(it) }
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_SUPPORT_SHOW_ERROR, showErrMtd)
        }

        // Sibling enumerator ("ImageViewer: Unable to retrieve sibling files")
        val enumerate = ctx.bridge.findMethod {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                usingStrings(listOf("ImageViewer: Unable to retrieve sibling files"), StringMatchType.StartsWith)
            }
        }.firstOrNull()

        if (enumerate != null) {
            putClass(ctx, FxSymbolKeys.CLS_SIBLING_LOADER, enumerate.declaredClass)
            putMethod(ctx, FxSymbolKeys.MTD_ENUMERATE_SIBLINGS, enumerate)

            val mimesField = enumerate.usingFields
                .map { it.field }
                .firstOrNull { f ->
                    Modifier.isStatic(f.modifiers) &&
                        (f.typeName == "java.util.Collection" || f.typeName == "java.util.Set")
                }
            putField(ctx, FxSymbolKeys.FLD_SUPPORTED_MIMES, mimesField)

            val sortMtd = enumerate.invokes.firstOrNull { inv ->
                Modifier.isStatic(inv.modifiers) &&
                    inv.paramCount == 4 &&
                    inv.paramTypeNames[0].endsWith("[]") &&
                    inv.paramTypeNames[2] == "boolean" &&
                    inv.paramTypeNames[3] == "boolean"
            }
            putMethod(ctx, FxSymbolKeys.MTD_SORT_ITEMS, sortMtd)

            val getCatalogTypeReturn = ctx.dirCatalogClass?.methods
                ?.firstOrNull { it.methodName == "getType" && it.paramCount == 0 }
                ?.returnTypeName
            val isRemoteMtd = enumerate.invokes.firstOrNull { inv ->
                inv.returnTypeName == "boolean" &&
                    inv.declaredClassName != ctx.dirCatalogClass?.name &&
                    inv.declaredClassName != ctx.fileItemClass?.name &&
                    (
                        (!Modifier.isStatic(inv.modifiers) && inv.paramCount == 0 && inv.declaredClassName == getCatalogTypeReturn) ||
                            (Modifier.isStatic(inv.modifiers) && inv.paramTypeNames == listOf("int"))
                        )
            }
            putMethod(ctx, FxSymbolKeys.MTD_IS_REMOTE, isRemoteMtd)

            val sortOrderTypeName = sortMtd?.paramTypeNames?.getOrNull(1)
            populateDirectoryOptionStore(ctx, dirOptStore, sortOrderTypeName)

            if (enumerate.paramCount == 6) {
                populateClassicSiblingBindings(ctx, enumerate, sortOrderTypeName)
            } else if (enumerate.paramCount == 0) {
                populateModernSiblingBindings(ctx, enumerate, sortOrderTypeName)
            }
        }

        // Open item stream (9012 static/instance V1) & StreamProvider (9108 ImageDisplay$c) & decodeItem
        var openItemStreamDesc: String? = null
        if (viewer != null && ctx.fileItemClass != null) {
            val openItemStreamMtd = viewer.methods.firstOrNull { m ->
                !m.isConstructor &&
                    m.paramTypeNames == listOf(ctx.fileItemClass!!.name, "android.content.Context") &&
                    m.returnTypeName == "java.io.InputStream"
            }
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_OPEN_ITEM_STREAM, openItemStreamMtd)
            openItemStreamDesc = openItemStreamMtd?.descriptor

            val decodeMtd = viewer.methods.firstOrNull { m ->
                !m.isConstructor &&
                    m.paramCount == 3 &&
                    (m.paramTypeNames[0] == "android.content.Context" || m.paramTypeNames[0] == viewer.name) &&
                    m.paramTypeNames[1] == ctx.fileItemClass!!.name &&
                    m.paramTypeNames[2] == "int"
            }
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_DECODE_ITEM, decodeMtd)
        }

        // StreamProvider (9108 c.read() invokes FileItem.openInputStream(Context); 9012 u.read() delegates to static V1)
        val openInputStreamDesc = ctx.methods[FxSymbolKeys.MTD_OPEN_INPUT_STREAM]
        if (openInputStreamDesc != null && viewer != null) {
            val viewerPkg = viewer.name.substringBeforeLast('.', "nextapp.fx.ui.viewer.image")
            val streamProviderCandidates = ctx.bridge.findClass {
                searchPackages(viewerPkg)
                matcher {
                    methods {
                        add {
                            name = "read"
                            paramCount = 0
                            returnType = "java.io.InputStream"
                        }
                    }
                }
            }
            val directStreamProvider = streamProviderCandidates.firstOrNull { cls ->
                val rm = cls.methods.firstOrNull { it.methodName == "read" && it.paramCount == 0 }
                rm != null && rm.invokes.any { inv -> inv.descriptor == openInputStreamDesc }
            } ?: if (openItemStreamDesc == null) streamProviderCandidates.firstOrNull() else null

            if (directStreamProvider != null) {
                val readMtd = directStreamProvider.methods.firstOrNull { it.methodName == "read" && it.paramCount == 0 }
                putMethod(ctx, FxSymbolKeys.MTD_VIEWER_STREAM_PROVIDER_READ, readMtd)

                val itemFieldDescFromInvoke = readMtd?.let {
                    ctx.dalvik?.findFieldReceiverForInvoke(it.descriptor, openInputStreamDesc)
                }
                val itemFieldDescFromCtor = ctx.fileItemClass?.let { fi ->
                    directStreamProvider.methods.firstOrNull { m ->
                        m.isConstructor && m.paramTypeNames == listOf(fi.name, viewer.name)
                    }?.let { ctor ->
                        ctx.dalvik?.findConstructorParamFieldWrites(ctor.descriptor)?.paramToField?.get(1)
                    }
                }
                val resolvedItemFld = (itemFieldDescFromInvoke ?: itemFieldDescFromCtor)
                    ?.let { desc -> directStreamProvider.fields.firstOrNull { it.descriptor == desc } }
                    ?: directStreamProvider.fields.firstOrNull { f ->
                        !Modifier.isStatic(f.modifiers) && f.typeName == ctx.fileItemClass?.name
                    }
                    ?: directStreamProvider.fields.lastOrNull { f ->
                        !Modifier.isStatic(f.modifiers) &&
                            f.typeName == "java.lang.Object" &&
                            readMtd?.usingFields?.any { uf -> uf.usingType.isRead() && uf.field.descriptor == f.descriptor } == true
                    }
                putField(ctx, FxSymbolKeys.FLD_VIEWER_STREAM_PROVIDER_ITEM, resolvedItemFld)
            }
        }

        // Media loader & GIF animated check anchored on "Failed to test if GIF is animated."
        scanViewerMediaLoaders(ctx)

        // Sibling & DisplayListener bindings on ImageDisplay / ImageViewerActivity
        if (viewer != null && imageDisplay != null) {
            resolveImageDisplayAndListener(ctx, viewer, imageDisplay)
        }

        // RemoteFileItem classes implementing FileItem and overriding openInputStream
        resolveRemoteItemClasses(ctx)
    }

    private fun findShowErrorMethod(supportCls: ClassData): MethodData? {
        return supportCls.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("int") &&
                m.returnTypeName == "void" &&
                m.invokes.any { inv -> inv.declaredClassName == "android.widget.Toast" || inv.methodName == "getString" }
        }
    }

    private fun populateDirectoryOptionStore(
        ctx: ScanContext,
        dirOptStore: ClassData?,
        sortOrderTypeName: String?
    ) {
        dirOptStore ?: return
        val getSortOpts = dirOptStore.methods.firstOrNull { m ->
            !m.isConstructor &&
                Modifier.isStatic(m.modifiers) &&
                m.paramCount == 1 &&
                m.paramTypeNames[0] == ctx.fileItemBaseClass?.name &&
                m.returnTypeName != "java.lang.String" &&
                m.returnTypeName != "void"
        }
        putMethod(ctx, FxSymbolKeys.MTD_GET_SORT_OPTIONS, getSortOpts)

        val pairCls = getSortOpts?.returnType
        if (pairCls != null) {
            putField(ctx, FxSymbolKeys.FLD_SORT_PAIR_DESC, pairCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "boolean" })
            putField(ctx, FxSymbolKeys.FLD_SORT_PAIR_ORDER, pairCls.fields.firstOrNull { f ->
                !Modifier.isStatic(f.modifiers) &&
                    f.typeName != "boolean" &&
                    (sortOrderTypeName == null || f.typeName == sortOrderTypeName)
            })
        }
    }

    private fun populateClassicSiblingBindings(
        ctx: ScanContext,
        enumerate: MethodData,
        sortOrderTypeName: String?
    ) {
        val cbInterface = enumerate.paramTypes.getOrNull(5) ?: return
        val viewer = ctx.viewerClass ?: return
        val viewerPkg = viewer.name.substringBeforeLast('.', "nextapp.fx.ui.viewer.image")

        val cbImpl = ctx.bridge.findClass {
            findFirst = true
            searchPackages(viewerPkg)
            matcher {
                interfaces { add { className = cbInterface.name } }
            }
        }.firstOrNull() ?: cbInterface

        putClass(ctx, FxSymbolKeys.CLS_SIBLING_CALLBACK, cbImpl)

        val loadedMtd = cbImpl.methods.firstOrNull { m ->
            !m.isConstructor &&
                m.paramCount == 2 &&
                m.paramTypeNames[0].endsWith("[]") &&
                m.paramTypeNames[1] == "int"
        }
        putMethod(ctx, FxSymbolKeys.MTD_SIBLING_CB_LOADED, loadedMtd)

        val failMtd = cbImpl.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramCount == 0 &&
                m.returnTypeName == "void"
        }
        putMethod(ctx, FxSymbolKeys.MTD_SIBLING_CB_FAIL, failMtd)

        val activityFld = cbImpl.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == viewer.name }
        putField(ctx, FxSymbolKeys.FLD_SIBLING_CB_ACTIVITY, activityFld)

        if (loadedMtd != null) {
            val viewerWrites = loadedMtd.usingFields
                .filter { it.usingType.isWrite() && it.field.declaredClassName == viewer.name }
                .map { it.field }
            putField(ctx, FxSymbolKeys.FLD_VIEWER_SIBLINGS_LOADED, viewerWrites.firstOrNull { it.typeName == "boolean" })
            putField(ctx, FxSymbolKeys.FLD_VIEWER_SIBLING_ITEMS, viewerWrites.firstOrNull { it.typeName.endsWith("[]") })
        }
        if (sortOrderTypeName != null) {
            putField(ctx, FxSymbolKeys.FLD_VIEWER_SORT_ORDER, viewer.fields.firstOrNull { it.typeName == sortOrderTypeName })
        }
        if (ctx.fields[FxSymbolKeys.FLD_VIEWER_SORT_DESC] == null) {
            val cbCallers = cbImpl.methods.filter { it.isConstructor }.flatMap { it.callers }
            val sortBoolRead = cbCallers.firstOrNull()?.usingFields
                ?.firstOrNull { uf -> uf.usingType.isRead() && uf.field.declaredClassName == viewer.name && uf.field.typeName == "boolean" }
                ?.field
            putField(ctx, FxSymbolKeys.FLD_VIEWER_SORT_DESC, sortBoolRead)
        }
    }

    private fun populateModernSiblingBindings(
        ctx: ScanContext,
        enumerate: MethodData,
        sortOrderTypeName: String?
    ) {
        val runner = enumerate.declaredClass ?: return
        val viewer = ctx.viewerClass
        val dirFld = runner.fields.firstOrNull { it.typeName == ctx.dirItemClass?.name }
        val ctxFld = runner.fields.firstOrNull { it.typeName == "android.content.Context" }
            ?: viewer?.let { v -> runner.fields.firstOrNull { it.typeName == v.name } }
        val sortDescFld = runner.fields.firstOrNull { it.typeName == "boolean" }
        val currentItemFld = runner.fields.firstOrNull { it.typeName == ctx.fileItemClass?.name }
        val sortOrderFld = runner.fields.firstOrNull { f ->
            sortOrderTypeName != null && f.typeName == sortOrderTypeName
        } ?: runner.fields.firstOrNull { f ->
            f.descriptor != dirFld?.descriptor &&
                f.descriptor != ctxFld?.descriptor &&
                f.descriptor != sortDescFld?.descriptor &&
                f.descriptor != currentItemFld?.descriptor &&
                f.fieldsCountHint(ctx) == 0 &&
                f.type?.methods?.any { m -> !m.isConstructor && m.returnTypeName == f.typeName } == true
        }
        val callbackHolderFld = runner.fields.firstOrNull { f ->
            f.descriptor != dirFld?.descriptor &&
                f.descriptor != ctxFld?.descriptor &&
                f.descriptor != sortDescFld?.descriptor &&
                f.descriptor != currentItemFld?.descriptor &&
                f.descriptor != sortOrderFld?.descriptor
        }

        putField(ctx, FxSymbolKeys.FLD_RUNNER_DIR, dirFld)
        putField(ctx, FxSymbolKeys.FLD_RUNNER_CTX, ctxFld)
        putField(ctx, FxSymbolKeys.FLD_RUNNER_SORT_ORDER, sortOrderFld)
        putField(ctx, FxSymbolKeys.FLD_RUNNER_SORT_DESC, sortDescFld)
        putField(ctx, FxSymbolKeys.FLD_RUNNER_CURRENT_ITEM, currentItemFld)
        putField(ctx, FxSymbolKeys.FLD_RUNNER_CALLBACK_HOLDER, callbackHolderFld)

        if (viewer != null) {
            val viewerWrites = enumerate.usingFields
                .filter { it.usingType.isWrite() && it.field.declaredClassName == viewer.name }
                .map { it.field }
            putField(ctx, FxSymbolKeys.FLD_VIEWER_SIBLING_ITEMS, viewerWrites.firstOrNull { it.typeName.endsWith("[]") })
            putField(ctx, FxSymbolKeys.FLD_VIEWER_SIBLINGS_LOADED, viewerWrites.firstOrNull { it.typeName == "boolean" })

            if (sortOrderTypeName != null) {
                putField(ctx, FxSymbolKeys.FLD_VIEWER_SORT_ORDER, viewer.fields.firstOrNull { it.typeName == sortOrderTypeName })
            } else if (sortOrderFld != null) {
                putField(ctx, FxSymbolKeys.FLD_VIEWER_SORT_ORDER, viewer.fields.firstOrNull { it.typeName == sortOrderFld.typeName })
            }

            // Disambiguate viewerSortDescField (`I2`) from failure flag (`D2`) & siblingsLoaded (`x2`):
            // Unlike `x2` and `D2`, `sortDesc` is ONLY read in g.run() right after `viewerSortOrderField` (`M2`), never written in g.run() or b0.run().
            val runnerCallers = runner.methods.filter { it.isConstructor }.flatMap { it.callers }
            val writtenBoolDescs = (runnerCallers + listOf(enumerate))
                .flatMap { it.usingFields }
                .filter { uf -> uf.usingType.isWrite() && uf.field.declaredClassName == viewer.name && uf.field.typeName == "boolean" }
                .map { it.field.descriptor }
                .toHashSet()
            val siblingsLoadedDesc = ctx.fields[FxSymbolKeys.FLD_VIEWER_SIBLINGS_LOADED]
            val sortOrderDesc = ctx.fields[FxSymbolKeys.FLD_VIEWER_SORT_ORDER]

            val primaryCaller = runnerCallers.firstOrNull()
            val callerFieldOrder = primaryCaller?.usingFields
                ?.filter { uf -> uf.usingType.isRead() && uf.field.declaredClassName == viewer.name }
                ?.map { it.field }
                .orEmpty()
            val sortOrderReadIdx = if (sortOrderDesc != null) {
                callerFieldOrder.indexOfFirst { it.descriptor == sortOrderDesc }
            } else {
                -1
            }
            val sortDescOnViewer = (
                if (sortOrderReadIdx >= 0) {
                    callerFieldOrder.drop(sortOrderReadIdx + 1)
                } else {
                    callerFieldOrder
                }
                ).firstOrNull { f ->
                f.typeName == "boolean" &&
                    f.descriptor != siblingsLoadedDesc &&
                    f.descriptor !in writtenBoolDescs
            } ?: callerFieldOrder.firstOrNull { f ->
                f.typeName == "boolean" &&
                    f.descriptor != siblingsLoadedDesc &&
                    f.descriptor !in writtenBoolDescs
            }
            putField(ctx, FxSymbolKeys.FLD_VIEWER_SORT_DESC, sortDescOnViewer)
        }
    }

    private fun FieldData.fieldsCountHint(ctx: ScanContext): Int = type?.fieldCount ?: 0

    private fun scanViewerMediaLoaders(ctx: ScanContext) {
        val viewer = ctx.viewerClass ?: return
        val imageDisplay = ctx.imageDisplayClass ?: return

        val loaderMtd = ctx.bridge.findMethod {
            findFirst = true
            searchInClass(listOf(viewer))
            matcher {
                usingStrings(listOf("Failed to test if GIF is animated."), StringMatchType.Contains)
            }
        }.firstOrNull() ?: return

        val holderClass = loaderMtd.paramTypes.getOrNull(1)
        if (holderClass != null && holderClass.fields.size >= 2) {
            val itemFld = holderClass.fields.firstOrNull { f ->
                !Modifier.isStatic(f.modifiers) && f.typeName == ctx.fileItemClass?.name
            } ?: holderClass.fields.firstOrNull { !Modifier.isStatic(it.modifiers) }
            val uriFld = holderClass.fields.firstOrNull { f ->
                !Modifier.isStatic(f.modifiers) && f.typeName == "android.net.Uri"
            } ?: holderClass.fields.firstOrNull { f ->
                !Modifier.isStatic(f.modifiers) && f.descriptor != itemFld?.descriptor
            }
            putField(ctx, FxSymbolKeys.FLD_ITEM_HOLDER_FILE_ITEM, itemFld)
            putField(ctx, FxSymbolKeys.FLD_ITEM_HOLDER_URI, uriFld)
            putField(ctx, FxSymbolKeys.FLD_VIEWER_CURRENT_HOLDER, viewer.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == holderClass.name })
        }

        // Animated GIF probe invoked right inside that block: static (InputStream) -> boolean
        val gifCheckMtd = loaderMtd.invokes.firstOrNull { inv ->
            Modifier.isStatic(inv.modifiers) &&
                inv.paramTypeNames == listOf("java.io.InputStream") &&
                inv.returnTypeName == "boolean"
        }
        putMethod(ctx, FxSymbolKeys.MTD_HW_GIF_ANIMATED_CHECK, gifCheckMtd)

        // isGifField: boolean field written inside loaderMtd
        val isGifFld = loaderMtd.usingFields.firstOrNull { uf ->
            uf.usingType.isWrite() &&
                uf.field.declaredClassName == viewer.name &&
                uf.field.typeName == "boolean"
        }?.field
        putField(ctx, FxSymbolKeys.FLD_HW_IS_GIF, isGifFld)

        // Detect classic (9012: loadMedia calls child loadGif + loadBitmap) vs modern (9108: single merged loader + applyBitmapResult)
        val childThreeArgLoaders = loaderMtd.invokes.filter { inv ->
            inv.declaredClassName == viewer.name &&
                !inv.isConstructor &&
                !Modifier.isStatic(inv.modifiers) &&
                inv.paramCount == 3 &&
                inv.paramTypeNames[0] == imageDisplay.name &&
                inv.paramTypeNames[1] == holderClass?.name &&
                inv.paramTypeNames[2] == "int"
        }

        if (childThreeArgLoaders.size >= 2) {
            // Classic split layout (e.g. 9012 j1 -> l1 / k1)
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_LOAD_MEDIA, loaderMtd)
            val gifSub = childThreeArgLoaders.firstOrNull { inv ->
                inv.invokes.any { it.declaredClassName == "android.graphics.Movie" || it.methodName == "decodeStream" }
            } ?: childThreeArgLoaders[0]
            val bmpSub = childThreeArgLoaders.firstOrNull { it.descriptor != gifSub.descriptor }
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_LOAD_GIF, gifSub)
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_LOAD_BITMAP, bmpSub)
        } else {
            // Merged R8 loader (e.g. 9108 C)
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_LOAD_MEDIA_AND_BITMAP, loaderMtd)
            putMethod(ctx, FxSymbolKeys.MTD_VIEWER_LOAD_MEDIA, loaderMtd)
        }

        // Find applyBitmapResult(ImageDisplay, Holder, ImageResult, boolean) & ImageResult(Bitmap, float, boolean) if present (9108-style)
        val applyMtd = viewer.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramCount == 4 &&
                m.paramTypeNames[0] == imageDisplay.name &&
                m.paramTypeNames[1] == holderClass?.name &&
                m.paramTypeNames[3] == "boolean" &&
                m.returnTypeName == "void"
        }
        if (applyMtd != null) {
            putMethod(ctx, FxSymbolKeys.MTD_HW_APPLY_BITMAP_RESULT, applyMtd)
            val resultCls = applyMtd.paramTypes.getOrNull(2)
            val resultCtor = resultCls?.methods?.firstOrNull { m ->
                m.isConstructor && m.paramTypeNames == listOf("android.graphics.Bitmap", "float", "boolean")
            }
            putMethod(ctx, FxSymbolKeys.CTR_HW_IMAGE_RESULT, resultCtor)
        }

        // loadTokenField: int field read in Runnable callback calling load/apply or clear, OR written in DisplayListener
        val tokenCandidate = applyMtd?.callers
            ?.flatMap { it.usingFields }
            ?.firstOrNull { uf -> uf.usingType.isRead() && uf.field.declaredClassName == viewer.name && uf.field.typeName == "int" }
            ?.field
            ?: viewer.methods.firstOrNull { m ->
                !m.isConstructor &&
                    m.paramCount == 3 &&
                    m.paramTypeNames[0] == imageDisplay.name &&
                    m.paramTypeNames[2] == "int" &&
                    m.usingFields.any { uf -> uf.usingType.isWrite() && uf.field.declaredClassName == viewer.name && uf.field.typeName == "int" }
            }?.usingFields?.firstOrNull { uf ->
                uf.usingType.isWrite() && uf.field.declaredClassName == viewer.name && uf.field.typeName == "int"
            }?.field
        putField(ctx, FxSymbolKeys.FLD_HW_LOAD_TOKEN, tokenCandidate)
    }

    private fun resolveImageDisplayAndListener(
        ctx: ScanContext,
        viewer: ClassData,
        imageDisplay: ClassData
    ) {
        val viewerPkg = viewer.name.substringBeforeLast('.', "nextapp.fx.ui.viewer.image")

        // Listener method with (ImageDisplay, int)
        val listenerClass = ctx.bridge.findClass {
            findFirst = true
            searchPackages(viewerPkg)
            matcher {
                methods {
                    add {
                        paramTypes(imageDisplay.name, "int")
                        returnType = "void"
                    }
                }
            }
        }.firstOrNull { it.name != viewer.name && it.name != imageDisplay.name }

        if (listenerClass != null) {
            putClass(ctx, FxSymbolKeys.CLS_DISPLAY_LISTENER, listenerClass)
            val reqMtd = listenerClass.methods.firstOrNull { m ->
                !m.isConstructor && m.paramTypeNames == listOf(imageDisplay.name, "int")
            }
            putMethod(ctx, FxSymbolKeys.MTD_DISPLAY_LISTENER_REQUEST, reqMtd)

            val commit2 = listenerClass.methods.firstOrNull { m ->
                !m.isConstructor &&
                    m.descriptor != reqMtd?.descriptor &&
                    m.paramTypeNames == listOf(imageDisplay.name, "int") &&
                    m.returnTypeName == "void"
            }
            val commit0 = listenerClass.methods.firstOrNull { m ->
                !m.isConstructor && m.paramCount == 0 && m.returnTypeName == "void"
            }
            putMethod(ctx, FxSymbolKeys.MTD_DISPLAY_LISTENER_COMMIT, commit2 ?: commit0)

            val holderFld = listenerClass.fields.firstOrNull { f ->
                !Modifier.isStatic(f.modifiers) &&
                    (f.typeName == viewer.name || f.typeName == "android.view.KeyEvent\$Callback" || f.typeName == "java.lang.Object")
            }
            putField(ctx, FxSymbolKeys.FLD_DISPLAY_LISTENER_HOLDER, holderFld)

            putField(ctx, FxSymbolKeys.FLD_DISPLAY_LISTENER, imageDisplay.fields.firstOrNull { it.typeName == listenerClass.name })
        }

        // onKeyUp writes/reads siblingIndex (first int) and siblingCount (second int) and calls request/commit
        val onKeyUp = imageDisplay.methods.firstOrNull { it.methodName == "onKeyUp" }
        if (onKeyUp != null) {
            val intReads = onKeyUp.usingFields
                .filter { it.usingType.isRead() && it.field.declaredClassName == imageDisplay.name && it.field.typeName == "int" }
                .map { it.field }
                .distinctBy { it.descriptor }
            if (intReads.isNotEmpty()) putField(ctx, FxSymbolKeys.FLD_DISPLAY_SIBLING_INDEX, intReads[0])
            if (intReads.size >= 2) putField(ctx, FxSymbolKeys.FLD_DISPLAY_SIBLING_COUNT, intReads[1])

            val zeroArgCommit = onKeyUp.invokes.firstOrNull { inv ->
                inv.declaredClassName == imageDisplay.name && !inv.isConstructor && inv.paramCount == 0 && inv.returnTypeName == "void"
            }
            putMethod(ctx, FxSymbolKeys.MTD_DISPLAY_COMMIT, zeroArgCommit)
        }
    }

    private fun resolveRemoteItemClasses(ctx: ScanContext) {
        val fileItem = ctx.fileItemClass ?: return
        val openStream = ctx.methods[FxSymbolKeys.MTD_OPEN_INPUT_STREAM]
            ?.substringAfter("->")?.substringBefore('(')
            ?: return

        val localFileNodeName = ctx.classes[FxSymbolKeys.CLS_FILE_FILE_NODE]
            ?.let { DexSymbolCache.descriptorToJavaClassName(it) }
        val localNodeBaseName = ctx.classes[FxSymbolKeys.CLS_FILE_NODE_BASE]
            ?.let { DexSymbolCache.descriptorToJavaClassName(it) }

        val remoteClasses = ctx.bridge.findClass {
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                interfaces { add { className = fileItem.name } }
                methods {
                    add {
                        name = openStream
                        paramTypes("android.content.Context")
                        returnType = "java.io.InputStream"
                    }
                }
            }
        }
            .filter { cls ->
                val name = cls.name
                !name.startsWith("nextapp.fx.dirimpl.file.") &&
                    name != localFileNodeName &&
                    cls.superClass?.name != localNodeBaseName
            }
            .map { it.name }
            .distinct()
            .take(24)

        if (remoteClasses.isNotEmpty()) {
            ctx.stringLists[FxSymbolKeys.LIST_REMOTE_ITEM_CLASSES] = remoteClasses
        }
    }

    // -------------------------------------------------------------------------
    // Subsystem 4: HardwareRender (TouchImageDisplay, ImageDisplay, EnterTransition, LocalPath)
    // -------------------------------------------------------------------------
    private fun scanHardwareRenderAndGif(ctx: ScanContext) {
        val touch = ctx.bridge.getClassData("nextapp.maui.ui.imageview.TouchImageDisplay")
        putClass(ctx, FxSymbolKeys.CLS_TOUCH_DISPLAY, touch)

        if (touch != null) {
            val setImgBmp = touch.methods.firstOrNull { it.methodName == "setImageBitmap" && it.paramCount == 1 }
            val setImgMov = touch.methods.firstOrNull { it.methodName == "setImageMovie" && it.paramCount == 1 }
            val setImgNull = touch.methods.firstOrNull { it.methodName == "setImageNull" && it.paramCount == 0 }
            val setPrescale = touch.methods.firstOrNull { it.methodName == "setPrescale" && it.paramCount == 1 }
            val setTouch = touch.methods.firstOrNull { it.methodName == "setTouchEnabled" && it.paramCount == 1 }
            val zoomFit = touch.methods.firstOrNull { it.methodName == "zoomFit" && it.paramCount == 0 }
            val onDraw = touch.methods.firstOrNull { it.methodName == "onDraw" && it.paramCount == 1 }
            val onDetached = touch.methods.firstOrNull { it.methodName == "onDetachedFromWindow" && it.paramCount == 0 }

            putMethod(ctx, FxSymbolKeys.MTD_HW_SET_IMAGE_BITMAP, setImgBmp)
            putMethod(ctx, FxSymbolKeys.MTD_HW_SET_IMAGE_MOVIE, setImgMov)
            putMethod(ctx, FxSymbolKeys.MTD_HW_SET_IMAGE_NULL, setImgNull)
            putMethod(ctx, FxSymbolKeys.MTD_HW_SET_PRESCALE, setPrescale)
            putMethod(ctx, FxSymbolKeys.MTD_HW_SET_TOUCH_ENABLED, setTouch)
            putMethod(ctx, FxSymbolKeys.MTD_HW_ZOOM_FIT, zoomFit)
            putMethod(ctx, FxSymbolKeys.MTD_HW_ON_DRAW, onDraw)
            putMethod(ctx, FxSymbolKeys.MTD_HW_ON_DETACHED, onDetached)

            putField(ctx, FxSymbolKeys.FLD_HW_BITMAP, touch.fields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.graphics.Bitmap" })
            putField(ctx, FxSymbolKeys.FLD_HW_MOVIE, touch.fields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.graphics.Movie" })
            putField(ctx, FxSymbolKeys.FLD_HW_MOVIE_THREAD, touch.fields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "java.lang.Thread" })
            putField(ctx, FxSymbolKeys.FLD_HW_MATRIX, touch.fields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.graphics.Matrix" })
            putField(ctx, FxSymbolKeys.FLD_HW_CROP_RECT, touch.fields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.graphics.Rect" })

            // Paint used in onDraw for canvas.drawBitmap(..., paint)
            val drawPaint = onDraw?.usingFields
                ?.lastOrNull { uf -> uf.usingType.isRead() && uf.field.declaredClassName == touch.name && uf.field.typeName == "android.graphics.Paint" }
                ?.field
                ?: onDraw?.usingFields?.firstOrNull { uf -> uf.field.declaredClassName == touch.name && uf.field.typeName == "android.graphics.Paint" }?.field
            putField(ctx, FxSymbolKeys.FLD_HW_PAINT, drawPaint)

            // cropArField written by public setCropAR(float)
            val setCropAr = touch.methods.firstOrNull { it.methodName == "setCropAR" && it.paramCount == 1 }
            putField(ctx, FxSymbolKeys.FLD_HW_CROP_AR, setCropAr?.usingFields?.firstOrNull { it.usingType.isWrite() && it.field.typeName == "float" }?.field)

            // moviePlayingField: boolean cleared in setImageNull (or helper called by setImageNull)
            val moviePlaying = findMoviePlayingField(touch, setImgNull)
            putField(ctx, FxSymbolKeys.FLD_HW_MOVIE_PLAYING, moviePlaying)

            // needsZoomFitField: boolean cleared in onLayout & set true in setImageMovie
            val onLayout = touch.methods.firstOrNull { it.methodName == "onLayout" }
            val needsZoomFit = onLayout?.usingFields
                ?.firstOrNull { uf ->
                    uf.usingType.isWrite() &&
                        uf.field.declaredClassName == touch.name &&
                        uf.field.typeName == "boolean"
                }?.field
            putField(ctx, FxSymbolKeys.FLD_HW_NEEDS_ZOOM_FIT, needsZoomFit)

            // updateBoundsMethod: (int, int, boolean) -> void
            val updateBounds = touch.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramTypeNames == listOf("int", "int", "boolean") &&
                    m.returnTypeName == "void"
            }
            putMethod(ctx, FxSymbolKeys.MTD_HW_UPDATE_BOUNDS, updateBounds)

            // Dimension class from getSize() or updateBounds writes
            val dimCls = touch.methods.firstOrNull { it.methodName == "getSize" && it.paramCount == 0 }?.returnType
                ?: updateBounds?.usingFields?.firstOrNull { it.usingType.isWrite() && !it.field.typeName.startsWith("android.") && it.field.typeName != "float" }?.field?.type
            ctx.dimClass = dimCls

            if (dimCls != null) {
                val displaySizeFld = updateBounds?.usingFields
                    ?.filter { it.usingType.isWrite() && it.field.declaredClassName == touch.name && it.field.typeName == dimCls.name }
                    ?.lastOrNull()
                    ?.field
                putField(ctx, FxSymbolKeys.FLD_HW_DISPLAY_SIZE, displaySizeFld)

                val dimCtor = dimCls.methods.firstOrNull { m ->
                    m.isConstructor && m.paramTypeNames == listOf("int", "int")
                }
                val writtenInts = dimCtor?.usingFields
                    ?.filter { it.usingType.isWrite() && it.field.typeName == "int" }
                    ?.map { it.field }
                    ?.distinctBy { it.descriptor }
                    .orEmpty()
                if (writtenInts.size >= 2) {
                    putField(ctx, FxSymbolKeys.FLD_DIM_WIDTH, writtenInts[0])
                    putField(ctx, FxSymbolKeys.FLD_DIM_HEIGHT, writtenInts[1])
                } else {
                    val ints = dimCls.fields.filter { !Modifier.isStatic(it.modifiers) && it.typeName == "int" }
                    if (ints.size >= 2) {
                        putField(ctx, FxSymbolKeys.FLD_DIM_WIDTH, ints[0])
                        putField(ctx, FxSymbolKeys.FLD_DIM_HEIGHT, ints[1])
                    }
                }
            }

            // enforceLayerOnBitmapMethod (9012 g(Bitmap) using 2160) vs inline in setImageBitmap (9108)
            val enforceMtd = touch.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.methodName != "setImageBitmap" &&
                    m.paramTypeNames == listOf("android.graphics.Bitmap") &&
                    m.returnTypeName == "void"
            }
            putMethod(ctx, FxSymbolKeys.MTD_HW_ENFORCE_LAYER, enforceMtd)

            val layerHost = enforceMtd ?: setImgBmp
            val swLayerFld = layerHost?.usingFields
                ?.filter { uf ->
                    uf.usingType.isWrite() &&
                        uf.field.declaredClassName == touch.name &&
                        uf.field.typeName == "boolean" &&
                        uf.field.descriptor != moviePlaying?.descriptor &&
                        uf.field.descriptor != needsZoomFit?.descriptor
                }
                ?.map { it.field }
                ?.lastOrNull()
            putField(ctx, FxSymbolKeys.FLD_HW_SOFTWARE_LAYER, swLayerFld)
        }

        // ImageDisplay methods & fields
        val display = ctx.imageDisplayClass
        if (display != null) {
            putField(ctx, FxSymbolKeys.FLD_HW_DISPLAY_BITMAP, display.fields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.graphics.Bitmap" })
            val displayMovieFld = display.fields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.graphics.Movie" }
            putField(ctx, FxSymbolKeys.FLD_HW_DISPLAY_MOVIE, displayMovieFld)

            val deliverBmp = display.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramTypeNames == listOf("android.graphics.Bitmap", "boolean") &&
                    m.returnTypeName == "void"
            }
            val deliverMov = display.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramTypeNames == listOf("android.graphics.Movie") &&
                    m.returnTypeName == "void"
            }
            val clearDisplay = display.methods.firstOrNull { m ->
                !m.isConstructor &&
                    !Modifier.isStatic(m.modifiers) &&
                    m.paramCount == 0 &&
                    m.returnTypeName == "void" &&
                    m.invokes.any { inv -> inv.methodName == "setImageNull" }
            }
            putMethod(ctx, FxSymbolKeys.MTD_HW_DELIVER_BITMAP, deliverBmp)
            putMethod(ctx, FxSymbolKeys.MTD_HW_DELIVER_MOVIE, deliverMov)
            putMethod(ctx, FxSymbolKeys.MTD_HW_CLEAR_DISPLAY, clearDisplay)

            // Boolean flags on ImageDisplay: disposed read in clearDisplay; Movie delivery writes ONLY fullLoaded
            val dispDisposed = clearDisplay?.usingFields?.firstOrNull { uf ->
                uf.usingType.isRead() && uf.field.declaredClassName == display.name && uf.field.typeName == "boolean"
            }?.field
            putField(ctx, FxSymbolKeys.FLD_HW_DISPLAY_DISPOSED, dispDisposed)

            val clearedWrites = clearDisplay?.usingFields
                ?.filter { uf ->
                    uf.usingType.isWrite() &&
                        uf.field.declaredClassName == display.name &&
                        uf.field.typeName == "boolean" &&
                        uf.field.descriptor != dispDisposed?.descriptor
                }
                ?.map { it.field }
                ?.distinctBy { it.descriptor }
                .orEmpty()

            val movieDeliveryMtd = deliverMov
                ?: displayMovieFld?.writers?.firstOrNull { m ->
                    m.invokes.any { inv -> inv.methodName == "setImageMovie" }
                }
            val fullLoadedFld = movieDeliveryMtd?.usingFields
                ?.firstOrNull { uf ->
                    uf.usingType.isWrite() &&
                        uf.field.declaredClassName == display.name &&
                        uf.field.typeName == "boolean" &&
                        uf.field.descriptor != dispDisposed?.descriptor
                }?.field
                ?: clearedWrites.getOrNull(1)
            val previewLoadedFld = clearedWrites.firstOrNull { it.descriptor != fullLoadedFld?.descriptor }
                ?: deliverBmp?.usingFields
                    ?.filter { uf ->
                        uf.usingType.isWrite() &&
                            uf.field.declaredClassName == display.name &&
                            uf.field.typeName == "boolean" &&
                            uf.field.descriptor != dispDisposed?.descriptor &&
                            uf.field.descriptor != fullLoadedFld?.descriptor
                    }
                    ?.map { it.field }
                    ?.firstOrNull()

            putField(ctx, FxSymbolKeys.FLD_HW_DISPLAY_FULL_LOADED, fullLoadedFld)
            putField(ctx, FxSymbolKeys.FLD_HW_DISPLAY_PREVIEW_LOADED, previewLoadedFld)
        }

        // EnterTransition object inside ImageViewerActivity
        val viewer = ctx.viewerClass
        if (viewer != null) {
            val transitionCls = viewer.fields
                .asSequence()
                .mapNotNull { f -> f.type?.let { t -> f to t } }
                .firstOrNull { (_, t) ->
                    val pkg = viewer.name.substringBeforeLast('.', "")
                    t.name.startsWith("$pkg.") &&
                        t.fields.count { it.typeName == "android.view.View" } >= 3 &&
                        t.fields.count { it.typeName == "boolean" } >= 2
                }
            if (transitionCls != null) {
                val (transField, tCls) = transitionCls
                putField(ctx, FxSymbolKeys.FLD_HW_ENTER_TRANSITION, transField)
                val allViews = tCls.fields.filter { !Modifier.isStatic(it.modifiers) && it.typeName == "android.view.View" }
                // Exclude container View field ('a') whose readers measure getWidth()/getHeight(), keeping [bgView(c), topBar(d), bottomBar(e)]
                val nonContainerViews = allViews.filter { vf ->
                    vf.readers.none { rm ->
                        rm.invokes.any { inv -> inv.methodName == "getWidth" || inv.methodName == "getHeight" }
                    }
                }
                val views = if (nonContainerViews.size >= 3) {
                    nonContainerViews.takeLast(3)
                } else {
                    allViews.takeLast(3)
                }
                if (views.size >= 3) {
                    putField(ctx, FxSymbolKeys.FLD_HW_TRANSITION_BG_VIEW, views[0])
                    putField(ctx, FxSymbolKeys.FLD_HW_TRANSITION_TOP_BAR, views[1])
                    putField(ctx, FxSymbolKeys.FLD_HW_TRANSITION_BOTTOM_BAR, views[2])
                }
                // Check which boolean is checked first vs written in AnimatorListener/run
                val bools = tCls.fields.filter { !Modifier.isStatic(it.modifiers) && it.typeName == "boolean" }
                if (bools.size >= 2) {
                    // running flag ('h') is set true before starting / false on cancel; done ('g') starts false & becomes true on finish
                    val setFirst = tCls.methods.firstOrNull { !it.isConstructor && it.paramCount == 0 && it.usingFields.any { uf -> uf.usingType.isWrite() && uf.field.typeName == "boolean" } }
                    val runnerBool = setFirst?.usingFields?.lastOrNull { uf -> uf.usingType.isWrite() && uf.field.typeName == "boolean" }?.field
                    val doneBool = bools.firstOrNull { it.descriptor != runnerBool?.descriptor } ?: bools[0]
                    val runBool = runnerBool ?: bools[1]
                    putField(ctx, FxSymbolKeys.FLD_HW_TRANSITION_DONE, doneBool)
                    putField(ctx, FxSymbolKeys.FLD_HW_TRANSITION_RUNNING, runBool)
                }
            }
        }

        // LocalPath interface & provider (`ue.a0`/`ue.b0` or `kh.c0`/`kh.d0`) from FileNodeBase interfaces
        val fileNodeBase = ctx.classes[FxSymbolKeys.CLS_FILE_NODE_BASE]?.let { ctx.bridge.getClassData(it.removePrefix("L").removeSuffix(";").replace('/', '.')) }
        if (fileNodeBase != null) {
            val ifaces = fileNodeBase.interfaces
            val localPathProviderIface = ifaces.firstOrNull { iface ->
                val superIfaces = iface.interfaces.map { it.name }.toSet()
                val hasZeroArgString = iface.methods.any { m ->
                    !m.isConstructor && m.paramCount == 0 && m.returnTypeName == "java.lang.String"
                }
                val hasZeroArgBool = iface.methods.any { m ->
                    !m.isConstructor && m.paramCount == 0 && m.returnTypeName == "boolean"
                }
                hasZeroArgString && hasZeroArgBool && superIfaces.contains(ctx.fileItemBaseClass?.name)
            } ?: ifaces.firstOrNull { iface ->
                iface.methods.any { m -> !m.isConstructor && m.paramCount == 0 && m.returnTypeName == "java.lang.String" } &&
                    iface.methods.any { m -> !m.isConstructor && m.paramCount == 0 && m.returnTypeName == "boolean" }
            }
            val localMarkerIface = ifaces.firstOrNull { iface ->
                iface.name != localPathProviderIface?.name &&
                    iface.methods.isEmpty() &&
                    iface.interfaces.containsAll(listOfNotNull(ctx.fileItemBaseClass))
            } ?: ifaces.firstOrNull { iface ->
                iface.name != localPathProviderIface?.name && iface.methodCount == 0
            }

            putClass(ctx, FxSymbolKeys.CLS_LOCAL_PATH_CLASS, localMarkerIface ?: localPathProviderIface)
            putMethod(
                ctx,
                FxSymbolKeys.MTD_HW_LOCAL_PATH,
                localPathProviderIface?.methods?.firstOrNull { m ->
                    !m.isConstructor && m.paramCount == 0 && m.returnTypeName == "java.lang.String"
                }
            )
        }
    }

    private fun findMoviePlayingField(touch: ClassData, setImgNull: MethodData?): FieldData? {
        setImgNull ?: return null
        val direct = setImgNull.usingFields.firstOrNull { uf ->
            uf.usingType.isWrite() && uf.field.declaredClassName == touch.name && uf.field.typeName == "boolean"
        }?.field
        if (direct != null) return direct
        for (inv in setImgNull.invokes) {
            if (inv.declaredClassName != touch.name) continue
            val inner = inv.usingFields.firstOrNull { uf ->
                uf.usingType.isWrite() && uf.field.declaredClassName == touch.name && uf.field.typeName == "boolean"
            }?.field
            if (inner != null) return inner
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Subsystem 5: Native Settings & Live FX Theme Palette
    // -------------------------------------------------------------------------
    private fun scanSettingsAndTheme(ctx: ScanContext) {
        val mainPref = ctx.bridge.getClassData("nextapp.fx.ui.fxsystem.MainPrefActivity")
        if (mainPref != null) {
            putMethod(ctx, FxSymbolKeys.MTD_MAIN_PREF_ON_CREATE, mainPref.methods.firstOrNull { it.methodName == "onCreate" })
            putMethod(ctx, FxSymbolKeys.MTD_MAIN_PREF_ON_RESUME, mainPref.methods.firstOrNull { it.methodName == "onResume" })
        }

        val labelPref = ctx.bridge.getClassData("nextapp.maui.ui.preference.LabelPreference")
        putMethod(ctx, FxSymbolKeys.CTR_LABEL_PREFERENCE, labelPref?.methods?.firstOrNull { m ->
            m.isConstructor && m.paramTypeNames == listOf("android.content.Context")
        })

        // FxDialog class: has getDefaultContentLayout + isBackgroundLight
        val fxDialog = ctx.bridge.findClass {
            findFirst = true
            searchPackages("nextapp.fx.ui.widget")
            matcher {
                methods {
                    add { name = "getDefaultContentLayout"; paramCount = 0 }
                    add { name = "isBackgroundLight"; paramCount = 0 }
                }
            }
        }.firstOrNull()

        if (fxDialog != null) {
            val dlgCtor = fxDialog.methods.firstOrNull { m ->
                m.isConstructor && m.paramCount == 2 && m.paramTypeNames[0] == "android.content.Context" &&
                    m.paramTypes.getOrNull(1)?.superClass?.name == "java.lang.Enum"
            }
            putMethod(ctx, FxSymbolKeys.CTR_FX_DIALOG, dlgCtor)

            val styleEnum = dlgCtor?.paramTypes?.getOrNull(1)
            putClass(ctx, FxSymbolKeys.CLS_FX_DIALOG_STYLE_WITH_CLOSE_ENUM, styleEnum)

            // In callers of FxDialog(ctx, style), the style enum constant with close button is referenced as static field;
            // enum constants at ordinal 1 ('Z' in 9012 k$f and 9108 m) match index 1 static enum field.
            val enumStaticFields = styleEnum?.fields?.filter { Modifier.isStatic(it.modifiers) && it.typeName == styleEnum.name }.orEmpty()
            val secondConst = enumStaticFields.getOrNull(1) ?: enumStaticFields.firstOrNull()
            putField(ctx, FxSymbolKeys.CLS_FX_DIALOG_STYLE_WITH_CLOSE_FIELD, secondConst)

            putMethod(ctx, FxSymbolKeys.MTD_FX_DIALOG_SET_HEADER, fxDialog.methods.firstOrNull { it.methodName == "setHeader" && it.paramCount == 1 })
            putMethod(ctx, FxSymbolKeys.MTD_FX_DIALOG_SET_DESCRIPTION, fxDialog.methods.firstOrNull { it.methodName == "setDescription" && it.paramCount == 1 })
            putMethod(ctx, FxSymbolKeys.MTD_FX_DIALOG_GET_DEFAULT_LAYOUT, fxDialog.methods.firstOrNull { it.methodName == "getDefaultContentLayout" && it.paramCount == 0 })
            putMethod(ctx, FxSymbolKeys.MTD_FX_DIALOG_ADD_LABELED_VIEW, fxDialog.methods.firstOrNull { it.methodName == "addLabeledView" && it.paramCount == 2 })
            val isBgLightMtd = fxDialog.methods.firstOrNull { it.methodName == "isBackgroundLight" && it.paramCount == 0 }
            putMethod(ctx, FxSymbolKeys.MTD_FX_DIALOG_IS_BG_LIGHT, isBgLightMtd)
            putMethod(ctx, FxSymbolKeys.MTD_FX_DIALOG_DISMISS, fxDialog.methods.firstOrNull { it.methodName == "dismiss" && it.paramCount == 0 })
            putMethod(ctx, FxSymbolKeys.MTD_FX_DIALOG_SHOW, fxDialog.superClass?.methods?.firstOrNull { it.methodName == "show" && it.paramCount == 0 })

            val uiFld = fxDialog.fields.firstOrNull { it.fieldName == "ui" }
                ?: isBgLightMtd?.usingFields?.firstOrNull { it.usingType.isRead() && it.field.declaredClassName == fxDialog.name }?.field
            putField(ctx, FxSymbolKeys.FLD_FX_DIALOG_UI, uiFld)

            val uiTheme = uiFld?.type ?: findUiThemeClassByColors(ctx)
            ctx.uiThemeClass = uiTheme
            if (uiTheme != null) {
                populateUiThemeBindings(ctx, uiTheme)
            }
        } else {
            findUiThemeClassByColors(ctx)?.let { populateUiThemeBindings(ctx, it) }
        }
    }

    private fun findUiThemeClassByColors(ctx: ScanContext): ClassData? {
        return ctx.bridge.findClass {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                methods {
                    add {
                        name = "<init>"
                        usingNumbers(-553648128, 1862270976)
                    }
                }
            }
        }.firstOrNull()
    }

    private fun populateUiThemeBindings(
        ctx: ScanContext,
        uiTheme: ClassData
    ) {
        val getForCtx = uiTheme.methods.firstOrNull { m ->
            !m.isConstructor &&
                Modifier.isStatic(m.modifiers) &&
                m.paramTypeNames == listOf("android.content.Context") &&
                m.returnTypeName == uiTheme.name
        }
        putMethod(ctx, FxSymbolKeys.MTD_UI_GET_FOR_CONTEXT, getForCtx)

        // Locate ColorEnum class via boxBackground / windowBackground enum constructor literals
        val colorEnumClass = ctx.bridge.findMethod {
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                usingStrings(listOf("boxBackground", "boxPressedBackground", "windowBackground"))
            }
        }.firstOrNull()?.declaredClass

        val enumFieldsByName = colorEnumClass?.let { enumCls ->
            ctx.dalvik?.findStaticEnumFieldsByString("${enumCls.descriptor}-><clinit>()V")
        }.orEmpty()

        val windowBgEnumFld = colorEnumClass?.fields?.firstOrNull { f ->
            f.descriptor == enumFieldsByName["windowBackground"] || f.fieldName == "windowBackground"
        }
        val boxBgEnumFld = colorEnumClass?.fields?.firstOrNull { f ->
            f.descriptor == enumFieldsByName["boxBackground"] || f.fieldName == "boxBackground"
        }
        val boxPressedEnumFld = colorEnumClass?.fields?.firstOrNull { f ->
            f.descriptor == enumFieldsByName["boxPressedBackground"] || f.fieldName == "boxPressedBackground"
        }

        // Window background 0-arg int method: matched via windowBackground enum slot, or reading uiIsWindowLight boolean + colorEnum
        val windowBgMtd = uiTheme.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramCount == 0 &&
                m.returnTypeName == "int" &&
                (
                    (windowBgEnumFld != null && m.usingFields.any { uf -> uf.field.descriptor == windowBgEnumFld.descriptor }) ||
                        m.usingStrings.any { it == "windowBackground" }
                    )
        } ?: uiTheme.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramCount == 0 &&
                m.returnTypeName == "int" &&
                m.usingFields.any { uf -> uf.field.declaredClassName == colorEnumClass?.name } &&
                m.usingFields.any { uf -> uf.usingType.isRead() && uf.field.declaredClassName == uiTheme.name && uf.field.typeName == "boolean" } &&
                m.invokes.none { inv -> inv.usingStrings.any { it == "trimColorAccent4" || it == "trimColorBase4" } }
        }
        putMethod(ctx, FxSymbolKeys.MTD_UI_WINDOW_BG, windowBgMtd)

        // isWindowLightField is directly read inside windowBgMethod (ef.g.w / xc.f.P)
        val isWindowLightFld = windowBgMtd?.usingFields
            ?.firstOrNull { uf -> uf.usingType.isRead() && uf.field.declaredClassName == uiTheme.name && uf.field.typeName == "boolean" }
            ?.field
            ?: uiTheme.fields.firstOrNull { f -> !Modifier.isStatic(f.modifiers) && f.fieldName == "j" && f.typeName == "boolean" }
        putField(ctx, FxSymbolKeys.FLD_UI_IS_WINDOW_LIGHT, isWindowLightFld)

        // Resolve textPrimary/textSecondary and boxBg/boxPressedBg in constructor referencing -553648128 & 1862270976
        val colorInitCtor = uiTheme.methods.firstOrNull { m ->
            m.isConstructor && m.usingNumbers.any { it.intValue() == -553648128 } && m.usingNumbers.any { it.intValue() == 1862270976 }
        }
        if (colorInitCtor != null) {
            val intWrites = colorInitCtor.usingFields
                .filter { uf -> uf.usingType.isWrite() && uf.field.declaredClassName == uiTheme.name && uf.field.typeName == "int" }
                .map { it.field }
                .distinctBy { it.descriptor }

            val dalvikMapping = ctx.dalvik?.findUiThemeFieldMapping(
                ctorMethodDesc = colorInitCtor.descriptor,
                boxBgEnumDesc = boxBgEnumFld?.descriptor,
                boxPressedEnumDesc = boxPressedEnumFld?.descriptor
            )

            // Match constant readers on uiTheme without restricting paramCount == 0 (e.g. xc.f.K(e)/J(e) & ef.g.r(f)/D(f,String,String))
            val primaryByConst = uiTheme.fields.firstOrNull { f ->
                f.typeName == "int" && !Modifier.isStatic(f.modifiers) &&
                    f.readers.any { r -> !r.isConstructor && r.usingNumbers.any { it.intValue() == -553648128 } }
            }
            val secondaryByConst = uiTheme.fields.firstOrNull { f ->
                f.typeName == "int" && !Modifier.isStatic(f.modifiers) &&
                    f.readers.any { r -> !r.isConstructor && r.usingNumbers.any { it.intValue() == 1862270976 } }
            }

            fun resolveFieldByDescOrFallback(desc: String?, fallback: FieldData?): FieldData? {
                return desc?.let { d -> uiTheme.fields.firstOrNull { it.descriptor == d } } ?: fallback
            }

            // Positional intWrites[0..3] in ctor are [boxBg, boxPressedBg, textPrimary, textSecondary] (followed by pixel metrics)
            val boxBgFld = resolveFieldByDescOrFallback(dalvikMapping?.boxBgFieldDesc, intWrites.getOrNull(0))
            val boxPressedFld = resolveFieldByDescOrFallback(dalvikMapping?.boxPressedBgFieldDesc, intWrites.getOrNull(1))
            val primaryFld = resolveFieldByDescOrFallback(dalvikMapping?.textPrimaryFieldDesc, primaryByConst ?: intWrites.getOrNull(2))
            val secondaryFld = resolveFieldByDescOrFallback(dalvikMapping?.textSecondaryFieldDesc, secondaryByConst ?: intWrites.getOrNull(3))

            putField(ctx, FxSymbolKeys.FLD_UI_BOX_BG, boxBgFld)
            putField(ctx, FxSymbolKeys.FLD_UI_BOX_PRESSED_BG, boxPressedFld)
            putField(ctx, FxSymbolKeys.FLD_UI_TEXT_PRIMARY, primaryFld)
            putField(ctx, FxSymbolKeys.FLD_UI_TEXT_SECONDARY, secondaryFld)
        }

        val trimAccentMtd = findThemeTrimMethod(uiTheme, "trimColorAccent4")
        val trimBaseMtd = findThemeTrimMethod(uiTheme, "trimColorBase4")
        putMethod(ctx, FxSymbolKeys.MTD_UI_TRIM_ACCENT, trimAccentMtd)
        putMethod(ctx, FxSymbolKeys.MTD_UI_TRIM_BASE, trimBaseMtd)
    }

    private fun findThemeTrimMethod(uiTheme: ClassData, key: String): MethodData? {
        return uiTheme.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramCount == 0 &&
                m.returnTypeName == "int" &&
                (m.usingStrings.any { it == key } || m.invokes.any { inv -> inv.usingStrings.any { it == key } })
        }
    }

    // -------------------------------------------------------------------------
    // Subsystem 6: Home Overflow Menu
    // -------------------------------------------------------------------------
    private fun scanHomeMenu(ctx: ScanContext) {
        val homeView = ctx.bridge.getClassData("nextapp.fx.ui.homecontent.HomeContentView")
        ctx.homeContentViewClass = homeView
        putClass(ctx, FxSymbolKeys.CLS_HOME_CONTENT_VIEW, homeView)

        if (homeView != null) {
            putField(ctx, FxSymbolKeys.FLD_HOME_VIEW_ACTIVITY, homeView.fields.firstOrNull { it.fieldName == "activity" })
            putField(ctx, FxSymbolKeys.FLD_HOME_VIEW_RES, homeView.fields.firstOrNull { it.fieldName == "res" }
                ?: homeView.fields.firstOrNull { it.typeName == "android.content.res.Resources" })
        }

        val populateMtd = ctx.bridge.findMethod {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                usingStrings("action_filesystems", "action_home_customize", "action_theme")
            }
        }.firstOrNull() ?: return

        val contribCls = populateMtd.declaredClass ?: return
        putClass(ctx, FxSymbolKeys.CLS_MENU_CONTRIB, contribCls)
        putMethod(ctx, FxSymbolKeys.MTD_MENU_CONTRIB_POPULATE, populateMtd)

        // Merged menu contributor has int caseId field; dedicated inner class (9012) does not
        val caseFld = contribCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "int" }
        putField(ctx, FxSymbolKeys.FLD_MENU_CONTRIB_CASE, caseFld)
        if (caseFld != null) {
            val getMenuContribMtd = homeView?.methods?.firstOrNull { m ->
                !m.isConstructor && !Modifier.isStatic(m.modifiers) && m.methodName == "getMenuContributions" && m.paramCount == 0
            } ?: homeView?.methods?.firstOrNull { m ->
                !m.isConstructor && !Modifier.isStatic(m.modifiers) && m.paramCount == 0 &&
                    m.invokes.any { inv -> inv.isConstructor && inv.declaredClassName == contribCls.name }
            }
            val invokedCtor = getMenuContribMtd?.invokes?.firstOrNull { inv ->
                inv.isConstructor && inv.declaredClassName == contribCls.name
            } ?: contribCls.methods.firstOrNull { m ->
                m.isConstructor && m.paramTypeNames.contains("int")
            }

            var extractedCaseId: Int? = null
            if (invokedCtor != null) {
                val intParamIdx = invokedCtor.paramTypeNames.indexOf("int")
                if (intParamIdx >= 0 && getMenuContribMtd != null) {
                    extractedCaseId = ctx.dalvik?.findIntArgPassedToInvoke(
                        callerMethodDesc = getMenuContribMtd.descriptor,
                        calleeMethodDesc = invokedCtor.descriptor,
                        invokeArgIndex = 1 + intParamIdx
                    )
                }
                if (extractedCaseId == null) {
                    extractedCaseId = ctx.dalvik?.findConstructorParamFieldWrites(invokedCtor.descriptor)
                        ?.intConstToField
                        ?.get(caseFld.descriptor)
                }
            }
            if (extractedCaseId != null) {
                ctx.ints[FxSymbolKeys.INT_MENU_CONTRIB_CASE_ID] = extractedCaseId
            }
        }

        // Owner field referencing HomeContentView or its FX superclass (e.g. nextapp.fx.ui.content.u in 9108)
        val homeAncestors = collectClassHierarchyNames(homeView)
        val ownerFld = populateMtd.usingFields
            .firstOrNull { uf ->
                uf.usingType.isRead() &&
                    uf.field.declaredClassName == contribCls.name &&
                    uf.field.typeName in homeAncestors
            }?.field
            ?: contribCls.fields.firstOrNull { f -> !Modifier.isStatic(f.modifiers) && f.typeName in homeAncestors }
        putField(ctx, FxSymbolKeys.FLD_MENU_CONTRIB_OWNER, ownerFld)

        // bgLight field on contribCls or superclass
        val bgLightFld = populateMtd.usingFields
            .firstOrNull { uf ->
                uf.usingType.isRead() &&
                    uf.field.typeName == "boolean" &&
                    (uf.field.declaredClassName == contribCls.name || uf.field.declaredClassName == contribCls.superClass?.name)
            }?.field
            ?: contribCls.superClass?.fields?.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "boolean" }
        putField(ctx, FxSymbolKeys.FLD_MENU_CONTRIB_BG_LIGHT, bgLightFld)

        // MenuModel & MenuItem & ActionIcons from populateMtd calls
        val menuModelCls = populateMtd.paramTypes.getOrNull(1) ?: populateMtd.paramTypes.getOrNull(0)
        if (menuModelCls != null) {
            val addItemMtd = populateMtd.invokes.firstOrNull { inv ->
                inv.declaredClassName == menuModelCls.name &&
                    !inv.isConstructor &&
                    !Modifier.isStatic(inv.modifiers) &&
                    inv.paramCount == 1 &&
                    inv.returnTypeName == "void"
            } ?: menuModelCls.methods.firstOrNull { m ->
                !m.isConstructor && m.paramCount == 1 && m.returnTypeName == "void"
            }
            putMethod(ctx, FxSymbolKeys.MTD_MENU_MODEL_ADD_ITEM, addItemMtd)
            putMethod(ctx, FxSymbolKeys.MTD_MENU_MODEL_SIZE, menuModelCls.methods.firstOrNull { it.methodName == "size" && it.paramCount == 0 })
            putMethod(ctx, FxSymbolKeys.MTD_MENU_MODEL_ITEM, menuModelCls.methods.firstOrNull { it.methodName == "item" && it.paramCount == 1 })
            putField(ctx, FxSymbolKeys.FLD_MENU_MODEL_ITEMS_LIST, menuModelCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && (it.typeName == "java.util.ArrayList" || it.typeName == "java.util.List") })

            val baseItemInterface = addItemMtd?.paramTypes?.firstOrNull()
            // Find getTitle(): CharSequence on baseItemInterface subinterfaces or Action class
            val actionCtor = populateMtd.invokes.firstOrNull { inv ->
                inv.isConstructor &&
                    inv.paramCount == 3 &&
                    inv.paramTypeNames[0] == "java.lang.CharSequence" &&
                    inv.paramTypeNames[1] == "android.graphics.drawable.Drawable"
            }
            putMethod(ctx, FxSymbolKeys.CTR_MENU_ACTION, actionCtor)
            putClass(ctx, FxSymbolKeys.CLS_MENU_CLICK_LISTENER, actionCtor?.paramTypes?.getOrNull(2))

            val titledMtd = actionCtor?.declaredClass?.interfaces
                ?.flatMap { it.methods }
                ?.firstOrNull { it.methodName == "getTitle" && it.paramCount == 0 }
                ?: ctx.bridge.findMethod {
                    findFirst = true
                    baseItemInterface?.name?.substringBeforeLast('.', "")
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { searchPackages(it) }
                    matcher {
                        name = "getTitle"
                        paramCount = 0
                        returnType = "java.lang.CharSequence"
                    }
                }.firstOrNull()
            putMethod(ctx, FxSymbolKeys.MTD_MENU_ITEM_GET_TITLE, titledMtd)

            // Menu divider: 0-arg constructor class passed to menuModel.addItem
            val dividerCtor = populateMtd.invokes.firstOrNull { inv ->
                inv.isConstructor &&
                    inv.paramCount == 0 &&
                    inv.declaredClassName != actionCtor?.declaredClassName &&
                    (baseItemInterface == null || inv.declaredClass?.interfaces?.any { it.name == baseItemInterface.name } == true)
            }
            val dividerCls = dividerCtor?.declaredClass
                ?: populateMtd.invokes.firstOrNull { inv ->
                    inv.isConstructor &&
                        inv.declaredClassName.startsWith(menuModelCls.name.substringBeforeLast('.', "")) &&
                        inv.declaredClassName != actionCtor?.declaredClassName &&
                        inv.paramCount == 0
                }?.declaredClass
            putClass(ctx, FxSymbolKeys.CLS_MENU_DIVIDER, dividerCls)
            putMethod(ctx, FxSymbolKeys.CTR_MENU_DIVIDER, dividerCtor)
        }

        // ActionIcons factory invoked in populateMtd
        val actionIconsCreate = populateMtd.invokes.firstOrNull { inv ->
            inv.declaredClassName == "nextapp.fx.ui.res.ActionIcons" &&
                Modifier.isStatic(inv.modifiers) &&
                inv.paramCount == 3 &&
                inv.paramTypeNames[0] == "android.content.res.Resources" &&
                inv.paramTypeNames[1] == "java.lang.String"
        }
        putMethod(ctx, FxSymbolKeys.MTD_ACTION_ICONS_CREATE, actionIconsCreate)
    }

    private fun collectClassHierarchyNames(cls: ClassData?): Set<String> {
        val result = linkedSetOf<String>()
        var cur = cls
        while (cur != null && !cur.name.startsWith("android.") && !cur.name.startsWith("java.")) {
            result.add(cur.name)
            cur = cur.superClass
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Subsystem 7: Google Drive OAuth
    // -------------------------------------------------------------------------
    private fun scanGoogleDrive(ctx: ScanContext) {
        val authActivity = ctx.bridge.getClassData("nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity")
        putMethod(ctx, FxSymbolKeys.MTD_GDRIVE_AUTH_ON_CREATE, authActivity?.methods?.firstOrNull { it.methodName == "onCreate" })

        // Classic 9012 getClientId: static (Context) -> String returning default client id
        val getClientId9012 = ctx.bridge.findMethod {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                modifiers = Modifier.STATIC
                paramTypes("android.content.Context")
                returnType = "java.lang.String"
                usingStrings(GDRIVE_DEFAULT_CLIENT_ID)
            }
        }.firstOrNull()
        putMethod(ctx, FxSymbolKeys.MTD_GDRIVE_GET_CLIENT_ID_9012, getClientId9012)

        // Token credentials holder invoked next to OAuth token URL
        val tokenHolders = ctx.bridge.findMethod {
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                usingStrings(GDRIVE_TOKEN_URL)
            }
        }
        for (caller in tokenHolders) {
            val ctor9012 = caller.invokes.firstOrNull { inv ->
                inv.isConstructor && inv.paramTypeNames == listOf("java.lang.String", "java.lang.String", "java.lang.String")
            }
            if (ctor9012 != null) {
                putMethod(ctx, FxSymbolKeys.CTR_GDRIVE_TOKEN_CREDS_9012, ctor9012)
            }

            val ctor9108 = caller.invokes.firstOrNull { inv ->
                inv.isConstructor && inv.paramTypeNames == listOf("int", "java.lang.String", "java.lang.String", "java.lang.String")
            }
            if (ctor9108 != null) {
                putMethod(ctx, FxSymbolKeys.CTR_GDRIVE_TOKEN_CREDS_9108, ctor9108)
                val strWrites = ctor9108.usingFields
                    .filter { uf -> uf.usingType.isWrite() && uf.field.declaredClassName == ctor9108.declaredClassName && uf.field.typeName == "java.lang.String" }
                    .map { it.field }
                    .distinctBy { it.descriptor }
                if (strWrites.isNotEmpty()) putField(ctx, FxSymbolKeys.FLD_GDRIVE_TOKEN_CREDS_CLIENT_ID_9108, strWrites[0])
                if (strWrites.size >= 2) putField(ctx, FxSymbolKeys.FLD_GDRIVE_TOKEN_CREDS_SECRET_9108, strWrites[1])
            }
        }

        // Also check direct AppAuth token creds constructor if not invoked in same method as URL string
        if (ctx.methods[FxSymbolKeys.CTR_GDRIVE_TOKEN_CREDS_9108] == null) {
            val directCtor = ctx.bridge.findMethod {
                findFirst = true
                excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
                matcher {
                    usingStrings(GDRIVE_DEFAULT_CLIENT_ID, GDRIVE_TOKEN_URL)
                }
            }.firstOrNull()?.invokes?.firstOrNull { inv ->
                inv.isConstructor && inv.paramTypeNames == listOf("int", "java.lang.String", "java.lang.String", "java.lang.String")
            }
            if (directCtor != null) {
                putMethod(ctx, FxSymbolKeys.CTR_GDRIVE_TOKEN_CREDS_9108, directCtor)
                val strWrites = directCtor.usingFields
                    .filter { uf -> uf.usingType.isWrite() && uf.field.declaredClassName == directCtor.declaredClassName && uf.field.typeName == "java.lang.String" }
                    .map { it.field }
                    .distinctBy { it.descriptor }
                if (strWrites.isNotEmpty()) putField(ctx, FxSymbolKeys.FLD_GDRIVE_TOKEN_CREDS_CLIENT_ID_9108, strWrites[0])
                if (strWrites.size >= 2) putField(ctx, FxSymbolKeys.FLD_GDRIVE_TOKEN_CREDS_SECRET_9108, strWrites[1])
            }
        }

        // 9108 AppAuth AuthRequest.Builder wrapper constructor calling GDRIVE_DEFAULT_CLIENT_ID: (ServiceConfig, Uri) -> void
        val authReqCtor = ctx.bridge.findMethod {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                name = "<init>"
                paramCount = 2
                usingStrings(GDRIVE_DEFAULT_CLIENT_ID)
            }
        }.firstOrNull { m -> m.paramTypeNames[1] == "android.net.Uri" }
        if (authReqCtor != null) {
            putMethod(ctx, FxSymbolKeys.CTR_GDRIVE_AUTH_REQ_9108, authReqCtor)
            val clientIdFld = authReqCtor.usingFields
                .firstOrNull { uf -> uf.usingType.isWrite() && uf.field.declaredClassName == authReqCtor.declaredClassName && uf.field.typeName == "java.lang.String" }
                ?.field
            putField(ctx, FxSymbolKeys.FLD_GDRIVE_AUTH_REQ_CLIENT_ID_9108, clientIdFld)
        }
    }

    // -------------------------------------------------------------------------
    // Subsystem 8: Remote Thumbnails & Generator
    // -------------------------------------------------------------------------
    private fun scanRemoteThumb(ctx: ScanContext) {
        val loadThumb = ctx.bridge.findMethod {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                modifiers = Modifier.STATIC
                paramCount = 6
                usingStrings("Network", "Remote", "image/")
                usingNumbers(4194304L)
            }
        }.firstOrNull()

        if (loadThumb != null) {
            putMethod(ctx, FxSymbolKeys.MTD_LOAD_THUMBNAIL, loadThumb)
            val callbackCls = loadThumb.paramTypes.getOrNull(3)
            val cacheCls = loadThumb.paramTypes.getOrNull(4)
            putClass(ctx, FxSymbolKeys.CLS_THUMB_CALLBACK, callbackCls)
            putMethod(ctx, FxSymbolKeys.MTD_THUMB_CB_ENQUEUE, callbackCls?.methods?.singleOrNull { !it.isConstructor && it.paramCount == 1 })

            // Settings instance
            val settingsMtd = loadThumb.invokes.firstOrNull { inv ->
                Modifier.isStatic(inv.modifiers) &&
                    inv.paramTypeNames == listOf("android.content.Context") &&
                    inv.returnTypeName == inv.declaredClassName
            }
            val settingsCls = settingsMtd?.declaredClass
            ctx.settingsClass = settingsCls
            putMethod(ctx, FxSymbolKeys.MTD_SETTINGS_GET_INSTANCE, settingsMtd)
            if (settingsCls != null) {
                putMethod(ctx, FxSymbolKeys.MTD_SETTINGS_THUMBS_ENABLED, settingsCls.methods.firstOrNull { m ->
                    !m.isConstructor && m.paramCount == 0 && m.returnTypeName == "boolean" &&
                        m.usingStrings.any { it == "fileViewImageThumbnails" }
                })
                putMethod(ctx, FxSymbolKeys.MTD_SETTINGS_THUMBS_FOR_PATH, settingsCls.methods.firstOrNull { m ->
                    !m.isConstructor && m.paramTypeNames == listOf("java.lang.String") && m.returnTypeName == "boolean" &&
                        m.usingStrings.any { it.startsWith("fileViewImageThumbnails") }
                })
            }

            // RemoteThumbTask constructor: (Context, FileItem, int, ThumbCache, String)
            val remoteThumbCtor = loadThumb.invokes.firstOrNull { inv ->
                inv.isConstructor &&
                    inv.paramCount == 5 &&
                    inv.paramTypeNames[0] == "android.content.Context" &&
                    inv.paramTypeNames[2] == "int" &&
                    (cacheCls == null || inv.paramTypeNames[3] == cacheCls.name) &&
                    inv.paramTypeNames[4] == "java.lang.String"
            }
            putMethod(ctx, FxSymbolKeys.CTR_REMOTE_THUMB_TASK, remoteThumbCtor)

            val taskCls = remoteThumbCtor?.declaredClass
            if (taskCls != null) {
                putField(ctx, FxSymbolKeys.FLD_REMOTE_THUMB_TASK_MIME, taskCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "java.lang.String" })
                val baseTaskCls = taskCls.superClass
                if (baseTaskCls != null) {
                    putField(ctx, FxSymbolKeys.FLD_THUMB_TASK_ITEM, baseTaskCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == ctx.fileItemClass?.name })
                    putField(ctx, FxSymbolKeys.FLD_THUMB_TASK_CONTEXT, baseTaskCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.content.Context" })
                    putMethod(ctx, FxSymbolKeys.MTD_THUMB_TASK_GET_CACHE_FILE, baseTaskCls.methods.firstOrNull { m ->
                        !m.isConstructor &&
                            Modifier.isStatic(m.modifiers) &&
                            m.paramCount == 4 &&
                            m.paramTypeNames[0] == "android.content.Context" &&
                            m.paramTypeNames[2] == "java.lang.String" &&
                            m.paramTypeNames[3] == "java.lang.String" &&
                            m.returnTypeName == "java.io.File"
                    })
                }

                // Anchor via taskCls ()->File method: constructs resultBoxCls, invokes worker.<init>(...), and reads resultBoxCls field
                val taskFileMtd = taskCls.methods.firstOrNull { m ->
                    !m.isConstructor && !Modifier.isStatic(m.modifiers) && m.paramCount == 0 && m.returnTypeName == "java.io.File"
                }
                val workerCtor = taskFileMtd?.invokes?.firstOrNull { inv ->
                    inv.isConstructor &&
                        inv.paramCount >= 3 &&
                        inv.paramTypeNames.contains(taskCls.name) &&
                        (ctx.taskThreadClass == null || inv.declaredClass?.superClass?.name == ctx.taskThreadClass?.name)
                } ?: taskFileMtd?.invokes?.firstOrNull { inv ->
                    inv.isConstructor && inv.paramCount >= 3 && inv.paramTypeNames.contains(taskCls.name)
                }
                val workerCls = workerCtor?.declaredClass

                val resultBoxCls = workerCtor?.paramTypes?.firstOrNull { p ->
                    p.name != taskCls.name && p.name != "java.lang.Class" && p.name != "java.lang.String"
                }
                if (resultBoxCls != null && taskFileMtd != null) {
                    val directResultFld = taskFileMtd.usingFields
                        .firstOrNull { uf -> uf.usingType.isRead() && uf.field.declaredClassName == resultBoxCls.name }
                        ?.field
                    val getterResultFld = taskFileMtd.invokes
                        .firstOrNull { inv -> inv.declaredClassName == resultBoxCls.name && !inv.isConstructor && inv.paramCount == 0 }
                        ?.usingFields
                        ?.firstOrNull { uf -> uf.usingType.isRead() && uf.field.declaredClassName == resultBoxCls.name }
                        ?.field
                    val resolvedResultFld = directResultFld
                        ?: getterResultFld
                        ?: resultBoxCls.fields.singleOrNull { !Modifier.isStatic(it.modifiers) }
                    putField(ctx, FxSymbolKeys.FLD_THUMB_RESULT, resolvedResultFld)
                }

                if (workerCtor != null && workerCls != null) {
                    val ctorWrites = ctx.dalvik?.findConstructorParamFieldWrites(workerCtor.descriptor)
                    val ownerParamIdx = workerCtor.paramTypeNames.indexOf(taskCls.name)
                    val ownerFldDesc = if (ownerParamIdx >= 0) ctorWrites?.paramToField?.get(1 + ownerParamIdx) else null
                    val ownerFld = ownerFldDesc?.let { d -> workerCls.fields.firstOrNull { it.descriptor == d } }
                        ?: workerCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == taskCls.name }
                    putField(ctx, FxSymbolKeys.FLD_REMOTE_THUMB_WORKER_OWNER, ownerFld)

                    val boxParamIdx = resultBoxCls?.let { rb -> workerCtor.paramTypeNames.indexOf(rb.name) } ?: -1
                    val boxFldDesc = if (boxParamIdx >= 0) ctorWrites?.paramToField?.get(1 + boxParamIdx) else null
                    val boxFld = boxFldDesc?.let { d -> workerCls.fields.firstOrNull { it.descriptor == d } }
                        ?: resultBoxCls?.let { rb -> workerCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == rb.name } }
                    putField(ctx, FxSymbolKeys.FLD_REMOTE_THUMB_WORKER_RESULT, boxFld)

                    val caseFld = workerCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "int" }
                    putField(ctx, FxSymbolKeys.FLD_REMOTE_THUMB_WORKER_CASE, caseFld)
                    if (caseFld != null) {
                        val intParamIdx = workerCtor.paramTypeNames.indexOf("int")
                        var caseId: Int? = if (intParamIdx >= 0 && taskFileMtd != null) {
                            ctx.dalvik?.findIntArgPassedToInvoke(taskFileMtd.descriptor, workerCtor.descriptor, 1 + intParamIdx)
                        } else {
                            null
                        }
                        if (caseId == null) {
                            caseId = ctorWrites?.intConstToField?.get(caseFld.descriptor)
                                ?: workerCtor.usingNumbers.firstOrNull()?.intValue()
                        }
                        if (caseId != null) {
                            ctx.ints[FxSymbolKeys.INT_REMOTE_THUMB_WORKER_CASE_ID] = caseId
                        }
                    }
                }
            }
        }

        // RemoteThumbnailWorker anchored on "GeneratedRemoteItemThumbnail"
        val anchoredWorkerCls = ctx.fields[FxSymbolKeys.FLD_REMOTE_THUMB_WORKER_OWNER]
            ?.substringBefore("->")
            ?.let { DexSymbolCache.descriptorToJavaClassName(it) }
            ?.let { ctx.bridge.getClassData(it) }
        val workerMethod = anchoredWorkerCls?.let { cls ->
            ctx.bridge.findMethod {
                findFirst = true
                searchInClass(listOf(cls))
                matcher {
                    usingStrings("GeneratedRemoteItemThumbnail")
                }
            }.firstOrNull()
        } ?: ctx.bridge.findMethod {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                usingStrings("GeneratedRemoteItemThumbnail")
            }
        }.firstOrNull()

        if (workerMethod != null) {
            val workerCls = workerMethod.declaredClass
            if (workerCls != null) {
                putMethod(ctx, FxSymbolKeys.MTD_REMOTE_THUMB_WORKER_RUN, workerMethod)

                if (ctx.fields[FxSymbolKeys.FLD_REMOTE_THUMB_WORKER_OWNER] == null) {
                    val caseFld = workerCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "int" }
                    putField(ctx, FxSymbolKeys.FLD_REMOTE_THUMB_WORKER_CASE, caseFld)
                    val remoteTaskClsName = ctx.methods[FxSymbolKeys.CTR_REMOTE_THUMB_TASK]
                        ?.substringBefore("->")
                        ?.let { DexSymbolCache.descriptorToJavaClassName(it) }
                    val ownerFld = workerCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == remoteTaskClsName }
                        ?: workerMethod.usingFields
                            .lastOrNull { uf ->
                                uf.usingType.isRead() &&
                                    uf.field.declaredClassName == workerCls.name &&
                                    uf.field.typeName != "int" &&
                                    !uf.field.typeName.startsWith("android.") &&
                                    uf.field.typeName != "java.lang.String"
                            }?.field
                    putField(ctx, FxSymbolKeys.FLD_REMOTE_THUMB_WORKER_OWNER, ownerFld)
                }

                // ThumbGenerator anchored directly from worker's static createForFile(Context, String, File) call!
                val createForFile = workerMethod.invokes.firstOrNull { inv ->
                    Modifier.isStatic(inv.modifiers) &&
                        inv.paramTypeNames == listOf("android.content.Context", "java.lang.String", "java.io.File")
                }
                putMethod(ctx, FxSymbolKeys.MTD_THUMB_GEN_CREATE_FOR_FILE, createForFile)
                val thumbGenCls = createForFile?.returnType
                putClass(ctx, FxSymbolKeys.CLS_THUMB_GENERATOR, thumbGenCls)
                if (thumbGenCls != null) {
                    populateThumbGeneratorBindings(ctx, thumbGenCls, workerMethod)
                }
            }
        }

        // Marker interfaces (remoteItemMarkerClass & cloudThumbMarkerClass) and Dropbox helper
        if (loadThumb != null && ctx.fileItemClass != null) {
            val fileItemCls = ctx.fileItemClass!!
            val fileItemName = fileItemCls.name
            val fileItemPkg = fileItemName.substringBeforeLast('.', "")

            // cloudThumbMarker (ue.j0 / kh.h0): extends FileItem, declares ()->boolean, invoked inside loadThumb
            val invokedZeroArgBoolOnSubIface = loadThumb.invokes
                .filter { inv ->
                    val invCls = inv.declaredClass ?: return@filter false
                    !inv.isConstructor &&
                        !Modifier.isStatic(inv.modifiers) &&
                        inv.paramCount == 0 &&
                        inv.returnTypeName == "boolean" &&
                        inv.declaredClassName != fileItemName &&
                        Modifier.isInterface(invCls.modifiers) &&
                        invCls.interfaces.any { it.name == fileItemName }
                }
            val cloudThumbMarker = invokedZeroArgBoolOnSubIface
                .mapNotNull { it.declaredClass }
                .firstOrNull { iface ->
                    iface.methods.any { m -> !m.isConstructor && m.paramCount == 0 && m.returnTypeName == "java.lang.String" }
                } ?: invokedZeroArgBoolOnSubIface.firstOrNull()?.declaredClass

            // remoteItemMarker (ue.g0 / kh.f0): empty marker interface extending FileItem implemented by >= 2 remote classes
            val emptyMarkerCandidates = ctx.bridge.findClass {
                if (fileItemPkg.isNotEmpty()) searchPackages(fileItemPkg)
                matcher {
                    modifiers = Modifier.INTERFACE
                    interfaces { add { className = fileItemName } }
                }
            }.filter { cls ->
                cls.name != fileItemName &&
                    cls.name != cloudThumbMarker?.name &&
                    cls.methods.none { !it.isConstructor } &&
                    cls.interfaces.size == 1
            }

            val remoteItemMarker = emptyMarkerCandidates.maxByOrNull { marker ->
                ctx.bridge.findClass {
                    excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
                    matcher {
                        interfaces { add { className = marker.name } }
                    }
                }.size
            }

            putClass(ctx, FxSymbolKeys.CLS_REMOTE_ITEM_MARKER, remoteItemMarker)
            putClass(ctx, FxSymbolKeys.CLS_CLOUD_THUMB_MARKER, cloudThumbMarker)

            if (cloudThumbMarker != null) {
                val cloudBoolName = cloudThumbMarker.methods
                    .firstOrNull { m -> !m.isConstructor && m.paramCount == 0 && m.returnTypeName == "boolean" }
                    ?.methodName
                val cloudImpls = ctx.bridge.findClass {
                    excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
                    matcher {
                        interfaces { add { className = cloudThumbMarker.name } }
                    }
                }
                val dropboxCls = cloudImpls.firstOrNull { cls ->
                    cls.name.contains("dropbox", ignoreCase = true) ||
                        cls.fields.any { f -> f.typeName.contains("Dropbox", ignoreCase = true) } ||
                        cls.superClass?.fields?.any { f -> f.typeName.contains("Dropbox", ignoreCase = true) } == true
                } ?: ctx.bridge.findClass {
                    findFirst = true
                    searchPackages("nextapp.fx.plus.dirimpl.dropbox")
                    matcher {
                        interfaces { add { className = cloudThumbMarker.name } }
                    }
                }.firstOrNull()

                val dropboxSupports = if (cloudBoolName != null) {
                    dropboxCls?.methods?.firstOrNull { m ->
                        !m.isConstructor && !Modifier.isStatic(m.modifiers) && m.methodName == cloudBoolName && m.paramCount == 0
                    }
                } else {
                    null
                }
                putMethod(ctx, FxSymbolKeys.MTD_DROPBOX_SUPPORTS_THUMB, dropboxSupports)
            }
        }
    }

    private fun populateThumbGeneratorBindings(
        ctx: ScanContext,
        thumbGen: ClassData,
        workerMethod: MethodData
    ) {
        val flagsFld = thumbGen.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "int" }
        val ctxFld = thumbGen.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "android.content.Context" }
        val outFileFld = thumbGen.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "java.io.File" }
        val srcPathFld = thumbGen.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "java.lang.String" }
        val streamFld = thumbGen.fields.firstOrNull { f ->
            !Modifier.isStatic(f.modifiers) &&
                f.typeName != "int" &&
                f.typeName != "boolean" &&
                f.typeName != "android.content.Context" &&
                f.typeName != "java.io.File" &&
                f.typeName != "java.lang.String" &&
                f.type?.methods?.any { m -> m.methodName == "read" && m.paramCount == 0 && m.returnTypeName == "java.io.InputStream" } == true
        }
        putField(ctx, FxSymbolKeys.FLD_THUMB_GEN_FLAGS, flagsFld)
        putField(ctx, FxSymbolKeys.FLD_THUMB_GEN_CONTEXT, ctxFld)
        putField(ctx, FxSymbolKeys.FLD_THUMB_GEN_STREAM, streamFld)
        putField(ctx, FxSymbolKeys.FLD_THUMB_GEN_OUT_FILE, outFileFld)
        putField(ctx, FxSymbolKeys.FLD_THUMB_GEN_SOURCE_PATH, srcPathFld)
        putMethod(ctx, FxSymbolKeys.MTD_THUMB_GEN_STREAM_READ, streamFld?.type?.methods?.firstOrNull { it.methodName == "read" && it.paramCount == 0 })

        // run() invoked directly by workerMethod on thumbGen (returns ThumbImageInfo)
        val runMtd = workerMethod.invokes.firstOrNull { inv ->
            inv.declaredClassName == thumbGen.name && !inv.isConstructor && inv.paramCount == 0 && inv.returnTypeName != "void"
        } ?: thumbGen.methods.firstOrNull { m ->
            !m.isConstructor && !Modifier.isStatic(m.modifiers) && m.paramCount == 0 && m.returnTypeName != "void"
        }
        putMethod(ctx, FxSymbolKeys.MTD_THUMB_GEN_RUN, runMtd)

        val infoCls = runMtd?.returnType
        val infoCtor = infoCls?.methods?.firstOrNull { m ->
            m.isConstructor && m.paramCount == 4 &&
                (m.paramTypeNames == listOf("java.lang.String", "int", "int", "java.lang.String") ||
                    m.paramTypeNames == listOf("int", "int", "java.lang.String", "java.lang.String"))
        }
        putMethod(ctx, FxSymbolKeys.CTR_THUMB_IMAGE_INFO, infoCtor)

        // Generate 3-arg static method (9012 c(StreamSrc, File, String)) vs 0-arg instance run (9108 a())
        val gen3Mtd = thumbGen.methods.firstOrNull { m ->
            !m.isConstructor &&
                Modifier.isStatic(m.modifiers) &&
                m.paramCount == 3 &&
                m.paramTypeNames[1] == "java.io.File" &&
                m.paramTypeNames[2] == "java.lang.String"
        }
        putMethod(ctx, FxSymbolKeys.MTD_THUMB_GEN_GENERATE, gen3Mtd ?: runMtd)

        // Exceeds pixel cap (9012 k(DimClass) -> boolean using sqrt / 524288)
        val exceedsCapMtd = thumbGen.methods.firstOrNull { m ->
            !m.isConstructor &&
                !Modifier.isStatic(m.modifiers) &&
                m.paramCount == 1 &&
                m.returnTypeName == "boolean" &&
                (ctx.dimClass == null || m.paramTypeNames[0] == ctx.dimClass?.name)
        }
        putMethod(ctx, FxSymbolKeys.MTD_THUMB_GEN_EXCEEDS_CAP, exceedsCapMtd)
        if (ctx.fields[FxSymbolKeys.FLD_DIM_WIDTH] == null && exceedsCapMtd != null) {
            val dCls = exceedsCapMtd.paramTypes.firstOrNull()
            val intFields = dCls?.fields?.filter { !Modifier.isStatic(it.modifiers) && it.typeName == "int" }.orEmpty()
            if (intFields.size >= 2) {
                putField(ctx, FxSymbolKeys.FLD_DIM_WIDTH, intFields[0])
                putField(ctx, FxSymbolKeys.FLD_DIM_HEIGHT, intFields[1])
            }
        }
    }

    // -------------------------------------------------------------------------
    // Subsystem 9: Folder Lifecycle (ExplorerActivity, WindowManager, WindowModel)
    // -------------------------------------------------------------------------
    private fun scanFolderLifecycle(ctx: ScanContext) {
        val explorer = ctx.bridge.getClassData("nextapp.fx.ui.ExplorerActivity") ?: return
        putClass(ctx, FxSymbolKeys.CLS_EXPLORER_ACTIVITY, explorer)
        putMethod(ctx, FxSymbolKeys.MTD_EXPLORER_ON_RESUME, explorer.methods.firstOrNull { it.methodName == "onResume" && it.paramCount == 0 })
        putMethod(ctx, FxSymbolKeys.MTD_EXPLORER_ON_DESTROY, explorer.methods.firstOrNull { it.methodName == "onDestroy" && it.paramCount == 0 })

        val contentHost = explorer.superClass ?: return
        val fxPath = ctx.fxPathClass ?: return

        // Find WindowManager field on contentHost: type has ArrayList field AND a 1-arg void method taking WindowModel
        for (fld in contentHost.fields) {
            if (Modifier.isStatic(fld.modifiers)) continue
            val candidateMgr = fld.type ?: continue
            if (!candidateMgr.name.startsWith("nextapp.fx.")) continue
            val listFld = candidateMgr.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "java.util.ArrayList" }
                ?: continue

            // WindowModel candidate: a 1-arg void method parameter type on candidateMgr that holds (FxPath) -> void and () -> ContentModel
            for (closeMtd in candidateMgr.methods) {
                if (closeMtd.isConstructor || Modifier.isStatic(closeMtd.modifiers) || closeMtd.paramCount != 1 || closeMtd.returnTypeName != "void") {
                    continue
                }
                val modelCls = closeMtd.paramTypes.firstOrNull() ?: continue
                if (!modelCls.name.startsWith("nextapp.fx.")) continue

                val setPathMtd = modelCls.methods.firstOrNull { m ->
                    !m.isConstructor && !Modifier.isStatic(m.modifiers) && m.paramTypeNames == listOf(fxPath.name) && m.returnTypeName == "void"
                } ?: continue

                val currentContentMtd = modelCls.methods.firstOrNull { m ->
                    !m.isConstructor && !Modifier.isStatic(m.modifiers) && m.paramCount == 0 &&
                        m.returnTypeName != "void" && m.returnTypeName != fxPath.name &&
                        m.returnType?.fields?.any { !Modifier.isStatic(it.modifiers) && it.typeName == fxPath.name } == true
                } ?: continue

                val pathFld = currentContentMtd.returnType?.fields?.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == fxPath.name }
                    ?: continue

                putField(ctx, FxSymbolKeys.FLD_WINDOW_MANAGER, fld)
                putField(ctx, FxSymbolKeys.FLD_WINDOW_MODELS, listFld)
                putMethod(ctx, FxSymbolKeys.MTD_WINDOW_MODEL_CLOSE, closeMtd)
                putMethod(ctx, FxSymbolKeys.MTD_WINDOW_MODEL_SET_PATH, setPathMtd)
                putMethod(ctx, FxSymbolKeys.MTD_WINDOW_MODEL_CURRENT_CONTENT, currentContentMtd)
                putField(ctx, FxSymbolKeys.FLD_CONTENT_MODEL_PATH, pathFld)
                return
            }
        }
    }

    // -------------------------------------------------------------------------
    // Subsystem 10: Bonus Experience Limits (TextViewer, TextEdit, ExecActivity)
    // -------------------------------------------------------------------------
    private fun scanBonusLimits(ctx: ScanContext) {
        val textViewer = ctx.bridge.getClassData("nextapp.fx.ui.viewer.TextViewerActivity")
        val execActivity = ctx.bridge.getClassData("nextapp.fx.ui.viewer.ExecActivity")

        // 1) TextViewerActivity ("x/x-android-binary-xml")
        val tvLoader = ctx.bridge.findMethod {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                usingStrings("x/x-android-binary-xml")
            }
        }.firstOrNull()

        var encodingStreamCls: ClassData? = null
        var mimeProbeMtd: MethodData? = null

        if (tvLoader != null && textViewer != null) {
            mimeProbeMtd = tvLoader.invokes.firstOrNull { inv ->
                Modifier.isStatic(inv.modifiers) &&
                    inv.paramTypeNames == listOf("byte[]") &&
                    inv.returnTypeName == "java.lang.String"
            }
            putMethod(ctx, FxSymbolKeys.MTD_MIME_PROBE_BYTES, mimeProbeMtd)

            encodingStreamCls = tvLoader.invokes.firstOrNull { inv ->
                inv.isConstructor && inv.paramTypeNames == listOf("java.io.InputStream")
            }?.declaredClass
            resolveEncodingStream(ctx, encodingStreamCls)

            // Open binary xml static method on TextViewerActivity
            putMethod(ctx, FxSymbolKeys.MTD_TEXT_VIEWER_OPEN_BINARY_XML, textViewer.methods.firstOrNull { m ->
                !m.isConstructor &&
                    Modifier.isStatic(m.modifiers) &&
                    m.paramCount == 1 &&
                    java.io.Reader::class.java.name.let { m.returnType?.superClass?.name == it || m.returnTypeName == "java.io.StringReader" || m.returnTypeName == "java.io.Reader" }
            })

            // Lines (ArrayList), Charset (String), Truncated & Unsupported booleans on TextViewerActivity
            val listFld = tvLoader.usingFields.firstOrNull { uf ->
                uf.usingType.isWrite() && uf.field.declaredClassName == textViewer.name && uf.field.typeName == "java.util.ArrayList"
            }?.field
            val csFld = tvLoader.usingFields.firstOrNull { uf ->
                uf.usingType.isWrite() && uf.field.declaredClassName == textViewer.name && uf.field.typeName == "java.lang.String"
            }?.field
            val tvBoolWrites = tvLoader.usingFields
                .filter { uf -> uf.usingType.isWrite() && uf.field.declaredClassName == textViewer.name && uf.field.typeName == "boolean" }
                .map { it.field }
                .distinctBy { it.descriptor }
            putField(ctx, FxSymbolKeys.FLD_TEXT_VIEWER_LINES, listFld)
            putField(ctx, FxSymbolKeys.FLD_TEXT_VIEWER_CHARSET, csFld)

            var applyForBoolDisambig: MethodData? = null
            if (tvLoader.declaredClassName == textViewer.name) {
                // Direct method layout (9012 w0/u0)
                putMethod(ctx, FxSymbolKeys.MTD_TEXT_VIEWER_LOAD, tvLoader)
                val uiTarget = tvLoader.invokes.firstOrNull { inv ->
                    inv.isConstructor && inv.paramTypeNames == listOf(textViewer.name)
                }?.declaredClass?.let { innerRunnable ->
                    ctx.bridge.findMethod {
                        findFirst = true
                        searchInClass(listOf(innerRunnable))
                        matcher { name = "run" }
                    }.firstOrNull()?.invokes?.firstOrNull { it.declaredClassName == textViewer.name && it.paramCount == 0 }
                }
                putMethod(ctx, FxSymbolKeys.MTD_TEXT_VIEWER_APPLY, uiTarget)
                applyForBoolDisambig = uiTarget
            } else {
                // Worker Runnable layout (9108 pg.f0)
                val workerCls = tvLoader.declaredClass
                if (workerCls != null) {
                    putMethod(ctx, FxSymbolKeys.MTD_TEXT_VIEWER_WORKER_RUN, tvLoader)
                    applyForBoolDisambig = tvLoader
                    val workerCtor = workerCls.methods.firstOrNull { m ->
                        m.isConstructor && m.paramTypeNames == listOf(textViewer.name, "int")
                    }
                    putMethod(ctx, FxSymbolKeys.CTR_TEXT_VIEWER_WORKER, workerCtor)
                    val ctorWrites = workerCtor?.let { ctx.dalvik?.findConstructorParamFieldWrites(it.descriptor) }
                    val caseFld = ctorWrites?.paramToField?.get(2)
                        ?.let { d -> workerCls.fields.firstOrNull { it.descriptor == d } }
                        ?: workerCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "int" }
                    val actFld = ctorWrites?.paramToField?.get(1)
                        ?.let { d -> workerCls.fields.firstOrNull { it.descriptor == d } }
                        ?: workerCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == textViewer.name }
                    putField(ctx, FxSymbolKeys.FLD_TEXT_VIEWER_WORKER_CASE, caseFld)
                    putField(ctx, FxSymbolKeys.FLD_TEXT_VIEWER_WORKER_ACTIVITY, actFld)
                    val extractedTvCase = workerCtor?.let { wc ->
                        textViewer.methods.firstOrNull { m ->
                            m.invokes.any { it.descriptor == wc.descriptor }
                        }?.let { starter ->
                            ctx.dalvik?.findIntArgPassedToInvoke(starter.descriptor, wc.descriptor, 2)
                        }
                    } ?: 0
                    ctx.ints[FxSymbolKeys.INT_TEXT_VIEWER_WORKER_CASE_ID] = extractedTvCase
                }
            }

            // Disambiguate unsupported vs truncated via UI apply: unsupported is read immediately before 2-arg getString(int, Object...)
            val candidateBoolDescs = tvBoolWrites.map { it.descriptor }.toSet()
            val (unsupportedDesc, truncatedDesc) = if (applyForBoolDisambig != null && candidateBoolDescs.size >= 2) {
                ctx.dalvik?.findTextViewerUnsupportedAndTruncatedFields(
                    applyMethodDesc = applyForBoolDisambig.descriptor,
                    textViewerClassDesc = textViewer.descriptor,
                    candidateFieldDescs = candidateBoolDescs
                ) ?: (null to null)
            } else {
                null to null
            }
            val unsupportedFld = unsupportedDesc?.let { d -> tvBoolWrites.firstOrNull { it.descriptor == d } }
                ?: tvBoolWrites.firstOrNull()
            val truncatedFld = truncatedDesc?.let { d -> tvBoolWrites.firstOrNull { it.descriptor == d } }
                ?: tvBoolWrites.firstOrNull { it.descriptor != unsupportedFld?.descriptor }
                ?: tvBoolWrites.getOrNull(1)
                ?: tvBoolWrites.firstOrNull()
            putField(ctx, FxSymbolKeys.FLD_TEXT_VIEWER_UNSUPPORTED, unsupportedFld)
            putField(ctx, FxSymbolKeys.FLD_TEXT_VIEWER_TRUNCATED, truncatedFld)
        }

        // 2) TextEdit stream reader (51200 & 1048576 + "Out of memory reading file")
        val textEditMtd = ctx.bridge.findMethod {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                modifiers = Modifier.STATIC
                paramTypes("java.io.InputStream", "java.lang.String")
                usingNumbers(51200, 1048576)
            }
        }.firstOrNull()
        if (textEditMtd != null) {
            putMethod(ctx, FxSymbolKeys.MTD_TEXT_EDIT_READ_STREAM, textEditMtd)
            if (encodingStreamCls == null) {
                encodingStreamCls = textEditMtd.invokes.firstOrNull { inv ->
                    inv.isConstructor && inv.paramTypeNames == listOf("java.io.InputStream")
                }?.declaredClass
                resolveEncodingStream(ctx, encodingStreamCls)
            }
            val retCls = textEditMtd.returnType
            val ctor2 = retCls?.methods?.firstOrNull { m ->
                m.isConstructor && m.paramTypeNames == listOf("java.lang.CharSequence", "java.lang.String")
            }
            val ctor3 = retCls?.methods?.firstOrNull { m ->
                m.isConstructor && m.paramCount == 3 && m.paramTypeNames[1] == "int"
            }
            putMethod(ctx, FxSymbolKeys.CTR_TEXT_EDIT_RESULT, ctor2 ?: ctor3)
            if (ctor3 != null) {
                // Extract the exact int arg passed to ctor3(CharSequence, int, String) rather than scanning unrelated numeric literals (e.g. '\n' = 10)
                val ctorCaseId = ctx.dalvik?.findIntArgPassedToInvoke(
                    callerMethodDesc = textEditMtd.descriptor,
                    calleeMethodDesc = ctor3.descriptor,
                    invokeArgIndex = 2
                )
                if (ctorCaseId != null) {
                    ctx.ints[FxSymbolKeys.INT_TEXT_EDIT_RESULT_CASE_ID] = ctorCaseId
                }
            }
        }

        // 3) ExecActivity preview ("x/x-elf")
        val execLoader = ctx.bridge.findMethod {
            findFirst = true
            excludePackages(*EXCLUDED_LIBRARY_PACKAGES)
            matcher {
                usingStrings("x/x-elf")
            }
        }.firstOrNull()

        if (execLoader != null && execActivity != null) {
            if (mimeProbeMtd == null) {
                putMethod(ctx, FxSymbolKeys.MTD_MIME_PROBE_BYTES, execLoader.invokes.firstOrNull { inv ->
                    Modifier.isStatic(inv.modifiers) && inv.paramTypeNames == listOf("byte[]") && inv.returnTypeName == "java.lang.String"
                })
            }

            putField(ctx, FxSymbolKeys.FLD_EXEC_LINES, execLoader.usingFields.firstOrNull { uf ->
                uf.usingType.isWrite() && uf.field.declaredClassName == execActivity.name && uf.field.typeName == "java.util.ArrayList"
            }?.field)
            putField(ctx, FxSymbolKeys.FLD_EXEC_UNSUPPORTED, execLoader.usingFields.firstOrNull { uf ->
                uf.usingType.isWrite() && uf.field.declaredClassName == execActivity.name && uf.field.typeName == "boolean"
            }?.field)
            putField(ctx, FxSymbolKeys.FLD_EXEC_CHARSET, execLoader.usingFields.firstOrNull { uf ->
                uf.usingType.isWrite() && uf.field.declaredClassName == execActivity.name && uf.field.typeName == "java.lang.String"
            }?.field ?: execActivity.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "java.lang.String" })

            if (execLoader.declaredClassName == execActivity.name) {
                // Classic direct preview loader (9012 d2 / c2)
                putMethod(ctx, FxSymbolKeys.MTD_EXEC_LOAD_PREVIEW, execLoader)
                val uiTarget = execLoader.invokes.firstOrNull { inv ->
                    inv.isConstructor && inv.paramTypeNames == listOf(execActivity.name)
                }?.declaredClass?.let { innerRunnable ->
                    ctx.bridge.findMethod {
                        findFirst = true
                        searchInClass(listOf(innerRunnable))
                        matcher { name = "run" }
                    }.firstOrNull()?.invokes?.firstOrNull { it.declaredClassName == execActivity.name && it.paramCount == 0 }
                }
                putMethod(ctx, FxSymbolKeys.MTD_EXEC_APPLY_PREVIEW, uiTarget)
            } else {
                // Merged R8 worker (9108 w.run case 19 + pg.k(execActivity, 6))
                val workerCls = execLoader.declaredClass
                if (workerCls != null) {
                    putMethod(ctx, FxSymbolKeys.MTD_EXEC_WORKER_RUN, execLoader)
                    val uiRunnableCtor = execLoader.invokes.firstOrNull { inv ->
                        inv.isConstructor && inv.paramTypeNames == listOf(execActivity.name, "int")
                    }
                    putMethod(ctx, FxSymbolKeys.CTR_EXEC_UI_RUNNABLE, uiRunnableCtor)
                    if (uiRunnableCtor != null) {
                        val uiCase = ctx.dalvik?.findIntArgPassedToInvoke(
                            callerMethodDesc = execLoader.descriptor,
                            calleeMethodDesc = uiRunnableCtor.descriptor,
                            invokeArgIndex = 2
                        )
                        if (uiCase != null) {
                            ctx.ints[FxSymbolKeys.INT_EXEC_UI_RUNNABLE_CASE_ID] = uiCase
                        }
                    }

                    val uiRunMtd = uiRunnableCtor?.declaredClass?.methods?.firstOrNull { m ->
                        !m.isConstructor && m.methodName == "run" && m.paramCount == 0
                    }
                    val workerCtor = uiRunMtd?.invokes?.firstOrNull { inv ->
                        inv.isConstructor &&
                            inv.declaredClassName == workerCls.name &&
                            inv.paramTypeNames.contains("int")
                    }
                    val ctorWrites = workerCtor?.let {
                        ctx.dalvik?.findConstructorParamFieldWrites(it.descriptor)
                    }
                    val intParamIdx = workerCtor?.paramTypeNames?.indexOf("int") ?: -1
                    val caseFld = if (intParamIdx >= 0) {
                        ctorWrites?.paramToField?.get(1 + intParamIdx)?.let { desc ->
                            workerCls.fields.firstOrNull { it.descriptor == desc }
                        }
                    } else {
                        null
                    } ?: workerCls.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "int" }
                    putField(ctx, FxSymbolKeys.FLD_EXEC_WORKER_CASE, caseFld)

                    val caseId = ctx.dalvik?.findSwitchCaseReachingString(execLoader.descriptor, "x/x-elf")
                        ?: if (uiRunMtd != null && workerCtor != null && intParamIdx >= 0) {
                            val passedArgs = ctx.dalvik?.findAllIntArgsPassedToInvoke(
                                callerMethodDesc = uiRunMtd.descriptor,
                                calleeMethodDesc = workerCtor.descriptor,
                                invokeArgIndex = 1 + intParamIdx
                            ).orEmpty()
                            passedArgs.singleOrNull()
                                ?: passedArgs.firstOrNull { execLoader.usingNumbers.any { n -> n.intValue() == it } }
                        } else {
                            null
                        }
                    if (caseId != null) {
                        ctx.ints[FxSymbolKeys.INT_EXEC_WORKER_CASE_ID] = caseId
                    }

                    val execActivityDesc = execActivity.descriptor
                    val nonIntParamIndices = workerCtor?.paramTypeNames?.indices?.filter { it != intParamIdx }.orEmpty()
                    val arg1DescFromCtor = nonIntParamIndices.getOrNull(0)?.let { ctorWrites?.paramToField?.get(1 + it) }
                    val arg2DescFromCtor = nonIntParamIndices.getOrNull(1)?.let { ctorWrites?.paramToField?.get(1 + it) }
                    val candidateWorkerObjDescs = setOfNotNull(arg1DescFromCtor, arg2DescFromCtor)
                        .ifEmpty { workerCls.fields.filter { !Modifier.isStatic(it.modifiers) }.mapTo(HashSet()) { it.descriptor } }
                    val arg1DescByCast = ctx.dalvik?.findFieldCastToType(
                        methodDesc = execLoader.descriptor,
                        targetTypeDesc = execActivityDesc,
                        candidateFieldDescs = candidateWorkerObjDescs
                    )

                    val argReads = execLoader.usingFields
                        .filter { uf -> uf.usingType.isRead() && uf.field.declaredClassName == workerCls.name && uf.field.typeName == "java.lang.Object" }
                        .map { it.field }
                        .distinctBy { it.descriptor }

                    val arg1 = (arg1DescByCast ?: arg1DescFromCtor)?.let { d ->
                        workerCls.fields.firstOrNull { it.descriptor == d }
                    } ?: argReads.firstOrNull()
                    val arg2 = (if (arg1 != null && arg1.descriptor == arg1DescFromCtor) arg2DescFromCtor else arg1DescFromCtor)
                        ?.let { d -> workerCls.fields.firstOrNull { it.descriptor == d && it.descriptor != arg1?.descriptor } }
                        ?: argReads.firstOrNull { it.descriptor != arg1?.descriptor }
                    putField(ctx, FxSymbolKeys.FLD_EXEC_WORKER_ARG1, arg1)
                    putField(ctx, FxSymbolKeys.FLD_EXEC_WORKER_ARG2, arg2)
                }
            }
        }
    }

    private fun resolveEncodingStream(ctx: ScanContext, encodingStream: ClassData?) {
        encodingStream ?: return
        putMethod(ctx, FxSymbolKeys.CTR_ENCODING_STREAM, encodingStream.methods.firstOrNull { m ->
            m.isConstructor && m.paramTypeNames == listOf("java.io.InputStream")
        })
        putMethod(ctx, FxSymbolKeys.MTD_ENCODING_STREAM_GET_CHARSET, encodingStream.methods.firstOrNull { m ->
            !m.isConstructor && !Modifier.isStatic(m.modifiers) && m.paramCount == 0 && m.returnTypeName == "java.lang.String"
        })
        putMethod(ctx, FxSymbolKeys.MTD_ENCODING_STREAM_GET_BUFFER, encodingStream.methods.firstOrNull { m ->
            !m.isConstructor && !Modifier.isStatic(m.modifiers) && m.paramCount == 0 && m.returnTypeName == "byte[]"
        })
        putMethod(ctx, FxSymbolKeys.MTD_ENCODING_STREAM_DETECT, encodingStream.methods.firstOrNull { m ->
            !m.isConstructor && !Modifier.isStatic(m.modifiers) && m.paramCount == 0 && m.returnTypeName == "void" &&
                m.methodName != "close" &&
                m.usingFields.any { uf -> uf.usingType.isWrite() && uf.field.typeName == "java.lang.String" }
        })
        putField(ctx, FxSymbolKeys.FLD_ENCODING_STREAM_CHARSET, encodingStream.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "java.lang.String" })
        putField(ctx, FxSymbolKeys.FLD_ENCODING_STREAM_BUFFER, encodingStream.fields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.typeName == "byte[]" })
    }

    private fun putClass(ctx: ScanContext, key: String, cls: ClassData?) {
        if (cls != null) ctx.classes[key] = cls.descriptor
    }

    private fun putMethod(ctx: ScanContext, key: String, mtd: MethodData?) {
        if (mtd != null) ctx.methods[key] = mtd.descriptor
    }

    private fun putField(ctx: ScanContext, key: String, fld: FieldData?) {
        if (fld != null) ctx.fields[key] = fld.descriptor
    }
}

/**
 * Lightweight pure-Kotlin Dalvik bytecode inspector backed by raw `classes*.dex` bytes.
 * Used as a precision tie-breaker for R8-merged methods and constructors where DexKit's
 * unordered `usingFields` / `usingNumbers` sets cannot distinguish field or argument slots.
 */
internal class DalvikCodeInspector private constructor(
    private val dexFiles: List<ParsedDex>
) {

    data class CtorFieldMapping(
        val paramToField: Map<Int, String>,
        val intConstToField: Map<String, Int>
    )

    data class UiThemeCtorFields(
        val boxBgFieldDesc: String?,
        val boxPressedBgFieldDesc: String?,
        val textPrimaryFieldDesc: String?,
        val textSecondaryFieldDesc: String?
    )

    companion object {
        private val DEX_ENTRY_REGEX = Regex("^classes\\d*\\.dex$")
        private const val VAL_UNSET = Long.MIN_VALUE
        private const val VAL_TOP = Long.MAX_VALUE
        private const val SRC_UNSET = -2
        private const val SRC_TOP = -1

        private const val UI_TEXT_PRIMARY_DARK_ON_LIGHT = -553648128 // 0xDF000000
        private const val UI_TEXT_SECONDARY_DARK_ON_LIGHT = 1862270976 // 0x6F000000

        fun fromAppInfo(appInfo: ApplicationInfo?): DalvikCodeInspector? {
            if (appInfo == null) return null
            val paths = linkedSetOf<String>()
            appInfo.sourceDir?.takeIf { it.isNotBlank() }?.let { paths.add(it) }
            appInfo.splitSourceDirs?.forEach { split ->
                if (!split.isNullOrBlank()) paths.add(split)
            }
            val files = paths.map { File(it) }.filter { it.isFile }
            return fromApkFiles(files)
        }

        fun fromApkFiles(apkFiles: List<File>): DalvikCodeInspector? {
            if (apkFiles.isEmpty()) return null
            val parsed = mutableListOf<ParsedDex>()
            for (file in apkFiles) {
                if (!file.isFile) continue
                if (file.name.endsWith(".dex")) {
                    ParsedDex.parseOrNull(file.readBytes())?.let { parsed.add(it) }
                    continue
                }
                runCatching {
                    ZipFile(file).use { zip ->
                        val dexEntries = zip.entries().asSequence()
                            .filter { !it.isDirectory && DEX_ENTRY_REGEX.matches(it.name) }
                            .sortedBy { it.name }
                            .toList()
                        for (entry in dexEntries) {
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            ParsedDex.parseOrNull(bytes)?.let { parsed.add(it) }
                        }
                    }
                }
            }
            return if (parsed.isEmpty()) null else DalvikCodeInspector(parsed)
        }
    }

    /**
     * Finds the first literal integer passed at [invokeArgIndex] (0 = receiver for instance/constructor
     * calls, 1 = first parameter, 2 = second parameter, etc.) to [calleeMethodDesc] inside [callerMethodDesc].
     */
    fun findIntArgPassedToInvoke(
        callerMethodDesc: String,
        calleeMethodDesc: String,
        invokeArgIndex: Int
    ): Int? = findAllIntArgsPassedToInvoke(callerMethodDesc, calleeMethodDesc, invokeArgIndex).firstOrNull()

    /**
     * Finds all literal integers passed at [invokeArgIndex] across every call to [calleeMethodDesc]
     * inside [callerMethodDesc].
     */
    fun findAllIntArgsPassedToInvoke(
        callerMethodDesc: String,
        calleeMethodDesc: String,
        invokeArgIndex: Int
    ): List<Int> {
        if (invokeArgIndex < 0) return emptyList()
        val code = findMethodCode(callerMethodDesc) ?: return emptyList()
        val decoded = decodeMethod(code)
        if (decoded.insns.isEmpty()) return emptyList()

        val targetArgWordSlot = computeInvokeArgWordSlot(calleeMethodDesc, invokeArgIndex)
        val states = propagateStates(code, decoded, trackEntryParams = false)
        val results = mutableListOf<Int>()
        for (idx in decoded.insns.indices) {
            val insn = decoded.insns[idx]
            if (!insn.isInvoke) continue
            val invokedDesc = code.dex.getMethodDesc(insn.refIndex)
            if (invokedDesc != calleeMethodDesc) continue
            if (targetArgWordSlot >= insn.invokeArgs.size) continue
            val reg = insn.invokeArgs[targetArgWordSlot]

            val entryState = states.inConst[idx]
            val fromCfg = entryState?.getOrNull(reg)
            if (fromCfg != null && fromCfg != VAL_UNSET && fromCfg != VAL_TOP) {
                results.add(fromCfg.toInt())
                continue
            }
            val fallback = backwardFindConstInt(decoded, idx, reg)
            if (fallback != null) results.add(fallback)
        }
        return results
    }

    /**
     * Finds the switch `case` key inside [methodDesc] whose branch reaches literal string [targetString].
     */
    fun findSwitchCaseReachingString(methodDesc: String, targetString: String): Int? {
        val code = findMethodCode(methodDesc) ?: return null
        val decoded = decodeMethod(code)
        if (decoded.insns.isEmpty()) return null

        val targetStringIndices = HashSet<Int>()
        for (idx in decoded.insns.indices) {
            val insn = decoded.insns[idx]
            if ((insn.op == 0x1a || insn.op == 0x1b) && code.dex.getString(insn.refIndex) == targetString) {
                targetStringIndices.add(idx)
            }
        }
        if (targetStringIndices.isEmpty()) return null

        val units = code.insns
        for (insn in decoded.insns) {
            if (insn.op != 0x2b && insn.op != 0x2c) continue
            val payloadPc = insn.switchPayloadPc
            if (payloadPc !in 0 until (units.size - 1)) continue
            val ident = units[payloadPc]
            val size = units[payloadPc + 1]
            val cases = ArrayList<Pair<Int, Int>>(size.coerceAtLeast(0))
            if (ident == 0x0100 && payloadPc + 4 + 2 * size <= units.size) {
                val firstKey = (units[payloadPc + 2] and 0xFFFF) or (units[payloadPc + 3] shl 16)
                for (k in 0 until size) {
                    val off = payloadPc + 4 + 2 * k
                    val rel = (units[off] and 0xFFFF) or (units[off + 1] shl 16)
                    val targetIdx = decoded.pcToIdx[insn.pc + rel] ?: continue
                    cases.add((firstKey + k) to targetIdx)
                }
            } else if (ident == 0x0200 && payloadPc + 2 + 4 * size <= units.size) {
                for (k in 0 until size) {
                    val keyOff = payloadPc + 2 + 2 * k
                    val caseKey = (units[keyOff] and 0xFFFF) or (units[keyOff + 1] shl 16)
                    val tgtOff = payloadPc + 2 + 2 * size + 2 * k
                    val rel = (units[tgtOff] and 0xFFFF) or (units[tgtOff + 1] shl 16)
                    val targetIdx = decoded.pcToIdx[insn.pc + rel] ?: continue
                    cases.add(caseKey to targetIdx)
                }
            }
            if (cases.isEmpty()) continue

            val allCaseTargets = cases.mapTo(HashSet()) { it.second }
            for ((caseKey, startIdx) in cases) {
                val visited = BooleanArray(decoded.insns.size)
                val q = ArrayDeque<Int>()
                q.addLast(startIdx)
                visited[startIdx] = true
                while (q.isNotEmpty()) {
                    val cur = q.removeFirst()
                    if (cur in targetStringIndices) return caseKey
                    for (succ in decoded.succs[cur]) {
                        if (!visited[succ] && (succ !in allCaseTargets || succ == startIdx)) {
                            visited[succ] = true
                            q.addLast(succ)
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Maps constructor parameter indices (1-based: 1 = param 0, 2 = param 1, ...) and literal
     * integer constants directly written to instance fields of `this` inside [ctorMethodDesc].
     */
    fun findConstructorParamFieldWrites(ctorMethodDesc: String): CtorFieldMapping? {
        val code = findMethodCode(ctorMethodDesc) ?: return null
        val decoded = decodeMethod(code)
        if (decoded.insns.isEmpty()) return null

        val states = propagateStates(code, decoded, trackEntryParams = true)
        val paramToField = linkedMapOf<Int, String>()
        val intConstToField = linkedMapOf<String, Int>()

        for (idx in decoded.insns.indices) {
            val insn = decoded.insns[idx]
            if (insn.op !in 0x59..0x5f) continue // iput*
            val inSrc = states.inSource[idx] ?: continue
            val inCst = states.inConst[idx] ?: continue
            val objReg = insn.vB
            if (objReg !in inSrc.indices || inSrc[objReg] != 0) continue

            val valReg = insn.vA
            if (valReg !in inSrc.indices) continue
            val fieldDesc = code.dex.getFieldDesc(insn.refIndex) ?: continue

            val srcParam = inSrc[valReg]
            if (srcParam >= 1 && !paramToField.containsKey(srcParam)) {
                paramToField[srcParam] = fieldDesc
            }
            val cst = inCst[valReg]
            if (cst != VAL_UNSET && cst != VAL_TOP && !intConstToField.containsKey(fieldDesc)) {
                intConstToField[fieldDesc] = cst.toInt()
            }
        }
        return CtorFieldMapping(paramToField = paramToField, intConstToField = intConstToField)
    }

    /**
     * Extracts exact static field descriptors in `<clinit>` for enum entries constructed with
     * a literal string argument (e.g. `windowBackground`, `boxBackground`, `boxPressedBackground`).
     */
    fun findStaticEnumFieldsByString(clinitMethodDesc: String): Map<String, String> {
        val code = findMethodCode(clinitMethodDesc) ?: return emptyMap()
        val decoded = decodeMethod(code)
        if (decoded.insns.isEmpty()) return emptyMap()

        val result = linkedMapOf<String, String>()
        val regString = arrayOfNulls<String>(code.registersSize)
        val regEnumLabel = arrayOfNulls<String>(code.registersSize)

        for (insn in decoded.insns) {
            when (insn.op) {
                0x1a, 0x1b -> { // const-string, const-string/jumbo
                    if (insn.vA in regString.indices) {
                        regString[insn.vA] = code.dex.getString(insn.refIndex)
                        regEnumLabel[insn.vA] = null
                    }
                }
                0x07, 0x08, 0x09 -> { // move-object*
                    val dst = insn.vA
                    val src = insn.vB
                    if (dst in regString.indices && src in regString.indices) {
                        regString[dst] = regString[src]
                        regEnumLabel[dst] = regEnumLabel[src]
                    }
                }
                0x1f -> { // check-cast preserves identity
                }
                0x70, 0x76 -> { // invoke-direct /range (constructor call)
                    val mDesc = code.dex.getMethodDesc(insn.refIndex)
                    if (mDesc != null && mDesc.contains("-><init>(") && insn.invokeArgs.isNotEmpty()) {
                        val recv = insn.invokeArgs[0]
                        val matchedLabel = insn.invokeArgs.drop(1).firstNotNullOfOrNull { r ->
                            regString.getOrNull(r)
                        }
                        if (matchedLabel != null && recv in regEnumLabel.indices) {
                            regEnumLabel[recv] = matchedLabel
                        }
                    }
                }
                0x69 -> { // sput-object
                    val label = regEnumLabel.getOrNull(insn.vA)
                    val fldDesc = code.dex.getFieldDesc(insn.refIndex)
                    if (label != null && fldDesc != null && !result.containsKey(label)) {
                        result[label] = fldDesc
                    }
                }
                else -> {
                    clearWrittenRegs(insn, regString, regEnumLabel)
                }
            }
        }
        return result
    }

    /**
     * Finds the instance field descriptor whose value (`iget-object`) acts as the `this` receiver
     * when invoking [calleeMethodDesc] inside [callerMethodDesc].
     */
    fun findFieldReceiverForInvoke(
        callerMethodDesc: String,
        calleeMethodDesc: String
    ): String? {
        val code = findMethodCode(callerMethodDesc) ?: return null
        val decoded = decodeMethod(code)
        if (decoded.insns.isEmpty()) return null

        val regField = arrayOfNulls<String>(code.registersSize)
        for (insn in decoded.insns) {
            when {
                insn.op in 0x52..0x58 -> { // iget*
                    if (insn.vA in regField.indices) {
                        regField[insn.vA] = code.dex.getFieldDesc(insn.refIndex)
                    }
                }
                insn.op in 0x07..0x09 -> { // move-object*
                    if (insn.vA in regField.indices && insn.vB in regField.indices) {
                        regField[insn.vA] = regField[insn.vB]
                    }
                }
                insn.op == 0x1f -> { // check-cast keeps underlying field origin
                }
                insn.isInvoke -> {
                    val invoked = code.dex.getMethodDesc(insn.refIndex)
                    if (invoked == calleeMethodDesc && insn.invokeArgs.isNotEmpty()) {
                        val recvField = regField.getOrNull(insn.invokeArgs[0])
                        if (recvField != null) return recvField
                    }
                }
                else -> {
                    clearWrittenRegs(insn, regField, null)
                }
            }
        }
        return null
    }

    /**
     * Finds the instance field descriptor whose `Object` value (`iget-object`) is `check-cast`
     * to [targetTypeDesc] inside [methodDesc], optionally filtered to [candidateFieldDescs].
     */
    fun findFieldCastToType(
        methodDesc: String,
        targetTypeDesc: String,
        candidateFieldDescs: Set<String>? = null
    ): String? {
        val code = findMethodCode(methodDesc) ?: return null
        val decoded = decodeMethod(code)
        if (decoded.insns.isEmpty()) return null

        val regField = arrayOfNulls<String>(code.registersSize)
        for (insn in decoded.insns) {
            when {
                insn.op in 0x52..0x58 -> { // iget*
                    if (insn.vA in regField.indices) {
                        regField[insn.vA] = code.dex.getFieldDesc(insn.refIndex)
                    }
                }
                insn.op in 0x07..0x09 -> { // move-object*
                    if (insn.vA in regField.indices && insn.vB in regField.indices) {
                        regField[insn.vA] = regField[insn.vB]
                    }
                }
                insn.op == 0x1f -> { // check-cast
                    if (code.dex.getType(insn.refIndex) == targetTypeDesc) {
                        val fDesc = regField.getOrNull(insn.vA)
                        if (fDesc != null && (candidateFieldDescs == null || fDesc in candidateFieldDescs)) {
                            return fDesc
                        }
                    }
                }
                else -> {
                    clearWrittenRegs(insn, regField, null)
                }
            }
        }
        return null
    }

    /**
     * Resolves exact `boxBg`, `boxPressedBg`, `textPrimary`, and `textSecondary` fields in
     * FX UI theme constructor [ctorMethodDesc] using color enum slots and palette color constants.
     */
    fun findUiThemeFieldMapping(
        ctorMethodDesc: String,
        boxBgEnumDesc: String?,
        boxPressedEnumDesc: String?
    ): UiThemeCtorFields? {
        val code = findMethodCode(ctorMethodDesc) ?: return null
        val decoded = decodeMethod(code)
        if (decoded.insns.isEmpty()) return null

        val thisReg = (code.registersSize - code.insSize).coerceAtLeast(0)
        val states = propagateStates(code, decoded, trackEntryParams = true)

        var boxBgFieldDesc: String? = null
        var boxPressedBgFieldDesc: String? = null
        var textPrimaryFieldDesc: String? = null
        var textSecondaryFieldDesc: String? = null

        val enumSlotTag = IntArray(code.registersSize) // 0=none, 1=boxBgEnum, 2=boxPressedEnum, 3=boxBgVal, 4=boxPressedVal
        var pendingInvokeTag = 0
        var sawPrimaryConstDist = Int.MAX_VALUE
        var sawSecondaryConstDist = Int.MAX_VALUE

        for (idx in decoded.insns.indices) {
            val insn = decoded.insns[idx]
            if (sawPrimaryConstDist < 100) sawPrimaryConstDist++
            if (sawSecondaryConstDist < 100) sawSecondaryConstDist++

            if (insn.op !in 0x0a..0x0c) {
                pendingInvokeTag = 0
            }

            when (insn.op) {
                0x62 -> { // sget-object
                    val fld = code.dex.getFieldDesc(insn.refIndex)
                    if (insn.vA in enumSlotTag.indices) {
                        enumSlotTag[insn.vA] = when {
                            boxBgEnumDesc != null && fld == boxBgEnumDesc -> 1
                            boxPressedEnumDesc != null && fld == boxPressedEnumDesc -> 2
                            else -> 0
                        }
                    }
                }
                0x01, 0x02, 0x03, 0x07, 0x08, 0x09 -> {
                    if (insn.vA in enumSlotTag.indices && insn.vB in enumSlotTag.indices) {
                        enumSlotTag[insn.vA] = enumSlotTag[insn.vB]
                    }
                }
                0x0a -> { // move-result
                    if (insn.vA in enumSlotTag.indices) {
                        enumSlotTag[insn.vA] = if (pendingInvokeTag == 3 || pendingInvokeTag == 4) pendingInvokeTag else 0
                    }
                    pendingInvokeTag = 0
                }
                0x14, 0x15 -> { // const, const/high16
                    if (insn.constInt == UI_TEXT_PRIMARY_DARK_ON_LIGHT) {
                        sawPrimaryConstDist = 0
                    } else if (insn.constInt == UI_TEXT_SECONDARY_DARK_ON_LIGHT) {
                        sawSecondaryConstDist = 0
                    }
                    if (insn.vA in enumSlotTag.indices) enumSlotTag[insn.vA] = 0
                }
                0x59 -> { // iput (32-bit int field write)
                    val inSrc = states.inSource[idx]
                    val isThisWrite = (inSrc != null && insn.vB in inSrc.indices && inSrc[insn.vB] == 0) || insn.vB == thisReg
                    if (isThisWrite) {
                        val fieldDesc = code.dex.getFieldDesc(insn.refIndex)
                        if (fieldDesc != null && fieldDesc.endsWith(":I")) {
                            val tag = enumSlotTag.getOrNull(insn.vA) ?: 0
                            if (tag == 3 && boxBgFieldDesc == null) {
                                boxBgFieldDesc = fieldDesc
                            } else if (tag == 4 && boxPressedBgFieldDesc == null) {
                                boxPressedBgFieldDesc = fieldDesc
                            } else if (textPrimaryFieldDesc == null && sawPrimaryConstDist <= 20) {
                                textPrimaryFieldDesc = fieldDesc
                                sawPrimaryConstDist = Int.MAX_VALUE
                            } else if (textSecondaryFieldDesc == null && sawSecondaryConstDist <= 20 && fieldDesc != textPrimaryFieldDesc) {
                                textSecondaryFieldDesc = fieldDesc
                                sawSecondaryConstDist = Int.MAX_VALUE
                            }
                        }
                    }
                }
                else -> {
                    if (insn.isInvoke) {
                        val mDesc = code.dex.getMethodDesc(insn.refIndex)
                        if (mDesc != null && mDesc.endsWith(")I")) {
                            var foundTag = 0
                            for (r in insn.invokeArgs) {
                                val t = enumSlotTag.getOrNull(r) ?: 0
                                if (t == 1) {
                                    foundTag = 3
                                    break
                                } else if (t == 2) {
                                    foundTag = 4
                                    break
                                }
                            }
                            pendingInvokeTag = foundTag
                        }
                    } else {
                        clearWrittenRegInt(insn, enumSlotTag)
                    }
                }
            }
        }

        return UiThemeCtorFields(
            boxBgFieldDesc = boxBgFieldDesc,
            boxPressedBgFieldDesc = boxPressedBgFieldDesc,
            textPrimaryFieldDesc = textPrimaryFieldDesc,
            textSecondaryFieldDesc = textSecondaryFieldDesc
        )
    }

    /**
     * Disambiguates `TextViewerActivity` `unsupported` vs `truncated` booleans in [applyMethodDesc]
     * (`u0()` in 9012, `pg.f0.run()` in 9108) where `unsupported` is read immediately before the
     * 2-arg `getString(int, Object[])` format warning.
     */
    fun findTextViewerUnsupportedAndTruncatedFields(
        applyMethodDesc: String,
        textViewerClassDesc: String,
        candidateFieldDescs: Set<String>
    ): Pair<String?, String?> {
        val code = findMethodCode(applyMethodDesc) ?: return (null to null)
        val decoded = decodeMethod(code)
        if (decoded.insns.isEmpty()) return (null to null)

        val readsInOrder = mutableListOf<Pair<Int, String>>()
        var twoArgGetStringIdx = -1
        for (idx in decoded.insns.indices) {
            val insn = decoded.insns[idx]
            if (insn.op == 0x55) { // iget-boolean
                val fDesc = code.dex.getFieldDesc(insn.refIndex)
                if (fDesc != null && (candidateFieldDescs.contains(fDesc) ||
                        (fDesc.startsWith("$textViewerClassDesc->") && fDesc.endsWith(":Z")))
                ) {
                    readsInOrder.add(idx to fDesc)
                }
            } else if (insn.isInvoke && twoArgGetStringIdx < 0) {
                val mDesc = code.dex.getMethodDesc(insn.refIndex)
                if (mDesc != null && mDesc.endsWith("->getString(I[Ljava/lang/Object;)Ljava/lang/String;")) {
                    twoArgGetStringIdx = idx
                }
            }
        }
        if (twoArgGetStringIdx < 0) return (null to null)

        val unsupportedDesc = readsInOrder
            .lastOrNull { it.first < twoArgGetStringIdx && candidateFieldDescs.contains(it.second) }
            ?.second
            ?: readsInOrder.lastOrNull { it.first < twoArgGetStringIdx }?.second

        val truncatedDesc = readsInOrder
            .firstOrNull { it.first > twoArgGetStringIdx && it.second != unsupportedDesc && candidateFieldDescs.contains(it.second) }
            ?.second
            ?: candidateFieldDescs.firstOrNull { it != unsupportedDesc }

        return unsupportedDesc to truncatedDesc
    }

    // ---- Internal DEX parser & Dalvik instruction decoder ----

    private fun findMethodCode(methodDesc: String): MethodCode? {
        val classDesc = methodDesc.substringBefore("->")
        for (dex in dexFiles) {
            val code = dex.findMethodCode(classDesc, methodDesc)
            if (code != null) return code
        }
        return null
    }

    private class DecodedInsn(
        val pc: Int,
        val op: Int,
        val width: Int,
        val vA: Int = -1,
        val vB: Int = -1,
        val refIndex: Int = -1,
        val constInt: Int = 0,
        val isConstInt: Boolean = false,
        val isMove32: Boolean = false,
        val isInvoke: Boolean = false,
        val invokeArgs: IntArray = EMPTY_INT_ARRAY,
        val jumpRel: Int = 0,
        val switchPayloadPc: Int = -1,
        val dstReg: Int = -1,
        val dstIsWide: Boolean = false
    ) {
        companion object {
            val EMPTY_INT_ARRAY = IntArray(0)
        }
    }

    private class DecodedMethod(
        val insns: List<DecodedInsn>,
        val pcToIdx: Map<Int, Int>,
        val succs: Array<IntArray>
    )

    private class PropagatedStates(
        val inConst: Array<LongArray?>,
        val inSource: Array<IntArray?>
    )

    private fun decodeMethod(code: MethodCode): DecodedMethod {
        val units = code.insns
        val len = units.size
        val list = ArrayList<DecodedInsn>(len / 2 + 4)
        val pcToIdx = HashMap<Int, Int>(len)
        var pc = 0
        while (pc < len) {
            val u0 = units[pc]
            if (u0 == 0x0100 || u0 == 0x0200 || u0 == 0x0300) {
                val payloadWidth = when (u0) {
                    0x0100 -> if (pc + 1 < len) units[pc + 1] * 2 + 4 else 1
                    0x0200 -> if (pc + 1 < len) units[pc + 1] * 4 + 2 else 1
                    else -> if (pc + 3 < len) {
                        val ew = units[pc + 1]
                        val sz = (units[pc + 2] and 0xFFFF) or (units[pc + 3] shl 16)
                        ((sz.toLong() * ew + 1L) / 2L + 4L).toInt().coerceAtLeast(1)
                    } else 1
                }.coerceAtLeast(1)
                pc += payloadWidth
                continue
            }

            val op = u0 and 0xFF
            val width = dalvikInsnWidth(op)
            if (pc + width > len) break
            val u1 = if (width >= 2) units[pc + 1] else 0
            val u2 = if (width >= 3) units[pc + 2] else 0

            val insn = when (op) {
                0x01, 0x07 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xF, vB = (u0 ushr 12) and 0xF,
                    isMove32 = true, dstReg = (u0 ushr 8) and 0xF
                )
                0x02, 0x08 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xFF, vB = u1,
                    isMove32 = true, dstReg = (u0 ushr 8) and 0xFF
                )
                0x03, 0x09 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = u1, vB = u2,
                    isMove32 = true, dstReg = u1
                )
                0x04 -> DecodedInsn(pc = pc, op = op, width = width, dstReg = (u0 ushr 8) and 0xF, dstIsWide = true)
                0x05 -> DecodedInsn(pc = pc, op = op, width = width, dstReg = (u0 ushr 8) and 0xFF, dstIsWide = true)
                0x06 -> DecodedInsn(pc = pc, op = op, width = width, dstReg = u1, dstIsWide = true)
                0x0a, 0x0c, 0x0d -> DecodedInsn(pc = pc, op = op, width = width, vA = (u0 ushr 8) and 0xFF, dstReg = (u0 ushr 8) and 0xFF)
                0x0b -> DecodedInsn(pc = pc, op = op, width = width, vA = (u0 ushr 8) and 0xFF, dstReg = (u0 ushr 8) and 0xFF, dstIsWide = true)
                0x12 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xF,
                    constInt = (u0 shl 16) shr 28,
                    isConstInt = true,
                    dstReg = (u0 ushr 8) and 0xF
                )
                0x13 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xFF,
                    constInt = u1.toShort().toInt(),
                    isConstInt = true,
                    dstReg = (u0 ushr 8) and 0xFF
                )
                0x14 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xFF,
                    constInt = (u1 and 0xFFFF) or (u2 shl 16),
                    isConstInt = true,
                    dstReg = (u0 ushr 8) and 0xFF
                )
                0x15 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xFF,
                    constInt = u1 shl 16,
                    isConstInt = true,
                    dstReg = (u0 ushr 8) and 0xFF
                )
                0x16, 0x17, 0x18, 0x19 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xFF,
                    dstReg = (u0 ushr 8) and 0xFF,
                    dstIsWide = true
                )
                0x1a, 0x1c, 0x22, 0xfe, 0xff -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xFF,
                    refIndex = u1,
                    dstReg = (u0 ushr 8) and 0xFF
                )
                0x1b -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xFF,
                    refIndex = (u1 and 0xFFFF) or (u2 shl 16),
                    dstReg = (u0 ushr 8) and 0xFF
                )
                0x1f -> DecodedInsn( // check-cast (validates in place)
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xFF,
                    refIndex = u1
                )
                0x20, 0x23 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xF, vB = (u0 ushr 12) and 0xF,
                    refIndex = u1, dstReg = (u0 ushr 8) and 0xF
                )
                0x21 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xF, vB = (u0 ushr 12) and 0xF,
                    dstReg = (u0 ushr 8) and 0xF
                )
                0x28 -> DecodedInsn(pc = pc, op = op, width = width, jumpRel = (u0 shl 16) shr 24)
                0x29 -> DecodedInsn(pc = pc, op = op, width = width, jumpRel = u1.toShort().toInt())
                0x2a -> DecodedInsn(pc = pc, op = op, width = width, jumpRel = (u1 and 0xFFFF) or (u2 shl 16))
                0x2b, 0x2c -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    switchPayloadPc = pc + ((u1 and 0xFFFF) or (u2 shl 16))
                )
                in 0x2d..0x31 -> DecodedInsn(pc = pc, op = op, width = width, dstReg = (u0 ushr 8) and 0xFF)
                in 0x32..0x3d -> DecodedInsn(pc = pc, op = op, width = width, jumpRel = u1.toShort().toInt())
                in 0x44..0x4a -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    dstReg = (u0 ushr 8) and 0xFF,
                    dstIsWide = (op == 0x45)
                )
                in 0x52..0x58 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xF, vB = (u0 ushr 12) and 0xF,
                    refIndex = u1,
                    dstReg = (u0 ushr 8) and 0xF,
                    dstIsWide = (op == 0x53)
                )
                in 0x59..0x5f -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xF, vB = (u0 ushr 12) and 0xF,
                    refIndex = u1
                )
                in 0x60..0x66 -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xFF,
                    refIndex = u1,
                    dstReg = (u0 ushr 8) and 0xFF,
                    dstIsWide = (op == 0x61)
                )
                in 0x67..0x6d -> DecodedInsn(
                    pc = pc, op = op, width = width,
                    vA = (u0 ushr 8) and 0xFF,
                    refIndex = u1
                )
                in 0x6e..0x72 -> {
                    val count = (u0 ushr 12) and 0xF
                    val args = IntArray(count.coerceAtMost(5))
                    if (args.size >= 1) args[0] = u2 and 0xF
                    if (args.size >= 2) args[1] = (u2 ushr 4) and 0xF
                    if (args.size >= 3) args[2] = (u2 ushr 8) and 0xF
                    if (args.size >= 4) args[3] = (u2 ushr 12) and 0xF
                    if (args.size >= 5) args[4] = (u0 ushr 8) and 0xF
                    DecodedInsn(
                        pc = pc, op = op, width = width,
                        refIndex = u1, isInvoke = true, invokeArgs = args
                    )
                }
                in 0x74..0x78 -> {
                    val count = (u0 ushr 8) and 0xFF
                    val args = IntArray(count) { i -> u2 + i }
                    DecodedInsn(
                        pc = pc, op = op, width = width,
                        refIndex = u1, isInvoke = true, invokeArgs = args
                    )
                }
                in 0x7b..0x8f -> {
                    val isWideTarget = op in setOf(0x7d, 0x7e, 0x7f, 0x80, 0x81, 0x83, 0x84, 0x86, 0x87, 0x89, 0x8a, 0x8c)
                    DecodedInsn(pc = pc, op = op, width = width, dstReg = (u0 ushr 8) and 0xF, dstIsWide = isWideTarget)
                }
                in 0x90..0xaf -> {
                    val isWideTarget = (op in 0x9b..0xa5) || (op in 0xab..0xaf)
                    DecodedInsn(pc = pc, op = op, width = width, dstReg = (u0 ushr 8) and 0xFF, dstIsWide = isWideTarget)
                }
                in 0xb0..0xcf -> {
                    val isWideTarget = (op in 0xbb..0xc5) || (op in 0xcb..0xcf)
                    DecodedInsn(pc = pc, op = op, width = width, dstReg = (u0 ushr 8) and 0xF, dstIsWide = isWideTarget)
                }
                in 0xd0..0xe2 -> DecodedInsn(pc = pc, op = op, width = width, dstReg = (u0 ushr 8) and 0xF)
                else -> DecodedInsn(pc = pc, op = op, width = width)
            }

            pcToIdx[pc] = list.size
            list.add(insn)
            pc += width
        }

        val succs = Array(list.size) { idx ->
            val insn = list[idx]
            val targets = ArrayList<Int>(4)
            when (insn.op) {
                0x0e, 0x0f, 0x10, 0x11, 0x27 -> Unit // return* / throw
                0x28, 0x29, 0x2a -> { // goto*
                    pcToIdx[insn.pc + insn.jumpRel]?.let { targets.add(it) }
                }
                in 0x32..0x3d -> { // if-*
                    if (idx + 1 < list.size) targets.add(idx + 1)
                    pcToIdx[insn.pc + insn.jumpRel]?.let { if (!targets.contains(it)) targets.add(it) }
                }
                0x2b, 0x2c -> { // packed-switch / sparse-switch
                    if (idx + 1 < list.size) targets.add(idx + 1)
                    val payloadPc = insn.switchPayloadPc
                    if (payloadPc in 0 until (len - 1)) {
                        val ident = units[payloadPc]
                        val size = units[payloadPc + 1]
                        val tableStart = if (ident == 0x0100) payloadPc + 4 else if (ident == 0x0200) payloadPc + 2 + 2 * size else -1
                        if (tableStart >= 0) {
                            for (k in 0 until size) {
                                val off = tableStart + 2 * k
                                if (off + 1 >= len) break
                                val rel = (units[off] and 0xFFFF) or (units[off + 1] shl 16)
                                pcToIdx[insn.pc + rel]?.let { if (!targets.contains(it)) targets.add(it) }
                            }
                        }
                    }
                }
                else -> {
                    if (idx + 1 < list.size) targets.add(idx + 1)
                }
            }
            targets.toIntArray()
        }

        return DecodedMethod(list, pcToIdx, succs)
    }

    private fun propagateStates(
        code: MethodCode,
        decoded: DecodedMethod,
        trackEntryParams: Boolean
    ): PropagatedStates {
        val count = decoded.insns.size
        val regs = code.registersSize.coerceAtLeast(1)
        val inConst = arrayOfNulls<LongArray>(count)
        val inSource = arrayOfNulls<IntArray>(count)

        inConst[0] = LongArray(regs) { VAL_TOP }
        val entrySrc = IntArray(regs) { SRC_TOP }
        if (trackEntryParams) {
            val firstParamReg = (code.registersSize - code.insSize).coerceAtLeast(0)
            if (firstParamReg < regs) {
                entrySrc[firstParamReg] = 0 // this
                val paramDescs = parseParamDescriptors(code.methodDesc)
                var regCursor = firstParamReg + 1
                for ((pIdx, pDesc) in paramDescs.withIndex()) {
                    if (regCursor < regs) {
                        entrySrc[regCursor] = pIdx + 1
                    }
                    regCursor += if (pDesc == "J" || pDesc == "D") 2 else 1
                }
            }
        }
        inSource[0] = entrySrc

        val queue = ArrayDeque<Int>(count.coerceAtLeast(16))
        val inQueue = BooleanArray(count)
        queue.addLast(0)
        inQueue[0] = true

        val maxSteps = (count * 12 + 256).coerceAtMost(65536)
        var steps = 0
        while (queue.isNotEmpty() && steps++ < maxSteps) {
            val idx = queue.removeFirst()
            inQueue[idx] = false
            val insn = decoded.insns[idx]

            val outC = inConst[idx]!!.copyOf()
            val outS = inSource[idx]!!.copyOf()

            when {
                insn.isConstInt -> {
                    if (insn.vA in outC.indices) {
                        outC[insn.vA] = (1L shl 32) or (insn.constInt.toLong() and 0xFFFFFFFFL)
                        outS[insn.vA] = SRC_TOP
                    }
                }
                insn.isMove32 -> {
                    if (insn.vA in outC.indices && insn.vB in outC.indices) {
                        outC[insn.vA] = outC[insn.vB]
                        outS[insn.vA] = outS[insn.vB]
                    }
                }
                insn.dstReg >= 0 -> {
                    if (insn.dstReg in outC.indices) {
                        outC[insn.dstReg] = VAL_TOP
                        outS[insn.dstReg] = SRC_TOP
                    }
                    if (insn.dstIsWide && (insn.dstReg + 1) in outC.indices) {
                        outC[insn.dstReg + 1] = VAL_TOP
                        outS[insn.dstReg + 1] = SRC_TOP
                    }
                }
            }

            for (succ in decoded.succs[idx]) {
                val prevC = inConst[succ]
                val prevS = inSource[succ]
                var changed = false
                if (prevC == null || prevS == null) {
                    inConst[succ] = outC.copyOf()
                    inSource[succ] = outS.copyOf()
                    changed = true
                } else {
                    for (r in 0 until regs) {
                        val vc = outC[r]
                        if (vc != VAL_UNSET && prevC[r] != vc && prevC[r] != VAL_TOP) {
                            prevC[r] = if (prevC[r] == VAL_UNSET) vc else VAL_TOP
                            changed = true
                        }
                        val vs = outS[r]
                        if (vs != SRC_UNSET && prevS[r] != vs && prevS[r] != SRC_TOP) {
                            prevS[r] = if (prevS[r] == SRC_UNSET) vs else SRC_TOP
                            changed = true
                        }
                    }
                }
                if (changed && !inQueue[succ]) {
                    queue.addLast(succ)
                    inQueue[succ] = true
                }
            }
        }
        return PropagatedStates(inConst, inSource)
    }

    private fun backwardFindConstInt(decoded: DecodedMethod, invokeIdx: Int, targetReg: Int): Int? {
        var tracked = targetReg
        for (i in (invokeIdx - 1) downTo 0) {
            val insn = decoded.insns[i]
            if (insn.isConstInt && insn.vA == tracked) return insn.constInt
            if (insn.isMove32 && insn.vA == tracked) {
                tracked = insn.vB
                continue
            }
            if (insn.dstReg == tracked || (insn.dstIsWide && insn.dstReg + 1 == tracked)) {
                return null
            }
        }
        return null
    }

    private fun computeInvokeArgWordSlot(calleeMethodDesc: String, invokeArgIndex: Int): Int {
        if (invokeArgIndex <= 0) return 0
        val params = parseParamDescriptors(calleeMethodDesc)
        var wordSlot = 1 // 0 is `this` for instance/ctor invokes
        for (pIdx in 0 until (invokeArgIndex - 1)) {
            val pDesc = params.getOrNull(pIdx)
            wordSlot += if (pDesc == "J" || pDesc == "D") 2 else 1
        }
        return wordSlot
    }

    private fun parseParamDescriptors(methodDesc: String): List<String> {
        val open = methodDesc.indexOf('(')
        val close = methodDesc.indexOf(')', open + 1)
        if (open < 0 || close <= open + 1) return emptyList()
        val sig = methodDesc.substring(open + 1, close)
        val out = ArrayList<String>()
        var i = 0
        while (i < sig.length) {
            val start = i
            while (i < sig.length && sig[i] == '[') i++
            if (i >= sig.length) break
            if (sig[i] == 'L') {
                val semi = sig.indexOf(';', i + 1)
                i = if (semi >= 0) semi + 1 else sig.length
            } else {
                i++
            }
            out.add(sig.substring(start, i))
        }
        return out
    }

    private fun clearWrittenRegs(insn: DecodedInsn, arr1: Array<String?>, arr2: Array<String?>?) {
        if (insn.dstReg >= 0) {
            if (insn.dstReg in arr1.indices) {
                arr1[insn.dstReg] = null
                arr2?.set(insn.dstReg, null)
            }
            if (insn.dstIsWide && (insn.dstReg + 1) in arr1.indices) {
                arr1[insn.dstReg + 1] = null
                arr2?.set(insn.dstReg + 1, null)
            }
        }
    }

    private fun clearWrittenRegInt(insn: DecodedInsn, arr: IntArray) {
        if (insn.dstReg >= 0) {
            if (insn.dstReg in arr.indices) arr[insn.dstReg] = 0
            if (insn.dstIsWide && (insn.dstReg + 1) in arr.indices) arr[insn.dstReg + 1] = 0
        }
    }

    private fun dalvikInsnWidth(op: Int): Int = when (op) {
        0x00, 0x01, 0x04, 0x07, in 0x0a..0x12, 0x1d, 0x1e, 0x21, 0x27, 0x28,
        in 0x3e..0x43, 0x73, 0x79, 0x7a, in 0x7b..0x8f, in 0xb0..0xcf, in 0xe3..0xf9 -> 1
        0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23,
        0x29, in 0x2d..0x3d, in 0x44..0x6d, in 0x90..0xaf, in 0xd0..0xe2, 0xfe, 0xff -> 2
        0x03, 0x06, 0x09, 0x14, 0x17, 0x1b, 0x24, 0x25, 0x26, 0x2a, 0x2b, 0x2c,
        in 0x6e..0x72, in 0x74..0x78, 0xfc, 0xfd -> 3
        0xfa, 0xfb -> 4
        0x18 -> 5
        else -> 1
    }

    private class MethodCode(
        val dex: ParsedDex,
        val methodDesc: String,
        val registersSize: Int,
        val insSize: Int,
        val triesSize: Int,
        val insns: IntArray
    )

    private class ParsedDex(
        private val bytes: ByteArray,
        private val stringIdsSize: Int,
        private val stringIdsOff: Int,
        private val typeIdsSize: Int,
        private val typeIdsOff: Int,
        private val protoIdsSize: Int,
        private val protoIdsOff: Int,
        private val fieldIdsSize: Int,
        private val fieldIdsOff: Int,
        private val methodIdsSize: Int,
        private val methodIdsOff: Int,
        private val classDataOffByDesc: Map<String, Int>
    ) {
        private val stringCache = arrayOfNulls<String>(stringIdsSize)
        private val typeCache = arrayOfNulls<String>(typeIdsSize)
        private val protoCache = arrayOfNulls<String>(protoIdsSize)
        private val fieldCache = arrayOfNulls<String>(fieldIdsSize)
        private val methodCache = arrayOfNulls<String>(methodIdsSize)

        companion object {
            fun parseOrNull(bytes: ByteArray): ParsedDex? {
                if (bytes.size < 0x70) return null
                // Validate "dex\n" magic
                if (bytes[0] != 0x64.toByte() || bytes[1] != 0x65.toByte() ||
                    bytes[2] != 0x78.toByte() || bytes[3] != 0x0a.toByte()
                ) {
                    return null
                }
                val stringIdsSize = s32(bytes, 0x38)
                val stringIdsOff = s32(bytes, 0x3c)
                val typeIdsSize = s32(bytes, 0x40)
                val typeIdsOff = s32(bytes, 0x44)
                val protoIdsSize = s32(bytes, 0x48)
                val protoIdsOff = s32(bytes, 0x4c)
                val fieldIdsSize = s32(bytes, 0x50)
                val fieldIdsOff = s32(bytes, 0x54)
                val methodIdsSize = s32(bytes, 0x58)
                val methodIdsOff = s32(bytes, 0x5c)
                val classDefsSize = s32(bytes, 0x60)
                val classDefsOff = s32(bytes, 0x64)
                if (stringIdsSize < 0 || typeIdsSize < 0 || classDefsSize < 0) return null

                val tmpStrings = arrayOfNulls<String>(stringIdsSize)
                val classDataOffByDesc = HashMap<String, Int>(classDefsSize * 2)
                for (i in 0 until classDefsSize) {
                    val defOff = classDefsOff + i * 32
                    if (defOff + 32 > bytes.size) break
                    val classIdx = s32(bytes, defOff)
                    val classDataOff = s32(bytes, defOff + 24)
                    if (classIdx in 0 until typeIdsSize && classDataOff > 0 && classDataOff < bytes.size) {
                        val strIdx = s32(bytes, typeIdsOff + classIdx * 4)
                        val desc = decodeStringAt(bytes, stringIdsSize, stringIdsOff, tmpStrings, strIdx)
                        if (desc != null) {
                            classDataOffByDesc[desc] = classDataOff
                        }
                    }
                }

                return ParsedDex(
                    bytes = bytes,
                    stringIdsSize = stringIdsSize,
                    stringIdsOff = stringIdsOff,
                    typeIdsSize = typeIdsSize,
                    typeIdsOff = typeIdsOff,
                    protoIdsSize = protoIdsSize,
                    protoIdsOff = protoIdsOff,
                    fieldIdsSize = fieldIdsSize,
                    fieldIdsOff = fieldIdsOff,
                    methodIdsSize = methodIdsSize,
                    methodIdsOff = methodIdsOff,
                    classDataOffByDesc = classDataOffByDesc
                ).also {
                    System.arraycopy(tmpStrings, 0, it.stringCache, 0, tmpStrings.size)
                }
            }

            private fun s32(b: ByteArray, off: Int): Int {
                return (b[off].toInt() and 0xFF) or
                    ((b[off + 1].toInt() and 0xFF) shl 8) or
                    ((b[off + 2].toInt() and 0xFF) shl 16) or
                    ((b[off + 3].toInt() and 0xFF) shl 24)
            }

            private fun u16(b: ByteArray, off: Int): Int {
                return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
            }

            private fun decodeStringAt(
                bytes: ByteArray,
                stringIdsSize: Int,
                stringIdsOff: Int,
                cache: Array<String?>,
                idx: Int
            ): String? {
                if (idx !in 0 until stringIdsSize) return null
                cache[idx]?.let { return it }
                val dataOff = s32(bytes, stringIdsOff + idx * 4)
                if (dataOff < 0 || dataOff >= bytes.size) return null
                var p = dataOff
                // Skip UTF-16 length ULEB128
                while (p < bytes.size && (bytes[p++].toInt() and 0x80) != 0) {
                    // continue
                }
                val sb = StringBuilder()
                while (p < bytes.size) {
                    val a = bytes[p++].toInt() and 0xFF
                    if (a == 0) break
                    if (a < 0x80) {
                        sb.append(a.toChar())
                    } else if ((a and 0xE0) == 0xC0 && p < bytes.size) {
                        val b = bytes[p++].toInt() and 0xFF
                        sb.append((((a and 0x1F) shl 6) or (b and 0x3F)).toChar())
                    } else if ((a and 0xF0) == 0xE0 && p + 1 < bytes.size) {
                        val b = bytes[p++].toInt() and 0xFF
                        val c = bytes[p++].toInt() and 0xFF
                        sb.append((((a and 0x0F) shl 12) or ((b and 0x3F) shl 6) or (c and 0x3F)).toChar())
                    } else {
                        sb.append('?')
                    }
                }
                val str = sb.toString()
                cache[idx] = str
                return str
            }
        }

        fun getString(idx: Int): String? =
            decodeStringAt(bytes, stringIdsSize, stringIdsOff, stringCache, idx)

        fun getType(idx: Int): String? {
            if (idx !in 0 until typeIdsSize) return null
            typeCache[idx]?.let { return it }
            val strIdx = s32(bytes, typeIdsOff + idx * 4)
            return getString(strIdx)?.also { typeCache[idx] = it }
        }

        private fun getProto(idx: Int): String? {
            if (idx !in 0 until protoIdsSize) return null
            protoCache[idx]?.let { return it }
            val off = protoIdsOff + idx * 12
            val retType = getType(s32(bytes, off + 4)) ?: return null
            val paramsOff = s32(bytes, off + 8)
            val sb = StringBuilder("(")
            if (paramsOff > 0 && paramsOff + 4 <= bytes.size) {
                val pCount = s32(bytes, paramsOff)
                for (i in 0 until pCount) {
                    val tOff = paramsOff + 4 + i * 2
                    if (tOff + 2 > bytes.size) break
                    val pType = getType(u16(bytes, tOff)) ?: return null
                    sb.append(pType)
                }
            }
            sb.append(')').append(retType)
            return sb.toString().also { protoCache[idx] = it }
        }

        fun getFieldDesc(idx: Int): String? {
            if (idx !in 0 until fieldIdsSize) return null
            fieldCache[idx]?.let { return it }
            val off = fieldIdsOff + idx * 8
            val cls = getType(u16(bytes, off)) ?: return null
            val type = getType(u16(bytes, off + 2)) ?: return null
            val name = getString(s32(bytes, off + 4)) ?: return null
            return "$cls->$name:$type".also { fieldCache[idx] = it }
        }

        fun getMethodDesc(idx: Int): String? {
            if (idx !in 0 until methodIdsSize) return null
            methodCache[idx]?.let { return it }
            val off = methodIdsOff + idx * 8
            val cls = getType(u16(bytes, off)) ?: return null
            val proto = getProto(u16(bytes, off + 2)) ?: return null
            val name = getString(s32(bytes, off + 4)) ?: return null
            return "$cls->$name$proto".also { methodCache[idx] = it }
        }

        fun findMethodCode(classDesc: String, methodDesc: String): MethodCode? {
            val dataOff = classDataOffByDesc[classDesc] ?: return null
            val pos = intArrayOf(dataOff)
            val staticFieldsSize = readUleb128(pos)
            val instanceFieldsSize = readUleb128(pos)
            val directMethodsSize = readUleb128(pos)
            val virtualMethodsSize = readUleb128(pos)

            for (i in 0 until staticFieldsSize) {
                readUleb128(pos)
                readUleb128(pos)
            }
            for (i in 0 until instanceFieldsSize) {
                readUleb128(pos)
                readUleb128(pos)
            }

            var mIdx = 0
            for (i in 0 until directMethodsSize) {
                mIdx += readUleb128(pos)
                readUleb128(pos) // access flags
                val codeOff = readUleb128(pos)
                if (codeOff > 0 && getMethodDesc(mIdx) == methodDesc) {
                    return readCodeItem(methodDesc, codeOff)
                }
            }
            mIdx = 0
            for (i in 0 until virtualMethodsSize) {
                mIdx += readUleb128(pos)
                readUleb128(pos) // access flags
                val codeOff = readUleb128(pos)
                if (codeOff > 0 && getMethodDesc(mIdx) == methodDesc) {
                    return readCodeItem(methodDesc, codeOff)
                }
            }
            return null
        }

        private fun readCodeItem(methodDesc: String, codeOff: Int): MethodCode? {
            if (codeOff < 0 || codeOff + 16 > bytes.size) return null
            val registersSize = u16(bytes, codeOff)
            val insSize = u16(bytes, codeOff + 2)
            val triesSize = u16(bytes, codeOff + 6)
            val insnsSize = s32(bytes, codeOff + 12)
            if (insnsSize < 0 || codeOff + 16 + insnsSize * 2 > bytes.size) return null
            val insns = IntArray(insnsSize) { i -> u16(bytes, codeOff + 16 + i * 2) }
            return MethodCode(this, methodDesc, registersSize, insSize, triesSize, insns)
        }

        private fun readUleb128(pos: IntArray): Int {
            var p = pos[0]
            var result = 0
            var shift = 0
            while (p < bytes.size) {
                val b = bytes[p++].toInt() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            pos[0] = p
            return result
        }
    }
}
