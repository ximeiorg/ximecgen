package com.kingzcheung.ximecgen.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val DEFAULT_PRESETS = listOf(
    0xE3E4E8, 0xFFFFFF, 0x202020, 0x4A4A4A,
    0x202124, 0xE8EAED, 0x1A73E8, 0x8AB4F8,
    0x8F73E2, 0x2E7D32, 0xC62828, 0xE65100,
    0x424242, 0xAD1457, 0x00796B, 0xF9AB00,
).map { 0xFF000000 or it.toLong() }

private val RED_PRESETS = listOf(
    0xFFE3E4E8, 0xFFFFFFFF, 0xFF202020, 0xFF4A4A4A,
    0xFF202124, 0xFFE8EAED, 0xFF1A73E8, 0xFF8AB4F8,
    0xFF8F73E2, 0xFF2E7D32, 0xFFC62828, 0xFFE65100,
    0xFF424242, 0xFFAD1457, 0xFF00796B, 0xFFF9AB00,
)

@Composable
fun ColorPickerButton(
    color: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
fun ColorPickerDialog(
    title: String = "选择颜色",
    initialColor: Long,
    onColorChanged: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var color by remember(initialColor) { mutableStateOf(Color(0xFF000000 or initialColor)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFFFF8E7),
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, fontSize = 16.sp)

                Spacer(Modifier.height(12.dp))

                // Preview swatch
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color),
                )

                Spacer(Modifier.height(16.dp))

                // Red slider
                var red by remember(color) { mutableFloatStateOf(color.red * 255f) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R", fontSize = 12.sp, modifier = Modifier.width(20.dp))
                    Slider(
                        value = red,
                        onValueChange = { red = it; color = Color(red / 255f, color.green, color.blue) },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(Color(red / 255f, 0f, 0f)))
                }

                // Green slider
                var green by remember(color) { mutableFloatStateOf(color.green * 255f) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("G", fontSize = 12.sp, modifier = Modifier.width(20.dp))
                    Slider(
                        value = green,
                        onValueChange = { green = it; color = Color(color.red, green / 255f, color.blue) },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(Color(0f, green / 255f, 0f)))
                }

                // Blue slider
                var blue by remember(color) { mutableFloatStateOf(color.blue * 255f) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("B", fontSize = 12.sp, modifier = Modifier.width(20.dp))
                    Slider(
                        value = blue,
                        onValueChange = { blue = it; color = Color(color.red, color.green, blue / 255f) },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(Color(0f, 0f, blue / 255f)))
                }

                Spacer(Modifier.height(12.dp))

                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RED_PRESETS.chunked(8).forEach { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { preset ->
                                val isSelected = Color(preset) == color
                                val presetColor = Color(preset)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(presetColor)
                                        .then(if (isSelected) Modifier.border(2.dp, color, CircleShape) else Modifier)
                                        .clickable { color = presetColor; red = color.red * 255f; green = color.green * 255f; blue = color.blue * 255f },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        val checkTint = if (presetColor.red * 0.299f + presetColor.green * 0.587f + presetColor.blue * 0.114f > 0.5f) Color.Black else Color.White
                                        Icon(Icons.Default.Check, contentDescription = null, tint = checkTint, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onColorChanged(0xFFFFFFL and color.toArgb().toLong()); onDismiss() }) {
                        Text("确定")
                    }
                }
            }
        }
    }
}
