package com.scluzlep.fxplusmodule.xposed

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceCategory
import android.preference.PreferenceScreen
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.scluzlep.fxplusmodule.cache.PrefetchCacheManager
import com.scluzlep.fxplusmodule.config.ConfigContract
import com.scluzlep.fxplusmodule.config.ModuleConfig
import com.scluzlep.fxplusmodule.config.PrefetchLimitMode
import com.scluzlep.fxplusmodule.shizuku.ShizukuBridgeContract
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.Locale

internal data class FxDialogPalette(
    val isLight: Boolean,
    val windowBg: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val cardBg: Int,
    val chipBg: Int,
    val chipSelectedBg: Int,
    val chipSelectedText: Int,
    val accent: Int,
    val divider: Int
)

/**
 * Injects native `FxPlusModule` settings into FX's `MainPrefActivity` and Home overflow menu
 * (`9012` & `9108`), and builds a theme-aware FX native dialog (`nextapp.fx.ui.widget.k` / `n`)
 * matched to FX's live color scheme with inline Root/Shizuku diagnostics and Google Drive OAuth setup.
 */
object FxSettingsInjectorHook {

    private const val PREF_KEY_ENTRY = "fxplus_native_settings_entry"
    private const val CATEGORY_TITLE = "FxPlusModule"
    private const val ENTRY_TITLE = "FxPlusModule 设置"
    private const val DIALOG_TITLE = "FxPlusModule"
    private const val DIALOG_DESCRIPTION = "配置立即生效"
    private const val HOME_MENU_ENTRY_TITLE = "FxPlusModule"
    private const val HOME_MENU_CASE_9108 = 14
    private const val METHOD_REQUEST_PERMISSION = "request_permission"
    private const val KEY_SHIZUKU_INSTALLED = "shizuku_installed"

    private data class StorageBackendStatus(
        val bridgeReachable: Boolean,
        val shizukuInstalled: Boolean,
        val shizukuAvailable: Boolean,
        val shizukuGranted: Boolean,
        val serviceBound: Boolean,
        val privilegedUid: Int,
        val rootAvailable: Boolean
    )

    @Volatile
    private var injectedPrefRef: WeakReference<Preference>? = null

    @Volatile
    private var cachedBackendStatus: StorageBackendStatus? = null

    fun install(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val st = symbols.settings

        installer.install("settings:MainPrefActivity.onCreate", st.mainPrefOnCreate) { chain ->
            val result = chain.proceed()
            @Suppress("DEPRECATION")
            val activity = chain.thisObject as? PreferenceActivity
            if (activity != null) {
                FxConfigBridge.attachContext(activity)
                GoogleDriveOAuthHook.attachContext(activity)
                injectPreferenceEntry(symbols, installer, activity)
            }
            result
        }

        installer.install("settings:MainPrefActivity.onResume", st.mainPrefOnResume) { chain ->
            val result = chain.proceed()
            @Suppress("DEPRECATION")
            val activity = chain.thisObject as? PreferenceActivity
            if (activity != null) {
                FxConfigBridge.attachContext(activity)
                GoogleDriveOAuthHook.attachContext(activity)
                updateEntrySummary(activity)
            }
            result
        }

        installHomeMenuHook(symbols, installer)
    }

    private fun installHomeMenuHook(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val hm = symbols.homeMenu
        installer.install("settings:HomeMenu.populate", hm.menuContribPopulateMethod) { chain ->
            val result = chain.proceed()
            val contrib = chain.thisObject ?: return@install result
            if (symbols.profile == FxProfile.FX_9108) {
                val caseId = runCatching { hm.menuContribCaseField?.getInt(contrib) }.getOrNull()
                if (caseId != HOME_MENU_CASE_9108) return@install result
            }
            val homeView = runCatching { hm.menuContribOwnerField?.get(contrib) }.getOrNull()
                ?: return@install result
            if (hm.homeContentViewClass?.isInstance(homeView) != true) {
                return@install result
            }
            val overflowMenu = chain.getArg(1) ?: return@install result
            val homeActivity = runCatching { hm.homeViewActivityField?.get(homeView) as? Activity }
                .getOrNull() ?: return@install result
            val res = runCatching { hm.homeViewResField?.get(homeView) as? Resources }.getOrNull()
                ?: homeActivity.resources
            val isBgLight = runCatching { hm.menuContribBgLightField?.getBoolean(contrib) }
                .getOrNull() ?: false

            appendHomeMenuEntry(symbols, installer, overflowMenu, res, isBgLight, homeActivity)
            result
        }
    }

    private fun appendHomeMenuEntry(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        overflowMenu: Any,
        res: Resources,
        isBgLight: Boolean,
        homeActivity: Activity
    ) {
        val hm = symbols.homeMenu
        val addMethod = hm.menuModelAddItemMethod ?: return
        val actionCtor = hm.menuActionCtor ?: return
        val listenerInterface = hm.menuClickListenerInterface ?: return

        runCatching {
            if (hasHomeMenuEntry(hm, overflowMenu)) return

            createMenuDivider(hm, overflowMenu)?.let { divider ->
                addMethod.invoke(overflowMenu, divider)
            }

            val icon: Drawable? = runCatching {
                when (symbols.profile) {
                    FxProfile.FX_9012 -> hm.actionIconsCreateMethod?.invoke(
                        null, res, "action_settings", isBgLight
                    ) as? Drawable
                    FxProfile.FX_9108 -> hm.actionIconsCreateMethod?.invoke(
                        null, res, "action_settings", if (isBgLight) 1 else 0
                    ) as? Drawable
                    FxProfile.STRUCTURAL_FALLBACK -> null
                }
            }.getOrNull()

            val clickProxy = Proxy.newProxyInstance(
                symbols.classLoader,
                arrayOf(listenerInterface)
            ) { proxy, method, args ->
                when (method.name) {
                    "equals" -> proxy === args?.getOrNull(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "FxPlusModuleMenuListener"
                    else -> {
                        homeActivity.runOnUiThread {
                            showNativeSettingsDialog(symbols, installer, homeActivity)
                        }
                        null
                    }
                }
            }

            val item = actionCtor.newInstance(HOME_MENU_ENTRY_TITLE, icon, clickProxy)
            addMethod.invoke(overflowMenu, item)
        }.onFailure { err ->
            installer.logWarn("Failed to inject FxPlusModule item into Home overflow menu", err)
        }
    }

    private fun hasHomeMenuEntry(hm: HomeMenuBindings, overflowMenu: Any): Boolean {
        val getTitle = hm.menuItemGetTitleMethod ?: return false
        val titleOwner = getTitle.declaringClass

        val itemsList = runCatching { hm.menuModelItemsListField?.get(overflowMenu) as? List<*> }
            .getOrNull()
        if (itemsList != null) {
            for (item in itemsList) {
                if (item != null && titleOwner.isInstance(item)) {
                    val title = runCatching { getTitle.invoke(item)?.toString() }.getOrNull()
                    if (title == HOME_MENU_ENTRY_TITLE) return true
                }
            }
            return false
        }

        val sizeMethod = hm.menuModelSizeMethod ?: return false
        val itemMethod = hm.menuModelItemMethod ?: return false
        val size = (runCatching { sizeMethod.invoke(overflowMenu) as? Int }.getOrNull()) ?: 0
        for (i in 0 until size) {
            val item = runCatching { itemMethod.invoke(overflowMenu, i) }.getOrNull() ?: continue
            if (titleOwner.isInstance(item)) {
                val title = runCatching { getTitle.invoke(item)?.toString() }.getOrNull()
                if (title == HOME_MENU_ENTRY_TITLE) return true
            }
        }
        return false
    }

    private fun createMenuDivider(hm: HomeMenuBindings, overflowMenu: Any): Any? {
        hm.menuDividerCtor?.let { ctor ->
            val created = runCatching { ctor.newInstance() }.getOrNull()
            if (created != null) return created
        }
        val dividerClass = hm.menuDividerClass ?: return null
        val itemsList = runCatching { hm.menuModelItemsListField?.get(overflowMenu) as? List<*> }
            .getOrNull()
        val existing = itemsList?.firstOrNull { it != null && dividerClass.isInstance(it) }
        if (existing != null) return existing

        return runCatching {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = theUnsafeField.get(null)
            unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, dividerClass)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun injectPreferenceEntry(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        activity: PreferenceActivity
    ) {
        runCatching {
            val screen: PreferenceScreen = activity.preferenceScreen ?: return
            if (screen.findPreference(PREF_KEY_ENTRY) != null) {
                updateEntrySummary(activity)
                return
            }

            val category = PreferenceCategory(activity).apply {
                title = CATEGORY_TITLE
                order = -1000
            }
            screen.addPreference(category)

            val pref = createLabelPreference(symbols, activity).apply {
                key = PREF_KEY_ENTRY
                title = ENTRY_TITLE
                summary = buildSummary(FxConfigBridge.current)
                order = -999
                setOnPreferenceClickListener {
                    showNativeSettingsDialog(symbols, installer, activity)
                    true
                }
            }
            category.addPreference(pref)
            injectedPrefRef = WeakReference(pref)
        }.onFailure { err ->
            installer.logWarn("Failed to inject FX settings entry", err)
        }
    }

    @Suppress("DEPRECATION")
    private fun createLabelPreference(symbols: ResolvedSymbols, activity: PreferenceActivity): Preference {
        val ctor = symbols.settings.labelPreferenceCtor
        if (ctor != null) {
            runCatching {
                val created = ctor.newInstance(activity)
                if (created is Preference) return created
            }
        }
        return Preference(activity)
    }

    @Suppress("DEPRECATION")
    private fun updateEntrySummary(activity: PreferenceActivity) {
        val summary = buildSummary(FxConfigBridge.current)
        val cachedPref = injectedPrefRef?.get()
        if (cachedPref != null) {
            cachedPref.summary = summary
        } else {
            activity.preferenceScreen?.findPreference(PREF_KEY_ENTRY)?.summary = summary
        }
    }

    private fun buildSummary(cfg: ModuleConfig): String {
        val swipe = if (cfg.unlockLargeImageSwipe) "大图解锁开" else "大图解锁关"
        val prefetch = if (cfg.enableAdjacentPrefetch) {
            when (cfg.prefetchLimitMode) {
                PrefetchLimitMode.COUNT_ONLY -> "预加载 ${cfg.prefetchLimitMode.displayName} N±${cfg.prefetchCount}"
                PrefetchLimitMode.CAPACITY_ONLY -> "预加载 ${cfg.prefetchLimitMode.displayName} ${formatMb(cfg.prefetchDiskCacheMb)}"
                PrefetchLimitMode.BOTH -> "预加载 N±${cfg.prefetchCount} / ${formatMb(cfg.prefetchDiskCacheMb)}"
            }
        } else {
            "预加载关"
        }
        val hwRender = if (cfg.disableSoftwareImageRendering || cfg.enableHardwareGifPlayback) {
            "看图硬解开"
        } else {
            "看图硬解关"
        }
        val dataAccess = if (cfg.enableAndroidDataAccess) "目录增强开" else "目录增强关"
        return "$swipe · $prefetch · $hwRender · $dataAccess"
    }

    internal fun resolveLivePalette(
        symbols: ResolvedSymbols,
        context: Context,
        dialogObj: Any? = null
    ): FxDialogPalette {
        val st = symbols.settings
        val uiObj = runCatching {
            if (dialogObj != null) {
                st.fxDialogUiField?.get(dialogObj)
            } else {
                null
            } ?: st.uiGetForContext?.invoke(null, context)
        }.getOrNull()

        val systemLight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES
        val isLight = runCatching {
            (uiObj?.let { st.uiIsWindowLightField?.get(it) as? Boolean })
                ?: (dialogObj?.let { st.fxDialogIsBackgroundLight?.invoke(it) as? Boolean })
        }.getOrNull() ?: systemLight

        val rawWindowBg = runCatching { uiObj?.let { st.uiWindowBgMethod?.invoke(it) as? Int } }.getOrNull() ?: 0
        val rawBoxBg = runCatching { uiObj?.let { st.uiBoxBgField?.get(it) as? Int } }.getOrNull() ?: 0
        val rawBoxPressedBg = runCatching { uiObj?.let { st.uiBoxPressedBgField?.get(it) as? Int } }.getOrNull() ?: 0
        val rawTextPrimary = runCatching { uiObj?.let { st.uiTextPrimaryField?.get(it) as? Int } }.getOrNull() ?: 0
        val rawTextSecondary = runCatching { uiObj?.let { st.uiTextSecondaryField?.get(it) as? Int } }.getOrNull() ?: 0
        val rawTrimAccent = runCatching { uiObj?.let { st.uiTrimAccentMethod?.invoke(it) as? Int } }.getOrNull() ?: 0
        val rawTrimBase = runCatching { uiObj?.let { st.uiTrimBaseMethod?.invoke(it) as? Int } }.getOrNull() ?: 0

        val fallbackWindowBg = if (isLight) Color.parseColor("#FAFBFD") else Color.parseColor("#181C24")
        val windowBg = if (rawWindowBg != 0 && Color.alpha(rawWindowBg) > 0) {
            compositeOver(rawWindowBg, fallbackWindowBg)
        } else {
            fallbackWindowBg
        }

        val fallbackAccent = if (isLight) Color.parseColor("#0D6EFD") else Color.parseColor("#4D94FF")
        val rawAccent = when {
            rawTrimAccent != 0 && Color.alpha(rawTrimAccent) > 0 -> rawTrimAccent
            rawTrimBase != 0 && Color.alpha(rawTrimBase) > 0 -> rawTrimBase
            else -> fallbackAccent
        }
        val accent = compositeOver(rawAccent, windowBg)

        val fallbackPrimary = if (isLight) Color.parseColor("#181C24") else Color.parseColor("#F1F4F9")
        val textPrimary = if (rawTextPrimary != 0 && Color.alpha(rawTextPrimary) > 0) {
            compositeOver(rawTextPrimary, windowBg)
        } else {
            fallbackPrimary
        }

        val fallbackSecondary = if (isLight) Color.parseColor("#5B6472") else Color.parseColor("#A4AFBF")
        val textSecondary = if (rawTextSecondary != 0 && Color.alpha(rawTextSecondary) > 0) {
            compositeOver(rawTextSecondary, windowBg)
        } else {
            fallbackSecondary
        }

        val rawCardComp = if (rawBoxBg != 0 && Color.alpha(rawBoxBg) > 0) {
            compositeOver(rawBoxBg, windowBg)
        } else {
            0
        }
        val cardBg = if (rawCardComp != 0 && rawCardComp != windowBg) {
            rawCardComp
        } else {
            blendColors(windowBg, if (isLight) Color.BLACK else Color.WHITE, if (isLight) 0.05f else 0.08f)
        }

        val rawPressedComp = if (rawBoxPressedBg != 0 && Color.alpha(rawBoxPressedBg) > 0) {
            compositeOver(rawBoxPressedBg, windowBg)
        } else {
            0
        }
        val chipBg = if (rawPressedComp != 0 && rawPressedComp != cardBg && rawPressedComp != windowBg) {
            blendColors(cardBg, rawPressedComp, 0.7f)
        } else {
            blendColors(cardBg, if (isLight) Color.BLACK else Color.WHITE, if (isLight) 0.08f else 0.12f)
        }

        val lum = (0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent)) / 255.0
        val chipSelectedText = if (lum > 0.55) Color.parseColor("#11161C") else Color.WHITE
        val divider = blendColors(windowBg, textPrimary, 0.16f)

        return FxDialogPalette(
            isLight = isLight,
            windowBg = windowBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            cardBg = cardBg,
            chipBg = chipBg,
            chipSelectedBg = accent,
            chipSelectedText = chipSelectedText,
            accent = accent,
            divider = divider
        )
    }

    internal fun styleAlertDialog(dialog: AlertDialog, palette: FxDialogPalette) {
        runCatching {
            val density = dialog.context.resources.displayMetrics.density
            dialog.window?.setBackgroundDrawable(
                GradientDrawable().apply {
                    cornerRadius = 14f * density
                    setColor(palette.windowBg)
                    setStroke((1f * density).toInt().coerceAtLeast(1), palette.divider)
                }
            )
            val titleId = dialog.context.resources.getIdentifier("alertTitle", "id", "android")
            if (titleId != 0) {
                dialog.findViewById<TextView>(titleId)?.setTextColor(palette.textPrimary)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(palette.accent)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(palette.textSecondary)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(palette.textSecondary)
        }
    }

    fun showNativeSettingsDialog(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        activity: Activity
    ) {
        FxConfigBridge.attachContext(activity)
        GoogleDriveOAuthHook.attachContext(activity)

        val st = symbols.settings
        val ctor = st.fxDialogCtor
        val styleObj = st.fxDialogDefaultWithClose
        val getLayout = st.fxDialogGetDefaultContentLayout
        val showMethod = st.fxDialogShow

        if (ctor == null || styleObj == null || getLayout == null || showMethod == null) {
            showFallbackAlertDialog(symbols, activity)
            return
        }

        val dialogObj = runCatching { ctor.newInstance(activity, styleObj) }.getOrNull() ?: run {
            showFallbackAlertDialog(symbols, activity)
            return
        }

        runCatching {
            st.fxDialogSetHeader?.invoke(dialogObj, DIALOG_TITLE)
            st.fxDialogSetDescription?.invoke(dialogObj, DIALOG_DESCRIPTION)
            val palette = resolveLivePalette(symbols, activity, dialogObj)
            val rootLayout = getLayout.invoke(dialogObj) as LinearLayout
            populateDialogContent(
                activity = activity,
                container = rootLayout,
                palette = palette,
                onConfigUpdated = {
                    @Suppress("DEPRECATION")
                    if (activity is PreferenceActivity) {
                        updateEntrySummary(activity)
                    }
                }
            )
            showMethod.invoke(dialogObj)
        }.onFailure { err ->
            installer.logWarn("Native FX dialog construction failed, using AlertDialog fallback", err)
            showFallbackAlertDialog(symbols, activity)
        }
    }

    private fun showFallbackAlertDialog(symbols: ResolvedSymbols, activity: Activity) {
        val palette = resolveLivePalette(symbols, activity, dialogObj = null)
        val scroll = ScrollView(activity)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 16)
            setPadding(pad, pad / 2, pad, pad)
        }
        scroll.addView(container)
        val themeRes = if (palette.isLight) {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        }
        val dialog = AlertDialog.Builder(activity, themeRes)
            .setTitle(DIALOG_TITLE)
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .create()
        populateDialogContent(
            activity = activity,
            container = container,
            palette = palette,
            onConfigUpdated = {
                @Suppress("DEPRECATION")
                if (activity is PreferenceActivity) {
                    updateEntrySummary(activity)
                }
            }
        )
        dialog.show()
        styleAlertDialog(dialog, palette)
    }

    private fun populateDialogContent(
        activity: Activity,
        container: LinearLayout,
        palette: FxDialogPalette,
        onConfigUpdated: () -> Unit
    ) {
        lateinit var render: () -> Unit
        var probeTriggered = false

        fun commitUpdate(transform: (ModuleConfig) -> ModuleConfig) {
            FxConfigBridge.updateFromFxDialog(activity, transform)
            onConfigUpdated()
            render()
        }

        fun refreshBackendStatusAsync() {
            val actRef = WeakReference(activity)
            val appCtx = activity.applicationContext ?: activity
            Thread {
                val status = probeStorageBackendStatus(appCtx)
                cachedBackendStatus = status
                val act = actRef.get() ?: return@Thread
                if (!act.isFinishing && !act.isDestroyed) {
                    act.runOnUiThread {
                        if (!act.isFinishing && !act.isDestroyed) {
                            render()
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "FxPlus-BackendProbe"
                start()
            }
        }

        render = {
            container.removeAllViews()
            val cfg = FxConfigBridge.current

            addSectionTitle(activity, container, "远程大图与预加载", palette)
            addSwitchRow(
                activity, container, palette,
                title = "解除远程大图切换限制",
                summary = "允许在远程目录左右切换 4 MB 以上原图",
                checked = cfg.unlockLargeImageSwipe
            ) { checked -> commitUpdate { it.copy(unlockLargeImageSwipe = checked) } }

            addPresetChipRow(
                activity, container, palette,
                title = "切换大小上限",
                currentLabel = formatMbOrUnlimited(cfg.maxSwipeImageSizeMb),
                presets = ConfigContract.MAX_SWIPE_IMAGE_SIZE_MB_PRESETS,
                selectedValue = cfg.maxSwipeImageSizeMb,
                formatValue = ::formatMbOrUnlimited,
                enabled = cfg.unlockLargeImageSwipe,
                onSelect = { mb -> commitUpdate { it.copy(maxSwipeImageSizeMb = mb) } },
                onCustom = {
                    promptNumericInput(
                        activity = activity,
                        palette = palette,
                        title = "切换大小上限",
                        hint = "输入 0 表示不限制，单位 MB",
                        initial = cfg.maxSwipeImageSizeMb,
                        minVal = ConfigContract.MIN_MAX_SWIPE_IMAGE_SIZE_MB,
                        maxVal = 1_048_576
                    ) { mb -> commitUpdate { it.copy(maxSwipeImageSizeMb = mb) } }
                }
            )

            addSwitchRow(
                activity, container, palette,
                title = "启用相邻图片预加载",
                summary = "按浏览方向后台缓存原图字节流",
                checked = cfg.enableAdjacentPrefetch
            ) { checked -> commitUpdate { it.copy(enableAdjacentPrefetch = checked) } }

            addModeRow(
                activity, container, palette,
                currentMode = cfg.prefetchLimitMode,
                enabled = cfg.enableAdjacentPrefetch
            ) { mode -> commitUpdate { it.copy(prefetchLimitMode = mode) } }

            addPresetChipRow(
                activity, container, palette,
                title = "预加载个数",
                currentLabel = "前后各 ${cfg.prefetchCount} 张",
                presets = ConfigContract.PREFETCH_COUNT_PRESETS,
                selectedValue = cfg.prefetchCount,
                formatValue = { "N±$it" },
                enabled = cfg.enableAdjacentPrefetch,
                onSelect = { count -> commitUpdate { it.copy(prefetchCount = count) } },
                onCustom = {
                    promptNumericInput(
                        activity = activity,
                        palette = palette,
                        title = "预加载个数",
                        hint = "按 N±k 范围预加载，可输入 1 到 15",
                        initial = cfg.prefetchCount,
                        minVal = ConfigContract.MIN_PREFETCH_COUNT,
                        maxVal = ConfigContract.MAX_PREFETCH_COUNT
                    ) { count -> commitUpdate { it.copy(prefetchCount = count) } }
                }
            )

            addPresetChipRow(
                activity, container, palette,
                title = "预加载容量上限",
                currentLabel = formatMb(cfg.prefetchDiskCacheMb),
                presets = ConfigContract.PREFETCH_DISK_CACHE_MB_PRESETS,
                selectedValue = cfg.prefetchDiskCacheMb,
                formatValue = ::formatMb,
                enabled = cfg.enableAdjacentPrefetch,
                onSelect = { mb -> commitUpdate { it.copy(prefetchDiskCacheMb = mb) } },
                onCustom = {
                    promptNumericInput(
                        activity = activity,
                        palette = palette,
                        title = "预加载容量上限",
                        hint = "范围 32 到 16384 MB",
                        initial = cfg.prefetchDiskCacheMb,
                        minVal = ConfigContract.MIN_PREFETCH_DISK_CACHE_MB,
                        maxVal = ConfigContract.MAX_PREFETCH_DISK_CACHE_MB
                    ) { mb -> commitUpdate { it.copy(prefetchDiskCacheMb = mb) } }
                }
            )

            addPresetChipRow(
                activity, container, palette,
                title = "单文件预加载上限",
                currentLabel = formatMbOrUnlimited(cfg.prefetchMaxSingleFileMb),
                presets = ConfigContract.PREFETCH_MAX_SINGLE_FILE_MB_PRESETS,
                selectedValue = cfg.prefetchMaxSingleFileMb,
                formatValue = ::formatMbOrUnlimited,
                enabled = cfg.enableAdjacentPrefetch,
                onSelect = { mb -> commitUpdate { it.copy(prefetchMaxSingleFileMb = mb) } },
                onCustom = {
                    promptNumericInput(
                        activity = activity,
                        palette = palette,
                        title = "单文件预加载上限",
                        hint = "输入 0 表示不限制，单位 MB",
                        initial = cfg.prefetchMaxSingleFileMb,
                        minVal = ConfigContract.MIN_PREFETCH_MAX_SINGLE_FILE_MB,
                        maxVal = ConfigContract.MAX_PREFETCH_DISK_CACHE_MB
                    ) { mb -> commitUpdate { it.copy(prefetchMaxSingleFileMb = mb) } }
                }
            )

            addPresetChipRow(
                activity, container, palette,
                title = "预加载并发数",
                currentLabel = "${cfg.prefetchConcurrency} 线程",
                presets = ConfigContract.PREFETCH_CONCURRENCY_PRESETS,
                selectedValue = cfg.prefetchConcurrency,
                formatValue = { "$it 线程" },
                enabled = cfg.enableAdjacentPrefetch,
                onSelect = { count -> commitUpdate { it.copy(prefetchConcurrency = count) } },
                onCustom = null
            )

            addSwitchRow(
                activity, container, palette,
                title = "仅在 Wi-Fi 下预加载",
                summary = "移动数据网络下暂停后台预加载",
                checked = cfg.prefetchWifiOnly,
                enabled = cfg.enableAdjacentPrefetch
            ) { checked -> commitUpdate { it.copy(prefetchWifiOnly = checked) } }

            addDivider(activity, container, palette)
            addSectionTitle(activity, container, "远程缩略图与体验增强", palette)

            addSwitchRow(
                activity, container, palette,
                title = "启用远程缩略图",
                summary = "放开远程目录图片缩略图大小限制",
                checked = cfg.enableRemoteThumbnails
            ) { checked -> commitUpdate { it.copy(enableRemoteThumbnails = checked) } }

            addPresetChipRow(
                activity, container, palette,
                title = "缩略图源图上限",
                currentLabel = formatMbOrUnlimited(cfg.maxRemoteThumbnailSizeMb),
                presets = ConfigContract.MAX_REMOTE_THUMBNAIL_SIZE_MB_PRESETS,
                selectedValue = cfg.maxRemoteThumbnailSizeMb,
                formatValue = ::formatMbOrUnlimited,
                enabled = cfg.enableRemoteThumbnails,
                onSelect = { mb -> commitUpdate { it.copy(maxRemoteThumbnailSizeMb = mb) } },
                onCustom = {
                    promptNumericInput(
                        activity = activity,
                        palette = palette,
                        title = "缩略图源图上限",
                        hint = "输入 0 表示不限制，单位 MB",
                        initial = cfg.maxRemoteThumbnailSizeMb,
                        minVal = ConfigContract.MIN_MAX_REMOTE_THUMBNAIL_SIZE_MB,
                        maxVal = 16_384
                    ) { mb -> commitUpdate { it.copy(maxRemoteThumbnailSizeMb = mb) } }
                }
            )

            addSwitchRow(
                activity, container, palette,
                title = "高清缩略图",
                summary = "提升目录及查看器缩略图解码像素上限",
                checked = cfg.highResThumbnails,
                enabled = cfg.enableRemoteThumbnails
            ) { checked -> commitUpdate { it.copy(highResThumbnails = checked) } }

            addDivider(activity, container, palette)
            addSectionTitle(activity, container, "看图与动图渲染", palette)

            addSwitchRow(
                activity, container, palette,
                title = "禁用大图软件渲染",
                summary = "强制硬件加速直绘，超长与超高像素原图自动分块",
                checked = cfg.disableSoftwareImageRendering
            ) { checked -> commitUpdate { it.copy(disableSoftwareImageRendering = checked) } }

            addSwitchRow(
                activity, container, palette,
                title = "动图硬件加速重绘",
                summary = "使用系统硬件解码播放 GIF，修复发糊与掉帧",
                checked = cfg.enableHardwareGifPlayback
            ) { checked -> commitUpdate { it.copy(enableHardwareGifPlayback = checked) } }

            addDivider(activity, container, palette)
            addSectionTitle(activity, container, "目录与体验增强", palette)

            addSwitchRow(
                activity, container, palette,
                title = "Android/data 与 obb 访问增强",
                summary = "优先使用 Root，无 Root 时回退 Shizuku",
                checked = cfg.enableAndroidDataAccess
            ) { checked -> commitUpdate { it.copy(enableAndroidDataAccess = checked) } }

            addInlineBackendStatusCard(
                activity = activity,
                container = container,
                palette = palette,
                status = cachedBackendStatus,
                onRequestShizuku = {
                    requestShizukuPermissionAsync(activity) {
                        refreshBackendStatusAsync()
                    }
                },
                onRefresh = { refreshBackendStatusAsync() }
            )

            addSwitchRow(
                activity, container, palette,
                title = "解除文本预览截断",
                summary = "放宽内置文本预览的长度与行数上限",
                checked = cfg.unlockTextPreviewLimits
            ) { checked -> commitUpdate { it.copy(unlockTextPreviewLimits = checked) } }

            addSwitchRow(
                activity, container, palette,
                title = "调试日志",
                summary = "向 Xposed 日志输出详细诊断信息",
                checked = cfg.debugLogging
            ) { checked -> commitUpdate { it.copy(debugLogging = checked) } }

            addDivider(activity, container, palette)
            addSectionTitle(activity, container, "云端存储", palette)

            addActionRow(
                context = activity,
                container = container,
                palette = palette,
                title = "Google Drive 自定义 Client ID",
                summary = GoogleDriveOAuthHook.getCustomClientId(activity).let { customId ->
                    if (customId.isNotEmpty()) {
                        "已启用自定义 Client ID（点击修改或清除）"
                    } else {
                        "解决内置 OAuth 被停用问题（未配置）"
                    }
                },
                actionLabel = "配置"
            ) {
                GoogleDriveOAuthHook.showConfigDialog(activity, palette) {
                    render()
                }
            }

            addDivider(activity, container, palette)
            addCacheFooter(
                activity = activity,
                container = container,
                palette = palette,
                onCleared = { render() }
            )
        }

        render()
        if (!probeTriggered) {
            probeTriggered = true
            refreshBackendStatusAsync()
        }
    }

    private fun probeStorageBackendStatus(appContext: Context): StorageBackendStatus {
        val ping = runCatching {
            appContext.contentResolver.call(
                Uri.parse(ShizukuBridgeContract.CONTENT_URI_BASE),
                ShizukuBridgeContract.METHOD_PING,
                null,
                null
            )
        }.getOrNull()

        val shizukuAvailable = ping?.getBoolean(ShizukuBridgeContract.KEY_SHIZUKU_AVAILABLE, false) ?: false
        val shizukuGranted = ping?.getBoolean(ShizukuBridgeContract.KEY_SHIZUKU_GRANTED, false) ?: false
        val serviceBound = ping?.getBoolean(ShizukuBridgeContract.KEY_SERVICE_BOUND, false) ?: false
        val privilegedUid = ping?.getInt(ShizukuBridgeContract.KEY_PRIVILEGED_UID, -1) ?: -1
        val shizukuInstalled = (ping?.getBoolean(KEY_SHIZUKU_INSTALLED, false) == true) ||
            shizukuAvailable ||
            isPackageInstalled(appContext, ShizukuBridgeContract.SHIZUKU_MANAGER_PACKAGE)
        val rootAvailable = if (!shizukuGranted) {
            runCatching { PrivilegedStorageClient.hasAnyBackend(appContext) }.getOrDefault(false)
        } else {
            false
        }
        return StorageBackendStatus(
            bridgeReachable = ping != null,
            shizukuInstalled = shizukuInstalled,
            shizukuAvailable = shizukuAvailable,
            shizukuGranted = shizukuGranted,
            serviceBound = serviceBound,
            privilegedUid = privilegedUid,
            rootAvailable = rootAvailable
        )
    }

    private fun requestShizukuPermissionAsync(
        activity: Activity,
        onCompleted: () -> Unit
    ) {
        val actRef = WeakReference(activity)
        val appCtx = activity.applicationContext ?: activity
        Thread {
            val reply = runCatching {
                appCtx.contentResolver.call(
                    Uri.parse(ShizukuBridgeContract.CONTENT_URI_BASE),
                    METHOD_REQUEST_PERMISSION,
                    null,
                    null
                )
            }.getOrNull()
            val act = actRef.get() ?: return@Thread
            if (!act.isFinishing && !act.isDestroyed) {
                act.runOnUiThread {
                    if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                    val msg = when {
                        reply == null -> "无法连接存储桥接，请确认模块已启用"
                        reply.getBoolean(ShizukuBridgeContract.KEY_SHIZUKU_GRANTED, false) -> "Shizuku 已授权"
                        !reply.getBoolean(ShizukuBridgeContract.KEY_SHIZUKU_AVAILABLE, false) -> "Shizuku 服务未运行，请先启动 Shizuku"
                        else -> "已发起 Shizuku 授权请求，请在弹窗中确认"
                    }
                    Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
                    onCompleted()
                    act.window?.decorView?.postDelayed({
                        val delayedAct = actRef.get()
                        if (delayedAct != null && !delayedAct.isFinishing && !delayedAct.isDestroyed) {
                            onCompleted()
                        }
                    }, 1_500L)
                }
            }
        }.apply {
            isDaemon = true
            name = "FxPlus-ShizukuPerm"
            start()
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun addInlineBackendStatusCard(
        activity: Activity,
        container: LinearLayout,
        palette: FxDialogPalette,
        status: StorageBackendStatus?,
        onRequestShizuku: () -> Unit,
        onRefresh: () -> Unit
    ) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 10)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 8).toFloat()
                setColor(palette.cardBg)
                setStroke(dp(activity, 1), palette.divider)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(activity, 4)
                bottomMargin = dp(activity, 6)
            }
        }

        val statusSummary = when {
            status == null -> "授权状态：正在检测 Root / Shizuku..."
            status.rootAvailable -> {
                if (status.shizukuAvailable) {
                    "授权状态：Root FUSE 已就绪（Shizuku 已运行但未授权）"
                } else {
                    "授权状态：Root FUSE 已就绪"
                }
            }
            status.shizukuGranted && status.serviceBound ->
                "授权状态：Shizuku 存储桥接已就绪（uid ${status.privilegedUid}）"
            status.shizukuGranted ->
                "授权状态：Shizuku 已授权（正在绑定存储服务）"
            status.shizukuAvailable ->
                "授权状态：Shizuku 已运行，尚未授予模块权限"
            status.shizukuInstalled ->
                "授权状态：已安装 Shizuku，但服务未运行"
            !status.bridgeReachable ->
                "授权状态：未检测到 Root，且无法连接存储桥接"
            else ->
                "授权状态：未检测到 Root 与 Shizuku"
        }

        card.addView(TextView(activity).apply {
            text = statusSummary
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(
                if (status != null && (status.rootAvailable || status.shizukuGranted)) {
                    palette.textPrimary
                } else {
                    palette.textSecondary
                }
            )
        })

        val showRequestBtn = status != null &&
            !status.shizukuGranted &&
            status.shizukuAvailable
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(activity, 8), 0, 0)
        }
        if (showRequestBtn) {
            btnRow.addView(makeThemedButton(
                context = activity,
                label = "请求 Shizuku 授权",
                primary = true,
                palette = palette,
                onClick = onRequestShizuku
            ).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                    marginEnd = dp(activity, 6)
                }
            })
        }
        btnRow.addView(makeThemedButton(
            context = activity,
            label = "刷新状态",
            primary = false,
            palette = palette,
            onClick = onRefresh
        ).apply {
            layoutParams = if (showRequestBtn) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f).apply {
                    marginStart = dp(activity, 6)
                }
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        })
        card.addView(btnRow)
        container.addView(card)
    }

    private fun addSectionTitle(
        context: Context,
        container: LinearLayout,
        text: String,
        palette: FxDialogPalette
    ) {
        val tv = TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.accent)
            setPadding(0, dp(context, 6), 0, dp(context, 4))
        }
        container.addView(tv)
    }

    private fun addDivider(context: Context, container: LinearLayout, palette: FxDialogPalette) {
        val v = View(context).apply {
            setBackgroundColor(palette.divider)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1))
            lp.setMargins(0, dp(context, 8), 0, dp(context, 8))
            layoutParams = lp
        }
        container.addView(v)
    }

    private fun addSwitchRow(
        context: Context,
        container: LinearLayout,
        palette: FxDialogPalette,
        title: String,
        summary: String,
        checked: Boolean,
        enabled: Boolean = true,
        onToggle: (Boolean) -> Unit
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 6), 0, dp(context, 6))
            alpha = if (enabled) 1f else 0.45f
        }
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(palette.textPrimary)
        })
        textCol.addView(TextView(context).apply {
            text = summary
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(palette.textSecondary)
        })
        val sw = Switch(context).apply {
            isChecked = checked
            isEnabled = enabled
            val uncheckedThumb = blendColors(palette.cardBg, palette.textSecondary, 0.7f)
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette.accent, uncheckedThumb)
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(withAlpha(palette.accent, 0x77), withAlpha(palette.textSecondary, 0x44))
            )
            setOnCheckedChangeListener { _, isChecked ->
                if (enabled) onToggle(isChecked)
            }
        }
        row.setOnClickListener {
            if (enabled) sw.toggle()
        }
        row.addView(textCol)
        row.addView(sw)
        container.addView(row)
    }

    private fun addActionRow(
        context: Context,
        container: LinearLayout,
        palette: FxDialogPalette,
        title: String,
        summary: String,
        actionLabel: String,
        onClick: () -> Unit
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = dp(context, 10)
            val padV = dp(context, 10)
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 8).toFloat()
                setColor(palette.cardBg)
                setStroke(dp(context, 1), palette.divider)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 4)
                bottomMargin = dp(context, 4)
            }
            setOnClickListener { onClick() }
        }
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 8)
            }
        }
        textCol.addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(palette.textPrimary)
        })
        textCol.addView(TextView(context).apply {
            text = summary
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(palette.textSecondary)
        })
        val badge = TextView(context).apply {
            text = actionLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.chipSelectedText)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 8).toFloat()
                setColor(palette.accent)
            }
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
        }
        row.addView(textCol)
        row.addView(badge)
        container.addView(row)
    }

    private fun addModeRow(
        context: Context,
        container: LinearLayout,
        palette: FxDialogPalette,
        currentMode: PrefetchLimitMode,
        enabled: Boolean,
        onSelect: (PrefetchLimitMode) -> Unit
    ) {
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 6), 0, dp(context, 6))
            alpha = if (enabled) 1f else 0.45f
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = "预加载限制模式"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(palette.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(context).apply {
            text = currentMode.displayName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.accent)
        })
        block.addView(header)

        val hScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(context, 6), 0, 0)
        }
        val chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (mode in PrefetchLimitMode.entries) {
            chipRow.addView(
                makeChip(
                    context = context,
                    label = mode.displayName,
                    selected = mode == currentMode,
                    enabled = enabled,
                    palette = palette
                ) { onSelect(mode) }
            )
        }
        hScroll.addView(chipRow)
        block.addView(hScroll)
        container.addView(block)
    }

    private fun addPresetChipRow(
        context: Context,
        container: LinearLayout,
        palette: FxDialogPalette,
        title: String,
        currentLabel: String,
        presets: List<Int>,
        selectedValue: Int,
        formatValue: (Int) -> String,
        enabled: Boolean,
        onSelect: (Int) -> Unit,
        onCustom: (() -> Unit)?
    ) {
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 6), 0, dp(context, 6))
            alpha = if (enabled) 1f else 0.45f
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(palette.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(context).apply {
            text = currentLabel
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.accent)
        })
        block.addView(header)

        val hScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(context, 6), 0, 0)
        }
        val chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (preset in presets) {
            chipRow.addView(
                makeChip(
                    context = context,
                    label = formatValue(preset),
                    selected = selectedValue == preset,
                    enabled = enabled,
                    palette = palette
                ) { onSelect(preset) }
            )
        }
        if (onCustom != null) {
            val isCustom = selectedValue !in presets
            chipRow.addView(
                makeChip(
                    context = context,
                    label = if (isCustom) "自定义(${formatValue(selectedValue)})" else "自定义",
                    selected = isCustom,
                    enabled = enabled,
                    palette = palette,
                    onClick = onCustom
                )
            )
        }
        hScroll.addView(chipRow)
        block.addView(hScroll)
        container.addView(block)
    }

    private fun addCacheFooter(
        activity: Activity,
        container: LinearLayout,
        palette: FxDialogPalette,
        onCleared: () -> Unit
    ) {
        val mgr = PrefetchCacheManager.getOrCreate(activity.applicationContext) { FxConfigBridge.current }
        val stats = mgr.stats()
        val usedMb = String.format(Locale.US, "%.1f", stats.totalBytes / (1024.0 * 1024.0))

        container.addView(TextView(activity).apply {
            text = "预加载缓存：$usedMb MB（${stats.entryCount} 个文件）"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(palette.textSecondary)
            setPadding(0, dp(activity, 4), 0, dp(activity, 8))
        })

        val clearBtn = makeThemedButton(
            context = activity,
            label = "清理预加载缓存",
            primary = false,
            palette = palette
        ) {
            mgr.clearCache()
            Toast.makeText(activity, "已清理预加载缓存", Toast.LENGTH_SHORT).show()
            onCleared()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(clearBtn)
    }

    private fun makeChip(
        context: Context,
        label: String,
        selected: Boolean,
        enabled: Boolean,
        palette: FxDialogPalette,
        onClick: () -> Unit
    ): TextView {
        val bg = GradientDrawable().apply {
            cornerRadius = dp(context, 8).toFloat()
            setColor(if (selected) palette.chipSelectedBg else palette.chipBg)
        }
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(if (selected) palette.chipSelectedText else palette.textPrimary)
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            background = bg
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(context, 8)
            }
            isClickable = enabled
            setOnClickListener {
                if (enabled) onClick()
            }
        }
    }

    internal fun makeThemedButton(
        context: Context,
        label: String,
        primary: Boolean,
        palette: FxDialogPalette,
        onClick: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, if (primary) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (primary) palette.chipSelectedText else palette.textPrimary)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 8).toFloat()
                setColor(if (primary) palette.accent else palette.chipBg)
                if (!primary) {
                    setStroke(dp(context, 1), palette.divider)
                }
            }
            val padH = dp(context, 12)
            val padV = dp(context, 8)
            setPadding(padH, padV, padH, padV)
            minHeight = 0
            minimumHeight = 0
            setOnClickListener { onClick() }
        }
    }

    internal fun makeThemedInputBox(
        context: Context,
        palette: FxDialogPalette,
        hintText: String = "",
        initialText: String = ""
    ): EditText {
        return EditText(context).apply {
            if (hintText.isNotEmpty()) hint = hintText
            if (initialText.isNotEmpty()) {
                setText(initialText)
                setSelection(text.length)
            }
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 8).toFloat()
                setColor(palette.cardBg)
                setStroke(dp(context, 1), palette.divider)
            }
            val pad = dp(context, 10)
            setPadding(pad, pad, pad, pad)
        }
    }

    private fun promptNumericInput(
        activity: Activity,
        palette: FxDialogPalette,
        title: String,
        hint: String,
        initial: Int,
        minVal: Int,
        maxVal: Int,
        onConfirm: (Int) -> Unit
    ) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initial.toString())
            setSelection(text.length)
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textSecondary)
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 8).toFloat()
                setColor(palette.cardBg)
                setStroke(dp(activity, 1), palette.divider)
            }
            val pad = dp(activity, 10)
            setPadding(pad, pad, pad, pad)
        }
        val wrapper = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 16)
            setPadding(pad, dp(activity, 8), pad, 0)
            addView(TextView(activity).apply {
                text = hint
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(palette.textSecondary)
                setPadding(0, 0, 0, dp(activity, 8))
            })
            addView(input)
        }
        val themeRes = if (palette.isLight) {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        }
        val dialog = AlertDialog.Builder(activity, themeRes)
            .setTitle(title)
            .setView(wrapper)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                val parsed = input.text?.toString()?.trim()?.toIntOrNull()
                if (parsed == null || parsed !in minVal..maxVal) {
                    Toast.makeText(activity, "请输入 $minVal 到 $maxVal 之间的整数", Toast.LENGTH_SHORT).show()
                } else {
                    onConfirm(parsed)
                }
            }
            .create()
        dialog.show()
        styleAlertDialog(dialog, palette)
    }

    private fun compositeOver(fg: Int, bg: Int): Int {
        val a = Color.alpha(fg) / 255f
        if (a >= 0.999f) return Color.rgb(Color.red(fg), Color.green(fg), Color.blue(fg))
        if (a <= 0.001f) return Color.rgb(Color.red(bg), Color.green(bg), Color.blue(bg))
        val r = (Color.red(fg) * a + Color.red(bg) * (1f - a)).toInt().coerceIn(0, 255)
        val g = (Color.green(fg) * a + Color.green(bg) * (1f - a)).toInt().coerceIn(0, 255)
        val b = (Color.blue(fg) * a + Color.blue(bg) * (1f - a)).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun blendColors(base: Int, overlay: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        val r = (Color.red(base) * (1f - t) + Color.red(overlay) * t).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * (1f - t) + Color.green(overlay) * t).toInt().coerceIn(0, 255)
        val b = (Color.blue(base) * (1f - t) + Color.blue(overlay) * t).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun formatMbOrUnlimited(mb: Int): String = if (mb <= 0) "不限" else formatMb(mb)

    private fun formatMb(mb: Int): String {
        return if (mb >= 1024 && mb % 1024 == 0) "${mb / 1024} GB" else "$mb MB"
    }

    internal fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
