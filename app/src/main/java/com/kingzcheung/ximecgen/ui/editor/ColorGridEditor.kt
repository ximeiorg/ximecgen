package com.kingzcheung.ximecgen.ui.editor

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONObject

private val DEFAULT_COLORS = listOf(
    "keyboard_bg_color" to 0xE3E4E8L,
    "keyboard_bg_color_dark" to 0x202020L,
    "key_bg_color" to 0xFFFFFFL,
    "key_bg_color_dark" to 0x4A4A4AL,
    "special_key_bg_color" to 0xD3D4D8L,
    "special_key_bg_color_dark" to 0x3A3A3AL,
    "candidate_bar_bg_color" to 0xE3E4E8L,
    "candidate_bar_bg_color_dark" to 0x202020L,
    "key_text_color" to 0x202124L,
    "key_text_color_dark" to 0xE8EAEDL,
    "candidate_text_color" to 0x202124L,
    "candidate_text_color_dark" to 0xE8EAEDL,
)

private val FRIENDLY_NAMES = mapOf(
    "keyboard_bg_color" to "键盘背景",
    "keyboard_bg_color_dark" to "键盘背景(深)",
    "key_bg_color" to "按键底色",
    "key_bg_color_dark" to "按键底色(深)",
    "special_key_bg_color" to "特殊键底色",
    "special_key_bg_color_dark" to "特殊键底色(深)",
    "candidate_bar_bg_color" to "候选栏背景",
    "candidate_bar_bg_color_dark" to "候选栏背景(深)",
    "key_text_color" to "按键文字",
    "key_text_color_dark" to "按键文字(深)",
    "candidate_text_color" to "候选文字",
    "candidate_text_color_dark" to "候选文字(深)",
)

data class ColorEntry(val key: String, val value: Long)

@Composable
fun ColorGridEditor(
    colorsJson: JSONObject,
    onAddColor: (String, String) -> Unit,
    onUpdateColor: (String, String) -> Unit,
    onRemoveColor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    val entries = remember(colorsJson) {
        val list = mutableListOf<ColorEntry>()
        val keys = colorsJson.keys().asSequence().toList()
        for (key in keys) {
            val value = colorsJson.optString(key, null) ?: continue
            try {
                val clean = value.trimStart('#', ' ').removePrefix("0x").removePrefix("0X")
                list.add(ColorEntry(key, clean.toLong(16)))
            } catch (_: Exception) { }
        }
        if (list.isEmpty()) DEFAULT_COLORS.map { ColorEntry(it.first, it.second) }
        else list
    }

    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        entries.forEachIndexed { index, entry ->
            val displayName = FRIENDLY_NAMES[entry.key] ?: entry.key
            var showPicker by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth().clickable { showPicker = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF000000 or entry.value)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.key, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(displayName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("0x${(entry.value and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, modifier = Modifier.clickable { onRemoveColor(entry.key) }.padding(4.dp))
            }
            if (index < entries.lastIndex) HorizontalDivider()

            if (showPicker) {
                ColorPickerDialog(
                    title = displayName,
                    initialColor = entry.value,
                    onColorChanged = { newColor ->
                        val hex = "0x${(newColor and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()}"
                        onUpdateColor(entry.key, hex)
                        showPicker = false
                    },
                    onDismiss = { showPicker = false },
                )
            }
        }

        OutlinedButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) { Text("添加颜色项") }
    }

    if (showAddDialog) {
        AddColorEntryDialog(
            existingKeys = entries.map { it.key }.toSet(),
            onConfirm = { key, value ->
                onAddColor(key, value)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun AddColorEntryDialog(
    existingKeys: Set<String>,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0x8F73E2L) }
    var keyError by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text("添加颜色项", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = key,
                onValueChange = { key = it; keyError = false },
                label = { Text("键名 (如 my_custom_color)") },
                isError = keyError,
                supportingText = if (keyError) {{ Text("键名已存在或为空") }} else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("颜色: ", fontSize = 14.sp)
                ColorPickerButton(
                    color = color,
                    onClick = { showPicker = true },
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (key.isBlank() || key in existingKeys) { keyError = true; return@TextButton }
                        val hex = "0x${(color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()}"
                        onConfirm(key, hex)
                    },
                ) { Text("添加") }
            }
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            title = "选择颜色",
            initialColor = color,
            onColorChanged = { color = it; showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}
