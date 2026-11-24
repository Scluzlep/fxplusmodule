package com.scluzlep.fxplusmodule.xposed

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.scluzlep.fxplusmodule.cache.PrefetchCacheManager
import com.scluzlep.fxplusmodule.config.ConfigContract
import com.scluzlep.fxplusmodule.config.ModuleConfig
import com.scluzlep.fxplusmodule.config.PrefetchLimitMode
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Injects native `FX Plus 增强设置` into FX's `MainPrefActivity` (`9012` & `9108`)
 * and builds a theme-aware FX native dialog (`nextapp.fx.ui.widget.k` / `n`) exposing all
 * live toggles, preset + custom inputs for `预加载个数` and `预加载容量上限`, cache cleanup,
 * and shortcut to the standalone `FX Plus` app.
 */
object FxSettingsInjectorHook {

    private const val PREF_KEY_ENTRY = "fxplus_native_settings_entry"
    private const val CATEGORY_TITLE = "FX Plus 增强"
    private const val ENTRY_TITLE = "FX Plus 增强设置"

    @Volatile
    private var injectedPrefRef: WeakReference<Preference>? = null

    fun install(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val st = symbols.settings

        installer.install("settings:MainPrefActivity.onCreate", st.mainPrefOnCreate) { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? PreferenceActivity
            if (activity != null) {
                FxConfigBridge.attachContext(activity)
                injectPreferenceEntry(symbols, installer, activity)
            }
            result
        }

        installer.install("settings:MainPrefActivity.onResume", st.mainPrefOnResume) { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? PreferenceActivity
            if (activity != null) {
                FxConfigBridge.attachContext(activity)
                updateEntrySummary(activity)
            }
            result
        }
    }

    private fun injectPreferenceEntry(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        activity: PreferenceActivity
    ) {
        runCatching {
            @Suppress("DEPRECATION")
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

    private fun updateEntrySummary(activity: PreferenceActivity) {
        val summary = buildSummary(FxConfigBridge.current)
        val cachedPref = injectedPrefRef?.get()
        if (cachedPref != null) {
            cachedPref.summary = summary
        } else {
            @Suppress("DEPRECATION")
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

    private data class DialogPalette(
        val isLight: Boolean,
        val textPrimary: Int,
        val textSecondary: Int,
        val cardBg: Int,
        val chipBg: Int,
        val chipSelectedBg: Int,
        val chipSelectedText: Int,
        val accent: Int,
        val divider: Int
    ) {
        companion object {
            fun fromBackgroundLight(light: Boolean): DialogPalette {
                return if (light) {
                    DialogPalette(
                        isLight = true,
                        textPrimary = Color.parseColor("#181C24"),
                        textSecondary = Color.parseColor("#5B6472"),
                        cardBg = Color.parseColor("#F3F5F9"),
                        chipBg = Color.parseColor("#E3E8F0"),
                        chipSelectedBg = Color.parseColor("#0D6EFD"),
                        chipSelectedText = Color.WHITE,
                        accent = Color.parseColor("#0D6EFD"),
                        divider = Color.parseColor("#DDE3EC")
                    )
                } else {
                    DialogPalette(
                        isLight = false,
                        textPrimary = Color.parseColor("#F1F4F9"),
                        textSecondary = Color.parseColor("#A4AFBF"),
                        cardBg = Color.parseColor("#242933"),
                        chipBg = Color.parseColor("#323947"),
                        chipSelectedBg = Color.parseColor("#4D94FF"),
                        chipSelectedText = Color.parseColor("#0B111C"),
                        accent = Color.parseColor("#4D94FF"),
                        divider = Color.parseColor("#353D4B")
                    )
                }
            }
        }
    }

    private fun showNativeSettingsDialog(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        activity: PreferenceActivity
    ) {
        val st = symbols.settings
        val ctor = st.fxDialogCtor
        val styleObj = st.fxDialogDefaultWithClose
        val getLayout = st.fxDialogGetDefaultContentLayout
        val showMethod = st.fxDialogShow

        if (ctor == null || styleObj == null || getLayout == null || showMethod == null) {
            showFallbackAlertDialog(activity)
            return
        }

        val dialogObj = runCatching { ctor.newInstance(activity, styleObj) }.getOrNull() ?: run {
            showFallbackAlertDialog(activity)
            return
        }

        runCatching {
            st.fxDialogSetHeader?.invoke(dialogObj, ENTRY_TITLE)
            st.fxDialogSetDescription?.invoke(
                dialogObj,
                "配置立即生效并与 FX Plus 独立管理端同步"
            )
            val isLight = (st.fxDialogIsBackgroundLight?.invoke(dialogObj) as? Boolean) ?: true
            val palette = DialogPalette.fromBackgroundLight(isLight)
            val rootLayout = getLayout.invoke(dialogObj) as LinearLayout
            populateDialogContent(
                activity = activity,
                container = rootLayout,
                palette = palette,
                onDismissDialog = { runCatching { st.fxDialogDismiss?.invoke(dialogObj) } },
                onConfigUpdated = { updateEntrySummary(activity) }
            )
            showMethod.invoke(dialogObj)
        }.onFailure { err ->
            installer.logWarn("Native FX dialog construction failed, using AlertDialog fallback", err)
            showFallbackAlertDialog(activity)
        }
    }

    private fun showFallbackAlertDialog(activity: PreferenceActivity) {
        val palette = DialogPalette.fromBackgroundLight(true)
        val scroll = android.widget.ScrollView(activity)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 16)
            setPadding(pad, pad / 2, pad, pad)
        }
        scroll.addView(container)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(ENTRY_TITLE)
            .setView(scroll)
            .setPositiveButton("关闭", null)
            .create()
        populateDialogContent(
            activity = activity,
            container = container,
            palette = palette,
            onDismissDialog = { dialog.dismiss() },
            onConfigUpdated = { updateEntrySummary(activity) }
        )
        dialog.show()
    }

    private fun populateDialogContent(
        activity: Activity,
        container: LinearLayout,
        palette: DialogPalette,
        onDismissDialog: () -> Unit,
        onConfigUpdated: () -> Unit
    ) {
        lateinit var render: () -> Unit

        fun commitUpdate(transform: (ModuleConfig) -> ModuleConfig) {
            FxConfigBridge.updateFromFxDialog(activity, transform)
            onConfigUpdated()
            render()
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
            addCacheAndLaunchFooter(
                activity = activity,
                container = container,
                palette = palette,
                onCleared = { render() },
                onLaunchApp = {
                    onDismissDialog()
                    launchStandaloneApp(activity)
                }
            )
        }

        render()
    }

    private fun addSectionTitle(
        context: Context,
        container: LinearLayout,
        text: String,
        palette: DialogPalette
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

    private fun addDivider(context: Context, container: LinearLayout, palette: DialogPalette) {
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
        palette: DialogPalette,
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

    private fun addModeRow(
        context: Context,
        container: LinearLayout,
        palette: DialogPalette,
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
        palette: DialogPalette,
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

    private fun addCacheAndLaunchFooter(
        activity: Activity,
        container: LinearLayout,
        palette: DialogPalette,
        onCleared: () -> Unit,
        onLaunchApp: () -> Unit
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

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val clearBtn = Button(activity).apply {
            text = "清理预加载缓存"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(activity, 6)
            }
            setOnClickListener {
                mgr.clearCache()
                Toast.makeText(activity, "已清理预加载缓存", Toast.LENGTH_SHORT).show()
                onCleared()
            }
        }
        val launchBtn = Button(activity).apply {
            text = "打开 FX Plus 与 Shizuku"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(activity, 6)
            }
            setOnClickListener { onLaunchApp() }
        }
        btnRow.addView(clearBtn)
        btnRow.addView(launchBtn)
        container.addView(btnRow)
    }

    private fun makeChip(
        context: Context,
        label: String,
        selected: Boolean,
        enabled: Boolean,
        palette: DialogPalette,
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

    private fun promptNumericInput(
        activity: Activity,
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
        }
        val wrapper = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 16)
            setPadding(pad, dp(activity, 8), pad, 0)
            addView(TextView(activity).apply {
                text = hint
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
            addView(input)
        }
        AlertDialog.Builder(activity)
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
            .show()
    }

    private fun launchStandaloneApp(activity: Activity) {
        val intent = Intent().apply {
            component = ComponentName(
                ConfigContract.MODULE_PACKAGE,
                ConfigContract.MAIN_ACTIVITY_CLASS
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            activity.startActivity(intent)
        }.onFailure {
            Toast.makeText(activity, "无法打开 FX Plus 应用，请检查是否已安装", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatMbOrUnlimited(mb: Int): String = if (mb <= 0) "不限" else formatMb(mb)

    private fun formatMb(mb: Int): String {
        return if (mb >= 1024 && mb % 1024 == 0) "${mb / 1024} GB" else "$mb MB"
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
