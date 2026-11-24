package com.scluzlep.fxplusmodule.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Pure-Kotlin ZIP + DEX binary verifier ensuring every class, method descriptor,
 * and field type referenced by [FxSymbolResolver] exists verbatim inside the real
 * FX `9012` (`apk/base.apk`) and `9108` (`FX Explorer_v9.1.0.8_9108.apk`) APKs.
 */
class DualApkDexContractTest {

    @Test
    fun verify9012ContractsAgainstRealApk() {
        val apkFile = resolveRepoRelativeFile("../apk/base.apk")
        assumeTrue("FX 9012 APK missing at ${apkFile.absolutePath}; skipping in CI", apkFile.isFile)
        verifyApkContract(apkFile, FxDexContracts.contract9012, label = "9012")
    }

    @Test
    fun verify9108ContractsAgainstRealApk() {
        val apkFile = resolveRepoRelativeFile("../FX Explorer_v9.1.0.8_9108.apk")
        assumeTrue("FX 9108 APK missing at ${apkFile.absolutePath}; skipping in CI", apkFile.isFile)
        verifyApkContract(apkFile, FxDexContracts.contract9108, label = "9108")
    }

    @Test
    fun v11ContractsIncludeHardwareRenderAndFolderLifecycleSymbols() {
        val c9012 = FxDexContracts.contract9012
        val c9108 = FxDexContracts.contract9108

        assertTrue(c9012.classes.containsAll(listOf(
            "nextapp.fx.ui.ExplorerActivity",
            "nextapp.fx.ui.content.r",
            "nextapp.fx.ui.content.h0",
            "nextapp.fx.ui.content.e2",
            "nextapp.fx.ui.content.e0",
            "v8.f"
        )))
        assertTrue(c9108.classes.containsAll(listOf(
            "nextapp.fx.ui.ExplorerActivity",
            "nextapp.fx.ui.content.k",
            "nextapp.fx.ui.content.w",
            "nextapp.fx.ui.content.c1",
            "nextapp.fx.ui.content.p",
            "pa.c"
        )))
        assertTrue(c9012.methods.any { it.className == "nextapp.maui.ui.imageview.TouchImageDisplay" && it.methodName == "onDraw" })
        assertTrue(c9012.methods.any { it.className == "se.f" && it.methodName == "C" })
        assertTrue(c9108.methods.any { it.className == "nextapp.maui.ui.imageview.TouchImageDisplay" && it.methodName == "onDraw" })
        assertTrue(c9108.methods.any { it.className == "hh.f" && it.methodName == "G" })
        assertTrue(c9012.fields.any { it.className == "nextapp.fx.ui.content.r" && it.fieldName == "r5" })
        assertTrue(c9108.fields.any { it.className == "nextapp.fx.ui.content.k" && it.fieldName == "n2" })
    }

    @Test
    fun v13ContractsIncludeThemeHomeMenuAndGoogleDriveSymbols() {
        val c9012 = FxDexContracts.contract9012
        val c9108 = FxDexContracts.contract9108

        assertTrue(c9012.classes.containsAll(listOf(
            "xc.f",
            "nextapp.fx.ui.homecontent.HomeContentView",
            "nextapp.fx.ui.homecontent.HomeContentView\$d",
            "nextapp.fx.ui.content.j0",
            "le.t",
            "le.f0",
            "le.r",
            "le.b\$a",
            "nextapp.fx.ui.res.ActionIcons",
            "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity",
            "nextapp.fx.plus.dirimpl.googledrive.b\$a",
            "ic.e\$a",
            "i9.e"
        )))
        assertTrue(c9108.classes.containsAll(listOf(
            "ef.g",
            "nextapp.fx.ui.homecontent.HomeContentView",
            "be.p",
            "nextapp.fx.ui.content.b0",
            "yg.r",
            "yg.y",
            "yg.p",
            "yg.b",
            "nextapp.fx.ui.res.ActionIcons",
            "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity",
            "gg.d",
            "jh.b",
            "bb.d"
        )))

        assertTrue(c9012.methods.any { it.className == "xc.f" && it.methodName == "e" && it.isStatic })
        assertTrue(c9012.methods.any { it.className == "xc.f" && it.methodName == "P" })
        assertTrue(c9012.methods.any { it.className == "xc.f" && it.methodName == "N" })
        assertTrue(c9012.methods.any { it.className == "xc.f" && it.methodName == "O" })
        assertTrue(c9012.methods.any { it.className == "i9.e" && it.methodName == "<init>" })
        assertTrue(c9012.methods.any { it.className == "nextapp.fx.ui.homecontent.HomeContentView\$d" && it.methodName == "p" })
        assertTrue(c9012.methods.any { it.className == "nextapp.fx.plus.dirimpl.googledrive.b\$a" && it.methodName == "c" && it.isStatic })
        assertTrue(c9012.methods.any { it.className == "ic.e\$a" && it.methodName == "<init>" })

        assertTrue(c9108.methods.any { it.className == "ef.g" && it.methodName == "b" && it.isStatic })
        assertTrue(c9108.methods.any { it.className == "ef.g" && it.methodName == "w" })
        assertTrue(c9108.methods.any { it.className == "ef.g" && it.methodName == "u" })
        assertTrue(c9108.methods.any { it.className == "ef.g" && it.methodName == "v" })
        assertTrue(c9108.methods.any { it.className == "bb.d" && it.methodName == "<init>" })
        assertTrue(c9108.methods.any { it.className == "be.p" && it.methodName == "m" })
        assertTrue(c9108.methods.any { it.className == "gg.d" && it.methodName == "<init>" })
        assertTrue(c9108.methods.any { it.className == "jh.b" && it.methodName == "<init>" })

        assertTrue(c9012.fields.any { it.className == "nextapp.fx.ui.viewer.image.ImageViewerActivity" && it.fieldName == "R5" })
        assertTrue(c9012.fields.any { it.className == "nextapp.fx.ui.widget.k" && it.fieldName == "ui" })
        assertTrue(c9012.fields.any { it.className == "nextapp.fx.ui.homecontent.HomeContentView\$d" && it.fieldName == "d" })
        assertTrue(c9108.fields.any { it.className == "nextapp.fx.ui.viewer.image.ImageViewerActivity" && it.fieldName == "N2" })
        assertTrue(c9108.fields.any { it.className == "nextapp.fx.ui.widget.n" && it.fieldName == "ui" })
        assertTrue(c9108.fields.any { it.className == "be.p" && it.fieldName == "c" })
        assertTrue(c9108.fields.any { it.className == "gg.d" && it.fieldName == "a" })
        assertTrue(c9108.fields.any { it.className == "jh.b" && it.fieldName == "a" })
        assertTrue(c9108.fields.any { it.className == "jh.b" && it.fieldName == "b" })
    }

    @Test
    fun verifyDexKitInvariantAnchorsAgainstBothRealApks() {
        val apk9012 = resolveRepoRelativeFile("../apk/base.apk")
        val apk9108 = resolveRepoRelativeFile("../FX Explorer_v9.1.0.8_9108.apk")
        assumeTrue("FX 9012 APK missing at ${apk9012.absolutePath}; skipping in CI", apk9012.isFile)
        assumeTrue("FX 9108 APK missing at ${apk9108.absolutePath}; skipping in CI", apk9108.isFile)

        verifyDexKitAnchorsForApk(apk9012, label = "9012")
        verifyDexKitAnchorsForApk(apk9108, label = "9108")
    }

    @Test
    fun verifyDalvikCodeInspectorExtractsExact9012And9108Bindings() {
        val apk9012 = resolveRepoRelativeFile("../apk/base.apk")
        val apk9108 = resolveRepoRelativeFile("../FX Explorer_v9.1.0.8_9108.apk")
        assumeTrue("FX 9012 APK missing at ${apk9012.absolutePath}; skipping in CI", apk9012.isFile)
        assumeTrue("FX 9108 APK missing at ${apk9108.absolutePath}; skipping in CI", apk9108.isFile)

        // 9012 checks
        val inspector9012 = DalvikCodeInspector.fromApkFiles(listOf(apk9012))
            ?: error("DalvikCodeInspector failed to load 9012 APK")
        val enums9012 = inspector9012.findStaticEnumFieldsByString("Lnextapp/fx/ui/res/m\$a;-><clinit>()V")
        assertTrue("9012 enum missing windowBackground", enums9012.containsKey("windowBackground"))
        val boxBg9012 = enums9012["boxBackground"] ?: error("9012 enum missing boxBackground")
        val boxPressed9012 = enums9012["boxPressedBackground"] ?: error("9012 enum missing boxPressedBackground")
        val theme9012 = inspector9012.findUiThemeFieldMapping(
            ctorMethodDesc = "Lxc/f;-><init>(Landroid/content/Context;Lu9/h;)V",
            boxBgEnumDesc = boxBg9012,
            boxPressedEnumDesc = boxPressed9012
        ) ?: error("9012 xc.f.<init> mapping null")
        assertEquals("Lxc/f;->x:I", theme9012.boxBgFieldDesc)
        assertEquals("Lxc/f;->y:I", theme9012.boxPressedBgFieldDesc)
        assertEquals("Lxc/f;->l:I", theme9012.textPrimaryFieldDesc)
        assertEquals("Lxc/f;->k:I", theme9012.textSecondaryFieldDesc)

        // 9108 checks
        val inspector9108 = DalvikCodeInspector.fromApkFiles(listOf(apk9108))
            ?: error("DalvikCodeInspector failed to load 9108 APK")
        val enums9108 = inspector9108.findStaticEnumFieldsByString("Lfg/n;-><clinit>()V")
        assertEquals("Lfg/n;->i:Lfg/n;", enums9108["windowBackground"])
        val boxBg9108 = enums9108["boxBackground"] ?: error("9108 enum missing boxBackground")
        val boxPressed9108 = enums9108["boxPressedBackground"] ?: error("9108 enum missing boxPressedBackground")
        val theme9108 = inspector9108.findUiThemeFieldMapping(
            ctorMethodDesc = "Lef/g;-><init>(Landroid/content/Context;Lmb/l;)V",
            boxBgEnumDesc = boxBg9108,
            boxPressedEnumDesc = boxPressed9108
        ) ?: error("9108 ef.g.<init> mapping null")
        assertEquals("Lef/g;->v:I", theme9108.boxBgFieldDesc)
        assertEquals("Lef/g;->w:I", theme9108.boxPressedBgFieldDesc)
        assertEquals("Lef/g;->l:I", theme9108.textPrimaryFieldDesc)
        assertEquals("Lef/g;->k:I", theme9108.textSecondaryFieldDesc)

        // Fix #3: 9108 viewer StreamProvider FileItem receiver
        assertEquals(
            "Lnextapp/fx/ui/viewer/image/c;->i:Ljava/lang/Object;",
            inspector9108.findFieldReceiverForInvoke(
                callerMethodDesc = "Lnextapp/fx/ui/viewer/image/c;->read()Ljava/io/InputStream;",
                calleeMethodDesc = "Lkh/e;->f(Landroid/content/Context;)Ljava/io/InputStream;"
            )
        )

        // Fix #7: 9108 HomeContentView.getMenuContributions() -> be.p(u, k, 14)
        assertEquals(
            14,
            inspector9108.findIntArgPassedToInvoke(
                callerMethodDesc = "Lnextapp/fx/ui/homecontent/HomeContentView;->getMenuContributions()Lnextapp/fx/ui/content/b0;",
                calleeMethodDesc = "Lbe/p;-><init>(Lnextapp/fx/ui/content/u;Lnextapp/fx/ui/content/k;I)V",
                invokeArgIndex = 3
            )
        )

        // Fix #8: 9108 RemoteThumbTask kf.e.d() -> be.k0(kf.e, String, a4.c) case 2
        val thumbCtorWrites = inspector9108.findConstructorParamFieldWrites(
            "Lbe/k0;-><init>(Lkf/e;Ljava/lang/String;La4/c;)V"
        ) ?: error("be.k0.<init> writes null")
        assertEquals("Lbe/k0;->e2:Ljava/lang/Object;", thumbCtorWrites.paramToField[1])
        assertEquals("Lbe/k0;->d2:Ljava/lang/Object;", thumbCtorWrites.paramToField[3])
        assertEquals(2, thumbCtorWrites.intConstToField["Lbe/k0;->c2:I"])

        // Fix #9: 9108 TextEdit reader caseId = 7 & ExecActivity worker/UI runnable caseIds (19 & 6)
        assertEquals(
            7,
            inspector9108.findIntArgPassedToInvoke(
                callerMethodDesc = "Lmg/n;->l(Ljava/io/InputStream;Ljava/lang/String;)Lk2/y;",
                calleeMethodDesc = "Lk2/y;-><init>(Ljava/lang/Object;ILjava/lang/Object;)V",
                invokeArgIndex = 2
            )
        )
        assertEquals(
            19,
            inspector9108.findSwitchCaseReachingString(
                methodDesc = "Lnextapp/fx/plus/ui/audio/w;->run()V",
                targetString = "x/x-elf"
            )
        )
        assertEquals(
            listOf(18, 19),
            inspector9108.findAllIntArgsPassedToInvoke(
                callerMethodDesc = "Lpg/k;->run()V",
                calleeMethodDesc = "Lnextapp/fx/plus/ui/audio/w;-><init>(Ljava/lang/Object;ILjava/lang/Object;)V",
                invokeArgIndex = 2
            ).sorted()
        )
        assertEquals(
            "Lnextapp/fx/ui/viewer/TextViewerActivity;->z5:Z" to "Lnextapp/fx/ui/viewer/TextViewerActivity;->y5:Z",
            inspector9012.findTextViewerUnsupportedAndTruncatedFields(
                applyMethodDesc = "Lnextapp/fx/ui/viewer/TextViewerActivity;->u0()V",
                textViewerClassDesc = "Lnextapp/fx/ui/viewer/TextViewerActivity;",
                candidateFieldDescs = setOf(
                    "Lnextapp/fx/ui/viewer/TextViewerActivity;->z5:Z",
                    "Lnextapp/fx/ui/viewer/TextViewerActivity;->y5:Z"
                )
            )
        )
        assertEquals(
            "Lnextapp/fx/ui/viewer/TextViewerActivity;->v2:Z" to "Lnextapp/fx/ui/viewer/TextViewerActivity;->u2:Z",
            inspector9108.findTextViewerUnsupportedAndTruncatedFields(
                applyMethodDesc = "Lpg/f0;->run()V",
                textViewerClassDesc = "Lnextapp/fx/ui/viewer/TextViewerActivity;",
                candidateFieldDescs = setOf(
                    "Lnextapp/fx/ui/viewer/TextViewerActivity;->v2:Z",
                    "Lnextapp/fx/ui/viewer/TextViewerActivity;->u2:Z"
                )
            )
        )
        assertEquals(
            6,
            inspector9108.findIntArgPassedToInvoke(
                callerMethodDesc = "Lnextapp/fx/plus/ui/audio/w;->run()V",
                calleeMethodDesc = "Lpg/k;-><init>(Lnextapp/fx/ui/viewer/ExecActivity;I)V",
                invokeArgIndex = 2
            )
        )
        val execWorkerWrites = inspector9108.findConstructorParamFieldWrites(
            "Lnextapp/fx/plus/ui/audio/w;-><init>(Ljava/lang/Object;ILjava/lang/Object;)V"
        ) ?: error("w.<init> writes null")
        assertEquals("Lnextapp/fx/plus/ui/audio/w;->i:Ljava/lang/Object;", execWorkerWrites.paramToField[1])
        assertEquals("Lnextapp/fx/plus/ui/audio/w;->f:I", execWorkerWrites.paramToField[2])
        assertEquals("Lnextapp/fx/plus/ui/audio/w;->X:Ljava/lang/Object;", execWorkerWrites.paramToField[3])
        assertEquals(
            "Lnextapp/fx/plus/ui/audio/w;->i:Ljava/lang/Object;",
            inspector9108.findFieldCastToType(
                methodDesc = "Lnextapp/fx/plus/ui/audio/w;->run()V",
                targetTypeDesc = "Lnextapp/fx/ui/viewer/ExecActivity;",
                candidateFieldDescs = setOf(
                    "Lnextapp/fx/plus/ui/audio/w;->i:Ljava/lang/Object;",
                    "Lnextapp/fx/plus/ui/audio/w;->X:Ljava/lang/Object;"
                )
            )
        )
        assertEquals(
            0,
            inspector9108.findIntArgPassedToInvoke(
                callerMethodDesc = "Lnextapp/fx/ui/viewer/TextViewerActivity;->onCreate(Landroid/os/Bundle;)V",
                calleeMethodDesc = "Lpg/f0;-><init>(Lnextapp/fx/ui/viewer/TextViewerActivity;I)V",
                invokeArgIndex = 2
            )
        )
    }

    private fun verifyDexKitAnchorsForApk(apkFile: File, label: String) {
        val index = parseApkDexIndex(apkFile)
        val errors = mutableListOf<String>()

        val exactStrings = listOf(
            "Failed to test if GIF is animated.",
            "INTERNAL ERROR",
            "action_filesystems",
            "action_home_customize",
            "action_theme",
            "907044817113-8d3ccj149pvkhj2ru91scjaahpr8v1e1.apps.googleusercontent.com",
            "https://accounts.google.com/o/oauth2/token",
            "Network",
            "Remote",
            "image/",
            "GeneratedRemoteItemThumbnail",
            "fileViewImageThumbnails",
            "trimColorAccent4",
            "trimColorBase4",
            "boxBackground",
            "boxPressedBackground",
            "windowBackground",
            "x/x-android-binary-xml",
            "x/x-elf",
            "Out of memory attempting to display text file."
        )
        for (expected in exactStrings) {
            if (expected !in index.dexStrings) {
                errors += "[$label] Missing invariant DexKit exact string: \"$expected\""
            }
        }

        val prefixStrings = listOf(
            "Task Thread: ",
            "ImageViewer: Unable to retrieve sibling files",
            "Out of memory reading file, Line "
        )
        for (prefix in prefixStrings) {
            if (index.dexStrings.none { it.startsWith(prefix) }) {
                errors += "[$label] Missing invariant DexKit prefix string: \"$prefix\""
            }
        }

        if (index.dexStrings.none { it.contains("EXCEEDED MAXIMUM LINE COUNT") }) {
            errors += "[$label] Missing invariant DexKit substring: \"EXCEEDED MAXIMUM LINE COUNT\""
        }

        val anchorClasses = listOf(
            "nextapp.fx.ui.viewer.image.ImageViewerActivity",
            "nextapp.fx.ui.viewer.image.ImageDisplay",
            "nextapp.maui.ui.imageview.TouchImageDisplay",
            "nextapp.fx.dirimpl.file.FileCatalog",
            "nextapp.xf.dir.DirectoryCatalog",
            "nextapp.fx.dir.optionstore.DirectoryOptionStore",
            "nextapp.fx.dir.optionstore.HiddenFileStore",
            "nextapp.fx.ui.ExplorerActivity",
            "nextapp.fx.ui.homecontent.HomeContentView",
            "nextapp.fx.ui.res.ActionIcons",
            "nextapp.fx.ui.fxsystem.MainPrefActivity",
            "nextapp.maui.ui.preference.LabelPreference",
            "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity",
            "nextapp.fx.ui.viewer.TextViewerActivity",
            "nextapp.fx.ui.viewer.ExecActivity"
        )
        for (cls in anchorClasses) {
            val desc = toDexDescriptor(cls)
            if (desc !in index.definedClasses) {
                errors += "[$label] Missing DexKit anchor class_def: $desc"
            }
        }

        val taskThreadCtorKey = "<init>(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Runnable;)V"
        val hasTaskThread = index.definedClasses.any { clsDesc ->
            index.classSuperClasses[clsDesc] == "Ljava/lang/Thread;" &&
                index.classMethods[clsDesc]?.containsKey(taskThreadCtorKey) == true
        }
        if (!hasTaskThread) {
            errors += "[$label] Missing TaskThread subclass of Ljava/lang/Thread; with $taskThreadCtorKey"
        }

        val hasFxDialog = index.definedClasses.any { clsDesc ->
            clsDesc.startsWith("Lnextapp/fx/ui/widget/") &&
                index.classMethods[clsDesc]?.containsKey("getDefaultContentLayout()Landroid/widget/LinearLayout;") == true &&
                index.classMethods[clsDesc]?.containsKey("isBackgroundLight()Z") == true
        }
        if (!hasFxDialog) {
            errors += "[$label] Missing FX dialog class defining getDefaultContentLayout() and isBackgroundLight()"
        }

        assertTrue(
            "DexKit invariant anchor failures for FX $label (${errors.size}):\n${errors.joinToString("\n")}",
            errors.isEmpty()
        )
    }

    private fun resolveRepoRelativeFile(relFromRepoRoot: String): File {
        val direct = File(relFromRepoRoot)
        if (direct.isFile) return direct
        val fromModule = File("../$relFromRepoRoot")
        if (fromModule.isFile) return fromModule
        return direct
    }

    private fun verifyApkContract(apkFile: File, contract: VersionContract, label: String) {
        val index = parseApkDexIndex(apkFile)
        val errors = mutableListOf<String>()

        for (javaClass in contract.classes) {
            val desc = toDexDescriptor(javaClass)
            if (desc !in index.definedClasses) {
                errors += "[$label] Missing DEX class_def: $desc"
            }
        }

        for (spec in contract.methods) {
            val classDesc = toDexDescriptor(spec.className)
            val clsMethods = index.classMethods[classDesc]
            if (clsMethods == null) {
                errors += "[$label] Missing owner class for method ${spec.className}.${spec.methodName}${spec.descriptor}"
                continue
            }
            val key = "${spec.methodName}${spec.descriptor}"
            val flags = clsMethods[key]
            if (flags == null) {
                errors += "[$label] Missing DEX method: $classDesc->$key"
            } else {
                val actualStatic = (flags and ACC_STATIC) != 0
                if (actualStatic != spec.isStatic) {
                    errors += "[$label] Static mismatch for $classDesc->$key (expected isStatic=${spec.isStatic}, actual=$actualStatic)"
                }
            }
        }

        for (spec in contract.fields) {
            val classDesc = toDexDescriptor(spec.className)
            val clsFields = index.classFields[classDesc]
            if (clsFields == null) {
                errors += "[$label] Missing owner class for field ${spec.className}.${spec.fieldName}:${spec.typeDescriptor}"
                continue
            }
            val key = "${spec.fieldName}:${spec.typeDescriptor}"
            val flags = clsFields[key]
            if (flags == null) {
                errors += "[$label] Missing DEX field: $classDesc->$key"
            } else {
                val actualStatic = (flags and ACC_STATIC) != 0
                if (actualStatic != spec.isStatic) {
                    errors += "[$label] Static mismatch for $classDesc->$key (expected isStatic=${spec.isStatic}, actual=$actualStatic)"
                }
            }
        }

        assertTrue(
            "DEX contract failures for FX $label (${errors.size}):\n${errors.joinToString("\n")}",
            errors.isEmpty()
        )
    }

    private fun toDexDescriptor(javaName: String): String {
        return "L${javaName.replace('.', '/')};"
    }

    private class DexIndex {
        val definedClasses = HashSet<String>(4096)
        val dexStrings = HashSet<String>(16384)
        val classSuperClasses = HashMap<String, String>(4096)
        val classMethods = HashMap<String, MutableMap<String, Int>>(4096)
        val classFields = HashMap<String, MutableMap<String, Int>>(4096)
    }

    private fun parseApkDexIndex(apkFile: File): DexIndex {
        val index = DexIndex()
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && DEX_NAME_REGEX.matches(it.name) }
                .sortedBy { it.name }
                .toList()
            require(entries.isNotEmpty()) { "No classes*.dex entries in ${apkFile.name}" }
            for (entry in entries) {
                val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                parseSingleDex(dexBytes, index)
            }
        }
        return index
    }

    private fun parseSingleDex(bytes: ByteArray, index: DexIndex) {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = String(bytes, 0, 4, Charsets.US_ASCII)
        require(magic == "dex\n") { "Invalid DEX magic: $magic" }

        val stringIdsSize = buf.getInt(0x38)
        val stringIdsOff = buf.getInt(0x3C)
        val typeIdsSize = buf.getInt(0x40)
        val typeIdsOff = buf.getInt(0x44)
        val protoIdsSize = buf.getInt(0x48)
        val protoIdsOff = buf.getInt(0x4C)
        val fieldIdsSize = buf.getInt(0x50)
        val fieldIdsOff = buf.getInt(0x54)
        val methodIdsSize = buf.getInt(0x58)
        val methodIdsOff = buf.getInt(0x5C)
        val classDefsSize = buf.getInt(0x60)
        val classDefsOff = buf.getInt(0x64)

        val strings = Array(stringIdsSize) { i ->
            val strDataOff = buf.getInt(stringIdsOff + i * 4)
            readMutf8String(bytes, strDataOff)
        }
        index.dexStrings.addAll(strings)

        val types = Array(typeIdsSize) { i ->
            val descriptorIdx = buf.getInt(typeIdsOff + i * 4)
            strings[descriptorIdx]
        }

        val protos = Array(protoIdsSize) { i ->
            val base = protoIdsOff + i * 12
            val returnTypeIdx = buf.getInt(base + 4)
            val paramsOff = buf.getInt(base + 8)
            val sb = StringBuilder("(")
            if (paramsOff != 0) {
                val count = buf.getInt(paramsOff)
                for (p in 0 until count) {
                    val paramTypeIdx = buf.getShort(paramsOff + 4 + p * 2).toInt() and 0xFFFF
                    sb.append(types[paramTypeIdx])
                }
            }
            sb.append(')').append(types[returnTypeIdx])
            sb.toString()
        }

        val fieldKeys = Array(fieldIdsSize) { i ->
            val base = fieldIdsOff + i * 8
            val typeIdx = buf.getShort(base + 2).toInt() and 0xFFFF
            val nameIdx = buf.getInt(base + 4)
            "${strings[nameIdx]}:${types[typeIdx]}"
        }

        val methodKeys = Array(methodIdsSize) { i ->
            val base = methodIdsOff + i * 8
            val protoIdx = buf.getShort(base + 2).toInt() and 0xFFFF
            val nameIdx = buf.getInt(base + 4)
            "${strings[nameIdx]}${protos[protoIdx]}"
        }

        for (c in 0 until classDefsSize) {
            val base = classDefsOff + c * 32
            val classIdx = buf.getInt(base)
            val superclassIdx = buf.getInt(base + 8)
            val classDataOff = buf.getInt(base + 24)
            val classDesc = types[classIdx]
            index.definedClasses.add(classDesc)
            if (superclassIdx in types.indices) {
                index.classSuperClasses[classDesc] = types[superclassIdx]
            }

            if (classDataOff == 0) continue

            val fieldMap = index.classFields.getOrPut(classDesc) { HashMap() }
            val methodMap = index.classMethods.getOrPut(classDesc) { HashMap() }

            val cursor = IntArray(1) { classDataOff }
            val staticFieldsSize = readUleb128(bytes, cursor)
            val instanceFieldsSize = readUleb128(bytes, cursor)
            val directMethodsSize = readUleb128(bytes, cursor)
            val virtualMethodsSize = readUleb128(bytes, cursor)

            var fieldIdx = 0
            for (i in 0 until staticFieldsSize) {
                fieldIdx += readUleb128(bytes, cursor)
                val accessFlags = readUleb128(bytes, cursor)
                fieldMap[fieldKeys[fieldIdx]] = accessFlags
            }

            fieldIdx = 0
            for (i in 0 until instanceFieldsSize) {
                fieldIdx += readUleb128(bytes, cursor)
                val accessFlags = readUleb128(bytes, cursor)
                fieldMap[fieldKeys[fieldIdx]] = accessFlags
            }

            var methodIdx = 0
            for (i in 0 until directMethodsSize) {
                methodIdx += readUleb128(bytes, cursor)
                val accessFlags = readUleb128(bytes, cursor)
                readUleb128(bytes, cursor) // code_off
                methodMap[methodKeys[methodIdx]] = accessFlags
            }

            methodIdx = 0
            for (i in 0 until virtualMethodsSize) {
                methodIdx += readUleb128(bytes, cursor)
                val accessFlags = readUleb128(bytes, cursor)
                readUleb128(bytes, cursor) // code_off
                methodMap[methodKeys[methodIdx]] = accessFlags
            }
        }
    }

    private fun readUleb128(bytes: ByteArray, cursor: IntArray): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = bytes[cursor[0]++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result
    }

    private fun readMutf8String(bytes: ByteArray, offset: Int): String {
        val cursor = IntArray(1) { offset }
        readUleb128(bytes, cursor) // utf16_size
        val out = ByteArrayOutputStream()
        var pos = cursor[0]
        while (true) {
            val b = bytes[pos++].toInt() and 0xFF
            if (b == 0) break
            out.write(b)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    companion object {
        private const val ACC_STATIC = 0x0008
        private val DEX_NAME_REGEX = Regex("^classes[0-9]*\\.dex$")
    }
}
