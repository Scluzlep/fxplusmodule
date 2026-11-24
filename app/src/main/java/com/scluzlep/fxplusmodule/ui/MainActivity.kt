package com.scluzlep.fxplusmodule.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scluzlep.fxplusmodule.config.ConfigContract
import com.scluzlep.fxplusmodule.config.ModuleConfig
import com.scluzlep.fxplusmodule.config.ModuleConfigStore
import com.scluzlep.fxplusmodule.config.PrefetchLimitMode
import com.scluzlep.fxplusmodule.shizuku.ShizukuStatusHelper
import com.scluzlep.fxplusmodule.shizuku.ShizukuStatusSnapshot
import com.scluzlep.fxplusmodule.xposed.ModuleStatus
import com.scluzlep.fxplusmodule.xposed.XposedStatusSnapshot
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    private lateinit var configStore: ModuleConfigStore
    private var configState by mutableStateOf(ModuleConfig())
    private var syncStatusText by mutableStateOf("")
    private var syncStatusColor by mutableStateOf(FxPlusColors.Info)

    private val showNumericDialog = mutableStateOf(false)
    private var activeNumericSpec by mutableStateOf<NumericInputSpec?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configStore = ModuleConfigStore(this)
        ModuleStatus.init(this)
        ShizukuStatusHelper.init(this)
        ModuleStatus.setOnServiceBindListener {
            runOnUiThread {
                configState = configStore.syncWithRemoteIfBound()
            }
        }
        reloadFromStore()

        setContent {
            FxPlusTheme {
                val xposedStatus by ModuleStatus.status.collectAsState()
                val shizukuStatus by ShizukuStatusHelper.status.collectAsState()
                Scaffold(
                    topBar = {
                        TopAppBar(title = "fxplusmodule")
                    }
                ) { innerPadding ->
                    MainScreenContent(
                        innerPadding = innerPadding,
                        status = xposedStatus,
                        shizukuStatus = shizukuStatus,
                        config = configState,
                        syncMessage = syncStatusText,
                        syncColor = syncStatusColor,
                        onRequestScope = { ModuleStatus.requestFxScope() },
                        onHotReloadFx = { ModuleStatus.hotReloadFx() },
                        onRefreshStatus = {
                            ModuleStatus.refresh(this@MainActivity)
                            ShizukuStatusHelper.refresh()
                            reloadFromStore()
                        },
                        onRequestShizukuPermission = {
                            ShizukuStatusHelper.requestPermission()
                        },
                        onRefreshShizuku = {
                            ShizukuStatusHelper.refresh()
                            ShizukuStatusHelper.ensureServiceBoundAsync()
                        },
                        onUpdateConfig = { updater -> persistUpdate(updater) },
                        onOpenNumericInput = { spec ->
                            activeNumericSpec = spec
                            showNumericDialog.value = true
                        },
                        onResetDefaults = {
                            persistUpdate { ModuleConfig() }
                            syncStatusText = "已恢复默认配置"
                            syncStatusColor = FxPlusColors.Info
                        }
                    )
                    NumericInputDialog(
                        show = showNumericDialog,
                        spec = activeNumericSpec
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ModuleStatus.refresh(this)
        ShizukuStatusHelper.refresh()
        reloadFromStore()
    }

    override fun onDestroy() {
        ModuleStatus.setOnServiceBindListener(null)
        super.onDestroy()
    }

    private fun reloadFromStore() {
        configState = configStore.load()
    }

    private fun persistUpdate(transform: (ModuleConfig) -> ModuleConfig) {
        val next = transform(configState)
        val result = configStore.save(next)
        configState = result.config
        if (result.syncedToRemote) {
            syncStatusText = "配置已同步至 Xposed 服务"
            syncStatusColor = FxPlusColors.Success
        } else {
            syncStatusText = "配置已保存至本地，连接 Xposed 服务后自动同步"
            syncStatusColor = FxPlusColors.Warning
        }
    }
}

@Composable
private fun MainScreenContent(
    innerPadding: PaddingValues,
    status: XposedStatusSnapshot,
    shizukuStatus: ShizukuStatusSnapshot,
    config: ModuleConfig,
    syncMessage: String,
    syncColor: Color,
    onRequestScope: () -> Unit,
    onHotReloadFx: () -> Unit,
    onRefreshStatus: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onUpdateConfig: ((ModuleConfig) -> ModuleConfig) -> Unit,
    onOpenNumericInput: (NumericInputSpec) -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SmallTitle(
            text = "模块状态",
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        )
        StatusCard(
            status = status,
            syncMessage = syncMessage,
            syncColor = syncColor,
            onRequestScope = onRequestScope,
            onHotReloadFx = onHotReloadFx,
            onRefreshStatus = onRefreshStatus
        )

        SmallTitle(
            text = "远程大图切换",
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            SuperSwitch(
                checked = config.unlockLargeImageSwipe,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(unlockLargeImageSwipe = checked) }
                },
                title = "解除大图切换限制",
                summary = "允许在远程目录左右切换 4 MB 以上原图"
            )
            SectionDivider()
            PresetNumericOptionBlock(
                title = "切换大小上限",
                currentValueText = formatMbOrUnlimited(config.maxSwipeImageSizeMb),
                currentValue = config.maxSwipeImageSizeMb,
                presets = ConfigContract.MAX_SWIPE_IMAGE_SIZE_MB_PRESETS,
                formatPreset = ::formatMbOrUnlimited,
                enabled = config.unlockLargeImageSwipe,
                onSelectPreset = { mb ->
                    onUpdateConfig { it.copy(maxSwipeImageSizeMb = mb) }
                },
                onRequestCustom = {
                    onOpenNumericInput(
                        NumericInputSpec(
                            title = "切换大小上限",
                            summary = "输入 0 表示不限制，单位 MB",
                            initialValue = config.maxSwipeImageSizeMb,
                            minValue = ConfigContract.MIN_MAX_SWIPE_IMAGE_SIZE_MB,
                            maxValue = 1_048_576,
                            unitLabel = "MB",
                            onConfirm = { mb ->
                                onUpdateConfig { it.copy(maxSwipeImageSizeMb = mb) }
                            }
                        )
                    )
                }
            )
        }

        SmallTitle(
            text = "相邻图片预加载",
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            SuperSwitch(
                checked = config.enableAdjacentPrefetch,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(enableAdjacentPrefetch = checked) }
                },
                title = "启用相邻图片预加载",
                summary = "按浏览方向后台缓存原图字节流"
            )
            SectionDivider()
            PrefetchLimitModeBlock(
                currentMode = config.prefetchLimitMode,
                enabled = config.enableAdjacentPrefetch,
                onSelectMode = { mode ->
                    onUpdateConfig { it.copy(prefetchLimitMode = mode) }
                }
            )
            SectionDivider()
            PresetNumericOptionBlock(
                title = "预加载个数",
                currentValueText = "前后各 ${config.prefetchCount} 张",
                currentValue = config.prefetchCount,
                presets = ConfigContract.PREFETCH_COUNT_PRESETS,
                formatPreset = { "N±$it" },
                enabled = config.enableAdjacentPrefetch,
                onSelectPreset = { count ->
                    onUpdateConfig { it.copy(prefetchCount = count) }
                },
                onRequestCustom = {
                    onOpenNumericInput(
                        NumericInputSpec(
                            title = "预加载个数",
                            summary = "按 N±k 范围预加载，可输入 1 到 15",
                            initialValue = config.prefetchCount,
                            minValue = ConfigContract.MIN_PREFETCH_COUNT,
                            maxValue = ConfigContract.MAX_PREFETCH_COUNT,
                            unitLabel = "张",
                            onConfirm = { count ->
                                onUpdateConfig { it.copy(prefetchCount = count) }
                            }
                        )
                    )
                }
            )
            SectionDivider()
            PresetNumericOptionBlock(
                title = "预加载容量上限",
                currentValueText = formatCacheMb(config.prefetchDiskCacheMb),
                currentValue = config.prefetchDiskCacheMb,
                presets = ConfigContract.PREFETCH_DISK_CACHE_MB_PRESETS,
                formatPreset = ::formatCacheMb,
                enabled = config.enableAdjacentPrefetch,
                onSelectPreset = { mb ->
                    onUpdateConfig { it.copy(prefetchDiskCacheMb = mb) }
                },
                onRequestCustom = {
                    onOpenNumericInput(
                        NumericInputSpec(
                            title = "预加载容量上限",
                            summary = "范围 32 到 16384 MB",
                            initialValue = config.prefetchDiskCacheMb,
                            minValue = ConfigContract.MIN_PREFETCH_DISK_CACHE_MB,
                            maxValue = ConfigContract.MAX_PREFETCH_DISK_CACHE_MB,
                            unitLabel = "MB",
                            onConfirm = { mb ->
                                onUpdateConfig { it.copy(prefetchDiskCacheMb = mb) }
                            }
                        )
                    )
                }
            )
            SectionDivider()
            PresetNumericOptionBlock(
                title = "单文件预加载上限",
                currentValueText = formatMbOrUnlimited(config.prefetchMaxSingleFileMb),
                currentValue = config.prefetchMaxSingleFileMb,
                presets = ConfigContract.PREFETCH_MAX_SINGLE_FILE_MB_PRESETS,
                formatPreset = ::formatMbOrUnlimited,
                enabled = config.enableAdjacentPrefetch,
                onSelectPreset = { mb ->
                    onUpdateConfig { it.copy(prefetchMaxSingleFileMb = mb) }
                },
                onRequestCustom = {
                    onOpenNumericInput(
                        NumericInputSpec(
                            title = "单文件预加载上限",
                            summary = "输入 0 表示不限制，单位 MB",
                            initialValue = config.prefetchMaxSingleFileMb,
                            minValue = ConfigContract.MIN_PREFETCH_MAX_SINGLE_FILE_MB,
                            maxValue = ConfigContract.MAX_PREFETCH_DISK_CACHE_MB,
                            unitLabel = "MB",
                            onConfirm = { mb ->
                                onUpdateConfig { it.copy(prefetchMaxSingleFileMb = mb) }
                            }
                        )
                    )
                }
            )
            SectionDivider()
            PresetNumericOptionBlock(
                title = "预加载并发数",
                currentValueText = "${config.prefetchConcurrency} 线程",
                currentValue = config.prefetchConcurrency,
                presets = ConfigContract.PREFETCH_CONCURRENCY_PRESETS,
                formatPreset = { "$it 线程" },
                enabled = config.enableAdjacentPrefetch,
                allowCustom = false,
                onSelectPreset = { count ->
                    onUpdateConfig { it.copy(prefetchConcurrency = count) }
                }
            )
            SectionDivider()
            SuperSwitch(
                checked = config.prefetchWifiOnly,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(prefetchWifiOnly = checked) }
                },
                title = "仅在 Wi-Fi 下预加载",
                summary = "移动数据网络下暂停后台预加载",
                enabled = config.enableAdjacentPrefetch
            )
        }

        SmallTitle(
            text = "远程缩略图",
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            SuperSwitch(
                checked = config.enableRemoteThumbnails,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(enableRemoteThumbnails = checked) }
                },
                title = "启用远程缩略图",
                summary = "放开远程目录图片缩略图大小限制"
            )
            SectionDivider()
            PresetNumericOptionBlock(
                title = "缩略图源图上限",
                currentValueText = formatMbOrUnlimited(config.maxRemoteThumbnailSizeMb),
                currentValue = config.maxRemoteThumbnailSizeMb,
                presets = ConfigContract.MAX_REMOTE_THUMBNAIL_SIZE_MB_PRESETS,
                formatPreset = ::formatMbOrUnlimited,
                enabled = config.enableRemoteThumbnails,
                onSelectPreset = { mb ->
                    onUpdateConfig { it.copy(maxRemoteThumbnailSizeMb = mb) }
                },
                onRequestCustom = {
                    onOpenNumericInput(
                        NumericInputSpec(
                            title = "缩略图源图上限",
                            summary = "输入 0 表示不限制，单位 MB",
                            initialValue = config.maxRemoteThumbnailSizeMb,
                            minValue = ConfigContract.MIN_MAX_REMOTE_THUMBNAIL_SIZE_MB,
                            maxValue = 16_384,
                            unitLabel = "MB",
                            onConfirm = { mb ->
                                onUpdateConfig { it.copy(maxRemoteThumbnailSizeMb = mb) }
                            }
                        )
                    )
                }
            )
            SectionDivider()
            SuperSwitch(
                checked = config.highResThumbnails,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(highResThumbnails = checked) }
                },
                title = "高清缩略图",
                summary = "提升目录及查看器缩略图解码像素上限",
                enabled = config.enableRemoteThumbnails
            )
        }

        SmallTitle(
            text = "看图与动图渲染",
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            SuperSwitch(
                checked = config.disableSoftwareImageRendering,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(disableSoftwareImageRendering = checked) }
                },
                title = "禁用大图软件渲染",
                summary = "强制硬件加速直绘，超长与超高像素原图自动分块"
            )
            SectionDivider()
            SuperSwitch(
                checked = config.enableHardwareGifPlayback,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(enableHardwareGifPlayback = checked) }
                },
                title = "动图硬件加速重绘",
                summary = "使用系统硬件解码播放 GIF，修复发糊与掉帧"
            )
        }

        SmallTitle(
            text = "Android/data 与 obb 访问增强",
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        )
        AndroidDataAccessCard(
            enabled = config.enableAndroidDataAccess,
            shizukuStatus = shizukuStatus,
            onToggle = { checked ->
                onUpdateConfig { it.copy(enableAndroidDataAccess = checked) }
            },
            onRequestPermission = onRequestShizukuPermission,
            onRefresh = onRefreshShizuku
        )

        SmallTitle(
            text = "其他增强",
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            SuperSwitch(
                checked = config.unlockTextPreviewLimits,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(unlockTextPreviewLimits = checked) }
                },
                title = "解除文本预览截断",
                summary = "放宽内置文本预览的长度与行数上限"
            )
            SectionDivider()
            SuperSwitch(
                checked = config.debugLogging,
                onCheckedChange = { checked ->
                    onUpdateConfig { it.copy(debugLogging = checked) }
                },
                title = "调试日志",
                summary = "向 Xposed 日志输出详细 Hook 与缓存诊断"
            )
            SectionDivider()
            SuperArrow(
                title = "恢复默认配置",
                summary = "将全部开关与缓存参数重置为初始值",
                onClick = onResetDefaults
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AndroidDataAccessCard(
    enabled: Boolean,
    shizukuStatus: ShizukuStatusSnapshot,
    onToggle: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        SuperSwitch(
            checked = enabled,
            onCheckedChange = onToggle,
            title = "启用目录访问增强",
            summary = "优先使用 Root，无 Root 时回退 Shizuku"
        )
        SectionDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    label = if (shizukuStatus.binderAlive) "Shizuku 已运行" else "Shizuku 未运行",
                    accent = if (shizukuStatus.binderAlive) FxPlusColors.Success else FxPlusColors.Warning,
                    background = if (shizukuStatus.binderAlive) FxPlusColors.SuccessBg else FxPlusColors.WarningBg
                )
                StatusPill(
                    label = if (shizukuStatus.permissionGranted) "Shizuku 已授权" else "Shizuku 未授权",
                    accent = if (shizukuStatus.permissionGranted) FxPlusColors.Success else FxPlusColors.Warning,
                    background = if (shizukuStatus.permissionGranted) FxPlusColors.SuccessBg else FxPlusColors.WarningBg
                )
                StatusPill(
                    label = if (shizukuStatus.serviceBound) {
                        "存储桥接 uid ${shizukuStatus.privilegedUid}"
                    } else {
                        "存储桥接未绑定"
                    },
                    accent = if (shizukuStatus.serviceBound) FxPlusColors.Success else colors.onSurfaceVariantSummary,
                    background = if (shizukuStatus.serviceBound) FxPlusColors.SuccessBg else colors.surfaceContainerHigh
                )
            }
            if (shizukuStatus.binderAlive) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Shizuku API v${shizukuStatus.serverVersion} · uid ${shizukuStatus.privilegedUid}",
                    fontSize = 12.sp,
                    color = colors.onSurfaceSecondary
                )
            } else if (!shizukuStatus.shizukuInstalled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "未检测到 Shizuku，如设备已 Root 将自动走 Root 访问",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariantSummary
                )
            }
            if (shizukuStatus.statusMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = shizukuStatus.statusMessage,
                    fontSize = 12.sp,
                    color = FxPlusColors.Info,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    text = "申请 Shizuku 授权",
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1.3f),
                    insideMargin = PaddingValues(vertical = 10.dp)
                )
                TextButton(
                    text = "检测桥接",
                    onClick = onRefresh,
                    modifier = Modifier.weight(0.9f),
                    insideMargin = PaddingValues(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun PrefetchLimitModeBlock(
    currentMode: PrefetchLimitMode,
    enabled: Boolean,
    onSelectMode: (PrefetchLimitMode) -> Unit
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "预加载限制模式",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.onSurface else colors.disabledOnSurface
            )
            Text(
                text = currentMode.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) colors.primary else colors.disabledOnSurface
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrefetchLimitMode.entries.forEach { mode ->
                SelectableChip(
                    label = mode.displayName,
                    selected = currentMode == mode,
                    enabled = enabled,
                    onClick = { onSelectMode(mode) }
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: XposedStatusSnapshot,
    syncMessage: String,
    syncColor: Color,
    onRequestScope: () -> Unit,
    onHotReloadFx: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPill(
                label = if (status.serviceBound) "API ${status.apiVersion} 已连接" else "服务未连接",
                accent = if (status.serviceBound) FxPlusColors.Success else FxPlusColors.Warning,
                background = if (status.serviceBound) FxPlusColors.SuccessBg else FxPlusColors.WarningBg
            )
            StatusPill(
                label = if (status.selfHooked) "自激活已生效" else "未自激活",
                accent = if (status.selfHooked) FxPlusColors.Success else colors.onSurfaceVariantSummary,
                background = if (status.selfHooked) FxPlusColors.SuccessBg else colors.surfaceContainerHigh
            )
            StatusPill(
                label = if (status.fxInScope) "FX 已在作用域" else "FX 未在作用域",
                accent = if (status.fxInScope) FxPlusColors.Success else FxPlusColors.Warning,
                background = if (status.fxInScope) FxPlusColors.SuccessBg else FxPlusColors.WarningBg
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        if (status.serviceBound) {
            Text(
                text = "框架 · ${status.frameworkName} ${status.frameworkVersion}",
                fontSize = 13.sp,
                color = colors.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        val fxText = if (status.fxPackage.installed) {
            "目标应用 · nextapp.fx ${status.fxPackage.versionName} · ${status.fxPackage.compatibilityLabel}"
        } else {
            "目标应用 · 未安装 nextapp.fx"
        }
        Text(
            text = fxText,
            fontSize = 13.sp,
            color = colors.onSurfaceSecondary
        )

        if (status.fxTargets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            val targetSummary = status.fxTargets.joinToString(" | ") {
                "${it.processName} [${it.state}]"
            }
            Text(
                text = "运行中进程 · $targetSummary",
                fontSize = 12.sp,
                color = if (status.staleTargetCount > 0) FxPlusColors.Warning else colors.onSurfaceVariantSummary
            )
        }

        if (status.lastActionMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = status.lastActionMessage,
                fontSize = 12.sp,
                color = FxPlusColors.Info,
                fontWeight = FontWeight.Medium
            )
        }

        if (syncMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = syncMessage,
                fontSize = 12.sp,
                color = syncColor
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                text = "申请作用域",
                onClick = onRequestScope,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
                insideMargin = PaddingValues(vertical = 10.dp)
            )
            TextButton(
                text = "热重载 FX",
                onClick = onHotReloadFx,
                modifier = Modifier.weight(1f),
                insideMargin = PaddingValues(vertical = 10.dp)
            )
            TextButton(
                text = "刷新",
                onClick = onRefreshStatus,
                modifier = Modifier.weight(0.75f),
                insideMargin = PaddingValues(vertical = 10.dp)
            )
        }
    }
}

private fun formatMbOrUnlimited(mb: Int): String {
    return if (mb <= 0) "不限" else formatCacheMb(mb)
}

private fun formatCacheMb(mb: Int): String {
    return if (mb >= 1024 && mb % 1024 == 0) {
        "${mb / 1024} GB"
    } else {
        "$mb MB"
    }
}
