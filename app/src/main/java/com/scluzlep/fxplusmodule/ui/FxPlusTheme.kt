package com.scluzlep.fxplusmodule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

object FxPlusColors {
    val Success = Color(0xFF15803D)
    val SuccessBg = Color(0x1A15803D)
    val Warning = Color(0xFFB45309)
    val WarningBg = Color(0x1AB45309)
    val Info = Color(0xFF2563EB)
    val InfoBg = Color(0x1A2563EB)
    val Danger = Color(0xFFDC2626)
}

@Composable
fun FxPlusTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MiuixTheme(colors = colors, content = content)
}

@Composable
fun StatusPill(
    label: String,
    accent: Color,
    background: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val bgColor = when {
        !enabled -> colors.surfaceContainer.copy(alpha = 0.6f)
        selected -> colors.primary
        else -> colors.surfaceContainerHigh
    }
    val textColor = when {
        !enabled -> colors.disabledOnSurface
        selected -> colors.onPrimary
        else -> colors.onSurface
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
fun PresetNumericOptionBlock(
    title: String,
    currentValueText: String,
    currentValue: Int,
    presets: List<Int>,
    formatPreset: (Int) -> String,
    enabled: Boolean = true,
    allowCustom: Boolean = true,
    onSelectPreset: (Int) -> Unit,
    onRequestCustom: () -> Unit = {}
) {
    val colors = MiuixTheme.colorScheme
    val isCustom = allowCustom && currentValue !in presets

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
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.onSurface else colors.disabledOnSurface
            )
            Text(
                text = currentValueText,
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
            presets.forEach { preset ->
                SelectableChip(
                    label = formatPreset(preset),
                    selected = currentValue == preset,
                    enabled = enabled,
                    onClick = { onSelectPreset(preset) }
                )
            }
            if (allowCustom) {
                SelectableChip(
                    label = if (isCustom) "自定义 $currentValueText" else "自定义",
                    selected = isCustom,
                    enabled = enabled,
                    onClick = onRequestCustom
                )
            }
        }
    }
}

@Composable
fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MiuixTheme.colorScheme.dividerLine
    )
}

data class NumericInputSpec(
    val title: String,
    val summary: String,
    val initialValue: Int,
    val minValue: Int,
    val maxValue: Int,
    val unitLabel: String,
    val onConfirm: (Int) -> Unit
)

@Composable
fun NumericInputDialog(
    show: MutableState<Boolean>,
    spec: NumericInputSpec?
) {
    if (spec == null) return
    var inputText by remember(spec) { mutableStateOf(spec.initialValue.toString()) }
    var errorMessage by remember(spec) { mutableStateOf("") }

    SuperDialog(
        show = show,
        title = spec.title,
        summary = spec.summary,
        onDismissRequest = { show.value = false }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = inputText,
                onValueChange = { next ->
                    inputText = next.filter { it.isDigit() }
                    errorMessage = ""
                },
                label = "数值 · ${spec.unitLabel}",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    fontSize = 12.sp,
                    color = FxPlusColors.Danger
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = { show.value = false },
                    modifier = Modifier.weight(1f),
                    insideMargin = PaddingValues(vertical = 12.dp)
                )
                TextButton(
                    text = "确定",
                    onClick = {
                        val parsed = inputText.trim().toIntOrNull()
                        if (parsed == null || parsed < spec.minValue || parsed > spec.maxValue) {
                            errorMessage = "请输入 ${spec.minValue} 到 ${spec.maxValue} 之间的整数"
                        } else {
                            spec.onConfirm(parsed)
                            show.value = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    insideMargin = PaddingValues(vertical = 12.dp)
                )
            }
        }
    }
}
