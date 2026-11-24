package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

class DexKitSymbolCacheAndResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // -------------------------------------------------------------------------
    // Synthetic JVM fixture classes used to test DexSymbolCache & hydration
    // -------------------------------------------------------------------------
    enum class FakeDialogStyle {
        SIMPLE,
        WITH_CLOSE
    }

    class FakeTaskThread(
        val ownerClass: Class<*>,
        val taskLabel: String,
        val runnable: Runnable?
    ) : Thread(runnable, taskLabel)

    class FakeDim(var width: Int, var height: Int)

    class FakeItem(val tag: String)

    class FakeDialog(val ctx: Context, val style: FakeDialogStyle) {
        var ui: FakeUiTheme? = null
        fun setHeader(title: CharSequence) {}
        fun setDescription(desc: CharSequence) {}
        fun isBackgroundLight(): Boolean = true
    }

    class FakeUiTheme {
        var isLight: Boolean = true
        var textPrimary: Int = -553648128
        var textSecondary: Int = 1862270976
        var boxBg: Int = 0x11223344
        var boxPressedBg: Int = 0x22334455
        fun getWindowBg(): Int = 0xFFFFFF
        fun getTrimAccent(): Int = 0x0D6EFD
        fun getTrimBase(): Int = 0x3366CC
    }

    class FakeTouchDisplay {
        var bitmap: Bitmap? = null
        var swLayerFlag: Boolean = false
        var displaySize: FakeDim? = null
        fun updateBounds(w: Int, h: Int, swap: Boolean) {}
    }

    class FakeViewer {
        var currentHolder: FakeHolder? = null
        fun openStream(): InputStream? = null
    }

    class FakeHolder(var item: FakeItem?, var tag: String)

    // -------------------------------------------------------------------------
    // Manifest & Cache Tests
    // -------------------------------------------------------------------------
    @Test
    fun manifestJsonRoundTripPreservesAllSectionsAndUsability() {
        val original = FxSymbolManifest(
            schemaVersion = DexSymbolCache.SCHEMA_VERSION,
            fingerprint = "fp-abc-123",
            classes = mapOf(
                FxSymbolKeys.CLS_FILE_ITEM to descriptorOf(FakeItem::class.java),
                FxSymbolKeys.CLS_TOUCH_DISPLAY to descriptorOf(FakeTouchDisplay::class.java),
                FxSymbolKeys.CLS_IMAGE_VIEWER_ACTIVITY to descriptorOf(FakeViewer::class.java),
                FxSymbolKeys.CLS_FX_DIALOG_STYLE_WITH_CLOSE_ENUM to descriptorOf(FakeDialogStyle::class.java)
            ),
            methods = mapOf(
                FxSymbolKeys.CTR_TASK_THREAD to "${descriptorOf(FakeTaskThread::class.java)}-><init>(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Runnable;)V",
                FxSymbolKeys.MTD_HW_UPDATE_BOUNDS to "${descriptorOf(FakeTouchDisplay::class.java)}->updateBounds(IIZ)V"
            ),
            fields = mapOf(
                FxSymbolKeys.FLD_DIM_WIDTH to "${descriptorOf(FakeDim::class.java)}->width:I",
                FxSymbolKeys.FLD_DIM_HEIGHT to "${descriptorOf(FakeDim::class.java)}->height:I"
            ),
            stringLists = mapOf(
                FxSymbolKeys.LIST_REMOTE_ITEM_CLASSES to listOf(FakeItem::class.java.name)
            ),
            ints = mapOf(
                FxSymbolKeys.INT_REMOTE_THUMB_WORKER_CASE_ID to 2,
                FxSymbolKeys.INT_MENU_CONTRIB_CASE_ID to 14,
                FxSymbolKeys.INT_EXEC_WORKER_CASE_ID to 19
            )
        )

        assertTrue(original.isUsable())

        val json = original.toJson()
        val parsed = FxSymbolManifest.fromJson(json)
        assertNotNull(parsed)
        assertEquals(original.schemaVersion, parsed!!.schemaVersion)
        assertEquals(original.fingerprint, parsed.fingerprint)
        assertEquals(original.classes, parsed.classes)
        assertEquals(original.methods, parsed.methods)
        assertEquals(original.fields, parsed.fields)
        assertEquals(original.stringLists, parsed.stringLists)
        assertEquals(original.ints, parsed.ints)
        assertTrue(parsed.isUsable())
    }

    @Test
    fun sparseManifestIsMarkedUnusable() {
        val sparse = FxSymbolManifest(
            schemaVersion = DexSymbolCache.SCHEMA_VERSION,
            fingerprint = "fp-sparse",
            classes = mapOf("a" to "La;", "b" to "Lb;"),
            methods = mapOf("m1" to "La;->m1()V")
        )
        assertFalse(sparse.isUsable())
    }

    @Test
    fun cacheSaveLoadHitMissFingerprintInvalidationAndClear() {
        val cacheDir = tempFolder.newFolder("cache")
        val fp1 = DexSymbolCache.computeFingerprintFromValues(
            schemaVersion = DexSymbolCache.SCHEMA_VERSION,
            targetVersionCode = 9201L,
            targetSourceDir = "/data/app/fx/base.apk",
            targetApkSize = 15_000_000L,
            targetApkMtime = 1_700_000_000_000L,
            targetSplitDirs = "",
            moduleVersionCode = 1004001L,
            moduleSourceDir = "/data/app/fxplus/base.apk",
            moduleApkSize = 1_500_000L,
            moduleApkMtime = 1_700_000_123_000L
        )
        val fpChangedTarget = DexSymbolCache.computeFingerprintFromValues(
            schemaVersion = DexSymbolCache.SCHEMA_VERSION,
            targetVersionCode = 9202L,
            targetSourceDir = "/data/app/fx/base.apk",
            targetApkSize = 15_000_000L,
            targetApkMtime = 1_700_000_000_000L,
            targetSplitDirs = "",
            moduleVersionCode = 1004001L,
            moduleSourceDir = "/data/app/fxplus/base.apk",
            moduleApkSize = 1_500_000L,
            moduleApkMtime = 1_700_000_123_000L
        )
        val fpChangedModule = DexSymbolCache.computeFingerprintFromValues(
            schemaVersion = DexSymbolCache.SCHEMA_VERSION,
            targetVersionCode = 9201L,
            targetSourceDir = "/data/app/fx/base.apk",
            targetApkSize = 15_000_000L,
            targetApkMtime = 1_700_000_000_000L,
            targetSplitDirs = "",
            moduleVersionCode = 1004002L,
            moduleSourceDir = "/data/app/fxplus/base.apk",
            moduleApkSize = 1_500_000L,
            moduleApkMtime = 1_700_000_123_000L
        )
        assertNotEquals(fp1, fpChangedTarget)
        assertNotEquals(fp1, fpChangedModule)

        assertNull(DexSymbolCache.load(cacheDir, fp1))

        val manifest = FxSymbolManifest(
            schemaVersion = DexSymbolCache.SCHEMA_VERSION,
            fingerprint = fp1,
            classes = mapOf(
                FxSymbolKeys.CLS_FILE_ITEM to "La/b;",
                FxSymbolKeys.CLS_DIR_ITEM to "La/c;",
                FxSymbolKeys.CLS_TOUCH_DISPLAY to "La/d;",
                FxSymbolKeys.CLS_EXPLORER_ACTIVITY to "La/e;"
            ),
            methods = mapOf(FxSymbolKeys.MTD_LOAD_THUMBNAIL to "La/b;->m()V")
        )
        assertTrue(DexSymbolCache.save(cacheDir, manifest))

        // Cache hit for exact matching fingerprint
        val hit = DexSymbolCache.load(cacheDir, fp1)
        assertNotNull(hit)
        assertEquals(manifest, hit)

        // Cache miss when fingerprint changes (target APK or module APK updated)
        assertNull(DexSymbolCache.load(cacheDir, fpChangedTarget))
        assertNull(DexSymbolCache.load(cacheDir, fpChangedModule))

        // Delete cache file cleanly
        val cacheFile = DexSymbolCache.resolveCacheFile(cacheDir)!!
        assertTrue(cacheFile.isFile)
        assertTrue(cacheFile.delete())
        assertNull(DexSymbolCache.load(cacheDir, fp1))
    }

    @Test
    fun cacheLoadRejectsCorruptJsonBlankFingerprintOrWrongSchema() {
        val cacheDir = tempFolder.newFolder("corrupt_cache")
        val targetFile = DexSymbolCache.resolveCacheFile(cacheDir)!!
        targetFile.parentFile!!.mkdirs()

        targetFile.writeText("{not valid json!!!")
        assertNull(FxSymbolManifest.fromJson(targetFile.readText()))
        assertNull(DexSymbolCache.load(cacheDir, "any-fp"))

        targetFile.writeText("""{"schemaVersion":999,"fingerprint":"fp1","classes":{"a":"La;"}}""")
        assertNull(DexSymbolCache.load(cacheDir, "fp1"))

        assertNull(FxSymbolManifest.fromJson(""))
        assertNull(FxSymbolManifest.fromJson("""{"schemaVersion":0,"fingerprint":"fp1"}"""))
        assertNull(FxSymbolManifest.fromJson("""{"schemaVersion":1,"fingerprint":""}"""))
        assertNull(DexSymbolCache.load(File(cacheDir, "missing"), "fp1"))
        assertNull(DexSymbolCache.load(cacheDir, ""))
    }

    @Test
    fun pureJvmDescriptorResolutionHandlesMethodsConstructorsFieldsAndPrimitives() {
        val cl = javaClass.classLoader!!

        assertEquals(Void.TYPE, DexSymbolCache.resolveTypeDescriptor(cl, "V"))
        assertEquals(Boolean::class.javaPrimitiveType, DexSymbolCache.resolveTypeDescriptor(cl, "Z"))
        assertEquals(Int::class.javaPrimitiveType, DexSymbolCache.resolveTypeDescriptor(cl, "I"))
        assertEquals(Long::class.javaPrimitiveType, DexSymbolCache.resolveTypeDescriptor(cl, "J"))
        assertEquals(Float::class.javaPrimitiveType, DexSymbolCache.resolveTypeDescriptor(cl, "F"))
        assertEquals(IntArray::class.java, DexSymbolCache.resolveTypeDescriptor(cl, "[I"))
        assertEquals(FakeItem::class.java, DexSymbolCache.resolveClass(cl, descriptorOf(FakeItem::class.java)))

        val mtdDesc = "${descriptorOf(FakeTouchDisplay::class.java)}->updateBounds(IIZ)V"
        val resolvedMethod = DexSymbolCache.resolveMethod(cl, mtdDesc)
        assertNotNull(resolvedMethod)
        assertEquals("updateBounds", resolvedMethod!!.name)
        assertEquals(3, resolvedMethod.parameterTypes.size)

        // Mismatched return type rejects resolution safely
        assertNull(DexSymbolCache.resolveMethod(cl, "${descriptorOf(FakeTouchDisplay::class.java)}->updateBounds(IIZ)I"))

        val ctorDesc = "${descriptorOf(FakeTaskThread::class.java)}-><init>(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Runnable;)V"
        val resolvedCtor = DexSymbolCache.resolveConstructor(cl, ctorDesc)
        assertNotNull(resolvedCtor)
        assertEquals(3, resolvedCtor!!.parameterTypes.size)

        val fieldDesc = "${descriptorOf(FakeDim::class.java)}->width:I"
        val resolvedField = DexSymbolCache.resolveField(cl, fieldDesc)
        assertNotNull(resolvedField)
        assertEquals("width", resolvedField!!.name)
        assertNull(DexSymbolCache.resolveField(cl, "${descriptorOf(FakeDim::class.java)}->width:J"))
    }

    @Test
    fun hydrateFromManifestProducesDexKitDynamicResolvedSymbols() {
        val cl = javaClass.classLoader!!
        val touchDesc = descriptorOf(FakeTouchDisplay::class.java)
        val dimDesc = descriptorOf(FakeDim::class.java)
        val viewerDesc = descriptorOf(FakeViewer::class.java)
        val holderDesc = descriptorOf(FakeHolder::class.java)
        val itemDesc = descriptorOf(FakeItem::class.java)
        val dialogDesc = descriptorOf(FakeDialog::class.java)
        val styleDesc = descriptorOf(FakeDialogStyle::class.java)
        val uiDesc = descriptorOf(FakeUiTheme::class.java)
        val threadDesc = descriptorOf(FakeTaskThread::class.java)

        val manifest = FxSymbolManifest(
            schemaVersion = DexSymbolCache.SCHEMA_VERSION,
            fingerprint = "fp-hydrate-test",
            classes = mapOf(
                FxSymbolKeys.CLS_FILE_ITEM to itemDesc,
                FxSymbolKeys.CLS_TOUCH_DISPLAY to touchDesc,
                FxSymbolKeys.CLS_IMAGE_VIEWER_ACTIVITY to viewerDesc,
                FxSymbolKeys.CLS_FX_DIALOG_STYLE_WITH_CLOSE_ENUM to styleDesc
            ),
            methods = mapOf(
                FxSymbolKeys.CTR_TASK_THREAD to "$threadDesc-><init>(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Runnable;)V",
                FxSymbolKeys.MTD_VIEWER_OPEN_STREAM to "$viewerDesc->openStream()Ljava/io/InputStream;",
                FxSymbolKeys.MTD_HW_UPDATE_BOUNDS to "$touchDesc->updateBounds(IIZ)V",
                FxSymbolKeys.CTR_FX_DIALOG to "$dialogDesc-><init>(Landroid/content/Context;$styleDesc)V",
                FxSymbolKeys.MTD_FX_DIALOG_SET_HEADER to "$dialogDesc->setHeader(Ljava/lang/CharSequence;)V",
                FxSymbolKeys.MTD_FX_DIALOG_IS_BG_LIGHT to "$dialogDesc->isBackgroundLight()Z",
                FxSymbolKeys.MTD_UI_WINDOW_BG to "$uiDesc->getWindowBg()I",
                FxSymbolKeys.MTD_UI_TRIM_ACCENT to "$uiDesc->getTrimAccent()I",
                FxSymbolKeys.MTD_UI_TRIM_BASE to "$uiDesc->getTrimBase()I"
            ),
            fields = mapOf(
                FxSymbolKeys.FLD_DIM_WIDTH to "$dimDesc->width:I",
                FxSymbolKeys.FLD_DIM_HEIGHT to "$dimDesc->height:I",
                FxSymbolKeys.FLD_HW_BITMAP to "$touchDesc->bitmap:Landroid/graphics/Bitmap;",
                FxSymbolKeys.FLD_HW_SOFTWARE_LAYER to "$touchDesc->swLayerFlag:Z",
                FxSymbolKeys.FLD_HW_DISPLAY_SIZE to "$touchDesc->displaySize:$dimDesc",
                FxSymbolKeys.FLD_VIEWER_CURRENT_HOLDER to "$viewerDesc->currentHolder:$holderDesc",
                FxSymbolKeys.FLD_ITEM_HOLDER_FILE_ITEM to "$holderDesc->item:$itemDesc",
                FxSymbolKeys.CLS_FX_DIALOG_STYLE_WITH_CLOSE_FIELD to "$styleDesc->WITH_CLOSE:$styleDesc",
                FxSymbolKeys.FLD_FX_DIALOG_UI to "$dialogDesc->ui:$uiDesc",
                FxSymbolKeys.FLD_UI_IS_WINDOW_LIGHT to "$uiDesc->isLight:Z",
                FxSymbolKeys.FLD_UI_TEXT_PRIMARY to "$uiDesc->textPrimary:I",
                FxSymbolKeys.FLD_UI_TEXT_SECONDARY to "$uiDesc->textSecondary:I",
                FxSymbolKeys.FLD_UI_BOX_BG to "$uiDesc->boxBg:I",
                FxSymbolKeys.FLD_UI_BOX_PRESSED_BG to "$uiDesc->boxPressedBg:I"
            ),
            stringLists = mapOf(
                FxSymbolKeys.LIST_REMOTE_ITEM_CLASSES to listOf(FakeItem::class.java.name)
            ),
            ints = mapOf(
                FxSymbolKeys.INT_REMOTE_THUMB_WORKER_CASE_ID to 3,
                FxSymbolKeys.INT_MENU_CONTRIB_CASE_ID to 15,
                FxSymbolKeys.INT_TEXT_VIEWER_WORKER_CASE_ID to 4,
                FxSymbolKeys.INT_TEXT_EDIT_RESULT_CASE_ID to 8,
                FxSymbolKeys.INT_EXEC_WORKER_CASE_ID to 20,
                FxSymbolKeys.INT_EXEC_UI_RUNNABLE_CASE_ID to 7
            )
        )

        val symbols = FxSymbolResolver.hydrateFromManifest(cl, 9500L, manifest, "dexkit:cache")
        assertEquals(FxProfile.DEXKIT_DYNAMIC, symbols.profile)
        assertEquals("dexkit:cache", symbols.symbolResolutionSource)
        assertEquals(9500L, symbols.versionCode)
        assertEquals(FakeItem::class.java, symbols.sibling.fileItemClass)
        assertEquals(FakeViewer::class.java, symbols.sibling.imageViewerActivityClass)
        assertEquals(listOf(FakeItem::class.java), symbols.viewer.remoteItemClasses)
        assertEquals("openStream", symbols.viewer.viewerOpenStreamMethod?.name)
        assertEquals(FakeTaskThread::class.java, symbols.viewer.taskThreadCtor?.declaringClass)
        assertEquals("width", symbols.remoteThumb.dimWidthField?.name)
        assertEquals("height", symbols.remoteThumb.dimHeightField?.name)
        assertEquals(3, symbols.remoteThumb.remoteThumbWorkerCaseId)
        assertEquals(15, symbols.homeMenu.menuContribCaseId)
        assertEquals(4, symbols.bonusLimits.textViewerWorkerCaseId)
        assertEquals(8, symbols.bonusLimits.textEditResultCaseId)
        assertEquals(20, symbols.bonusLimits.execWorkerCaseId)
        assertEquals(7, symbols.bonusLimits.execUiRunnableCaseId)
        assertEquals(FakeDialogStyle.WITH_CLOSE, symbols.settings.fxDialogDefaultWithClose)
        assertEquals("getWindowBg", symbols.settings.uiWindowBgMethod?.name)
        assertEquals("getTrimAccent", symbols.settings.uiTrimAccentMethod?.name)
        assertEquals("updateBounds", symbols.hardwareRender.updateBoundsMethod?.name)
        assertEquals("bitmap", symbols.hardwareRender.bitmapField?.name)
        assertEquals("swLayerFlag", symbols.hardwareRender.softwareLayerFlagField?.name)
        assertEquals("width", symbols.hardwareRender.displaySizeWidthField?.name)
        assertEquals("height", symbols.hardwareRender.displaySizeHeightField?.name)
        assertEquals(symbols.sibling.viewerCurrentHolderField, symbols.hardwareRender.currentHolderField)
        assertEquals(symbols.sibling.itemHolderFileItemField, symbols.hardwareRender.holderFileItemField)

        // Unknown version without DexKit native lib in JVM gracefully falls back without throwing
        val fallback = FxSymbolResolver.resolve(cl, 9500L, null, null, null)
        assertEquals(FxProfile.STRUCTURAL_FALLBACK, fallback.profile)
        assertEquals("fallback", fallback.symbolResolutionSource)
    }

    @Test
    fun buildModuleVersionTagReactsToSizeAndLastModifiedChanges() {
        val apkFile = tempFolder.newFile("fxplus-module.apk")
        apkFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertTrue(apkFile.setLastModified(1_700_000_000_000L))
        val tagA = FxDexKitResolver.buildModuleVersionTag(apkFile)

        assertTrue(apkFile.setLastModified(1_700_000_123_000L))
        val tagB = FxDexKitResolver.buildModuleVersionTag(apkFile)
        assertNotEquals(tagA, tagB)

        apkFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
        assertTrue(apkFile.setLastModified(1_700_000_123_000L))
        val tagC = FxDexKitResolver.buildModuleVersionTag(apkFile)
        assertNotEquals(tagB, tagC)
        assertFalse(tagC.startsWith("v0_"))
    }

    private fun descriptorOf(clazz: Class<*>): String = "L${clazz.name.replace('.', '/')};"
}
