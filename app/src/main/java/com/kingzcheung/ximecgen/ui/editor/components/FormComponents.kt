package com.kingzcheung.ximecgen.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 通用文本/数字字段行。 */
@Composable
fun FieldRow(
    label: String,
    value: String,
    hint: String? = null,
    enabled: Boolean = true,
    onCommit: (String) -> Unit,
) {
    var editing by remember(value) { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = hint?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = {
            if (editing) {
                Row {
                    TextButton(onClick = {
                        editing = false
                        text = value
                    }) { Text("取消") }
                    TextButton(onClick = {
                        editing = false
                        onCommit(text)
                    }) { Text("确定") }
                }
            } else {
                TextButton(onClick = { if (enabled) editing = true }) { Text(value.ifEmpty { "未设置" }) }
            }
        },
    )
    if (editing) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text(label) },
        )
    }
}

/** 布尔开关行。 */
@Composable
fun BoolRow(label: String, value: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = hint?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingContent = { Switch(checked = value, onCheckedChange = onChange) },
    )
}

/** 数值滑杆行：拖动本地即时反馈，松手提交格式化值。 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String = { if (it % 1f == 0f) it.toInt().toString() else "%.2f".format(it) },
    onChangeFinished: (String) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label)
            Text(format(local), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChangeFinished(format(local)) },
            valueRange = range,
            steps = steps,
        )
    }
}

/** 颜色行：显示色块，点击弹出取色器。 */
@Composable
fun ColorRow(label: String, argb: Long?, onPick: (Long) -> Unit, onClear: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            val color = argb?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
        trailingContent = {
            Row {
                TextButton(onClick = { onPick(DEFAULT_SWATCH.first()) }) { Text(argb?.let { hexOf(it) } ?: "未设置") }
                if (onClear != null && argb != null) {
                    TextButton(onClick = onClear) { Text("清除") }
                }
            }
        },
        modifier = Modifier.clickable { onPick(argb ?: DEFAULT_SWATCH.first()) },
    )
}

fun hexOf(argb: Long): String = "0x%08X".format(argb)

/** 十六个预设色 + 自定义 RGB 滑杆的取色器（M3 底部弹层）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    title: String,
    initial: Long?,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit,
) {
    val initialColor = initial?.toInt() ?: 0xFF8F73E2.toInt()
    var red by remember { mutableStateOf((initialColor shr 16 and 0xFF) / 255f) }
    var green by remember { mutableStateOf((initialColor shr 8 and 0xFF) / 255f) }
    var blue by remember { mutableStateOf((initialColor and 0xFF) / 255f) }
    var alpha by remember { mutableStateOf(if (initial == null) 1f else (initialColor ushr 24 and 0xFF) / 255f) }
    val current = Color(red, green, blue, alpha)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(current),
            )
            Text("预设", style = MaterialTheme.typography.labelLarge)
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(DEFAULT_SWATCH) { c ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c.toInt()))
                            .clickable {
                                red = (c.toInt() shr 16 and 0xFF) / 255f
                                green = (c.toInt() shr 8 and 0xFF) / 255f
                                blue = (c.toInt() and 0xFF) / 255f
                                alpha = (c ushr 24 and 0xFF) / 255f
                            },
                    )
                }
            }
            Column(Modifier.fillMaxWidth()) {
                ColorChannelLabel("R", red)
                Slider(red, { red = it }, valueRange = 0f..1f)
                ColorChannelLabel("G", green)
                Slider(green, { green = it }, valueRange = 0f..1f)
                ColorChannelLabel("B", blue)
                Slider(blue, { blue = it }, valueRange = 0f..1f)
                ColorChannelLabel("A", alpha)
                Slider(alpha, { alpha = it }, valueRange = 0f..1f)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = {
                    val argb = ((alpha * 255).toInt() shl 24)
                        .or((red * 255).toInt() shl 16)
                        .or((green * 255).toInt() shl 8)
                        .or((blue * 255).toInt())
                        .toLong() and 0xFFFFFFFFL
                    onPicked(argb)
                }) { Text("应用") }
            }
        }
    }
}

@Composable
private fun ColorChannelLabel(name: String, v: Float) {
    Text(
        "$name ${(v * 255).toInt()}",
        style = MaterialTheme.typography.labelMedium,
    )
}

/** 预设色板：常用主题色 + 灰阶，首项为薰衣草紫。 */
val DEFAULT_SWATCH: List<Long> = listOf(
    0x8F73E2, 0x1A73E8, 0x00796B, 0xE65100,
    0xAD1457, 0x424242, 0xE3E4E8, 0xFFFFFF,
    0x60FFFFFF, 0x202124, 0xE8EAED, 0x4A4A4A,
    0xF6F6F6, 0x9AA0A6, 0x1D1B2E, 0x000000,
)
