package com.kingzcheung.ximecgen.ui.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Xime 支持的手势动作（与 validator 的 GESTURE_ACTIONS 一致）。 */
val GESTURE_ACTIONS = listOf(
    "commit", "command", "select_all", "copy", "cut", "paste",
    "line_start", "line_end", "undo", "none", "repeat",
    "switch_route", "toggle_ascii", "delete", "toggle_symbols",
)
val COMMAND_VALUES = listOf("clear_composition", "show_ime_picker")
val DISPLAY_MODES = listOf("key", "bubble", "both")

/**
 * 单个手势的编辑区：简单文本（上屏文本）或结构化 {label, action, value, display}。
 * [initial] 为 null 表示当前未定义。
 */
@Composable
fun GestureEditor(
    gestureName: String,
    initial: Any?,
    onChanged: (Any?) -> Unit,
) {
    val obj = initial as? JSONObject
    var structured by remember(initial) {
        mutableStateOf(obj != null && (obj.has("action") || obj.has("display") || obj.has("label")))
    }
    var simple by remember(initial) { mutableStateOf((initial as? String) ?: "") }
    var label by remember(initial) { mutableStateOf(obj?.optString("label") ?: "") }
    var action by remember(initial) { mutableStateOf(obj?.optString("action") ?: "commit") }
    var value by remember(initial) { mutableStateOf(obj?.optString("value") ?: "") }
    var display by remember(initial) { mutableStateOf(obj?.optString("display", "both") ?: "both") }

    Column(Modifier.padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !structured,
                onClick = {
                    structured = false
                    onChanged(if (simple.isEmpty()) null else simple)
                },
                label = { Text("上屏文本") },
            )
            FilterChip(
                selected = structured,
                onClick = {
                    structured = true
                    onChanged(
                        JSONObject()
                            .put("label", label.ifEmpty { gestureName })
                            .put("action", action)
                            .put("value", value)
                            .put("display", display)
                    )
                },
                label = { Text("结构化") },
            )
            TextButton(onClick = { onChanged(null) }) { Text("清除") }
        }

        if (!structured) {
            OutlinedTextField(
                value = simple,
                onValueChange = {
                    simple = it
                    onChanged(it.ifEmpty { null })
                },
                label = { Text("上屏文本") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = label,
                onValueChange = {
                    label = it
                    onChanged(buildObj(label, action, value, display))
                },
                label = { Text("显示文本（@ 开头为图标引用，如 @language）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            DropdownRow("动作", action, GESTURE_ACTIONS) {
                action = it
                onChanged(buildObj(label, action, value, display))
            }
            if (action == "command") {
                DropdownRow("命令", value.ifEmpty { COMMAND_VALUES.first() }, COMMAND_VALUES) {
                    value = it
                    onChanged(buildObj(label, action, value, display))
                }
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        onChanged(buildObj(label, action, value, display))
                    },
                    label = { Text("输出值（留空用显示文本）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DropdownRow("显示位置", display, DISPLAY_MODES) {
                display = it
                onChanged(buildObj(label, action, value, display))
            }
        }
    }
}

private fun buildObj(label: String, action: String, value: String, display: String): JSONObject {
    val o = JSONObject()
    if (label.isNotEmpty()) o.put("label", label)
    if (action.isNotEmpty()) o.put("action", action)
    if (value.isNotEmpty()) o.put("value", value)
    if (display.isNotEmpty()) o.put("display", display)
    return o
}

/** 简易下拉（无弹窗依赖）。 */
@Composable
fun DropdownRow(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label)
            Text(selected, color = MaterialTheme.colorScheme.primary)
        }
        if (expanded) {
            HorizontalDivider()
            options.forEach { opt ->
                Text(
                    opt,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expanded = false
                            onSelect(opt)
                        }
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                )
            }
        }
    }
}

/**
 * 按键手势编辑弹层：tap / swipe_up / swipe_down / long_press。
 * [onSave] 收到完整 binding 对象（调用方负责生成 set op）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureEditorSheet(
    keyId: String,
    binding: JSONObject?,
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit,
) {
    var tap by remember { mutableStateOf(binding?.opt("tap")) }
    var swipeUp by remember { mutableStateOf(binding?.opt("swipe_up")) }
    var swipeDown by remember { mutableStateOf(binding?.opt("swipe_down")) }
    var longPressValues by remember {
        mutableStateOf(
            binding?.optJSONObject("long_press")?.optJSONArray("values") ?: JSONArray()
        )
    }
    var lpDisplay by remember {
        mutableStateOf(binding?.optJSONObject("long_press")?.optString("display", "bubble") ?: "bubble")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text("按键 «$keyId» 手势", style = MaterialTheme.typography.titleMedium)
            Text("tap", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            GestureEditor("tap", tap, onChanged = { tap = it })

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("swipe_up（上滑）", style = MaterialTheme.typography.labelLarge)
            GestureEditor("swipe_up", swipeUp, onChanged = { swipeUp = it })

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("swipe_down（下滑）", style = MaterialTheme.typography.labelLarge)
            GestureEditor("swipe_down", swipeDown, onChanged = { swipeDown = it })

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("long_press（长按气泡，最多 10 项）", style = MaterialTheme.typography.labelLarge)
            DropdownRow("气泡显示模式", lpDisplay, DISPLAY_MODES) { lpDisplay = it }
            val items = (0 until longPressValues.length()).toList()
            items.forEach { idx ->
                val v = longPressValues.opt(idx)
                var text by remember(v) { mutableStateOf((v as? String) ?: "") }
                val vObj = v as? JSONObject
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (vObj == null) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                longPressValues.put(idx, it)
                            },
                            label = { Text("选项 ${idx + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        var objText by remember(v) {
                            mutableStateOf(
                                listOfNotNull(
                                    vObj.optString("label").ifEmpty { null },
                                    vObj.optString("action").ifEmpty { null },
                                    vObj.optString("value").ifEmpty { null },
                                ).joinToString(" · ")
                            )
                        }
                        Text(
                            "⟨${objText.ifEmpty { "结构化项" }}⟩",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = {
                        longPressValues.remove(idx)
                        longPressValues = JSONArray(longPressValues.toString())
                    }) { Text("删") }
                }
            }
            OutlinedButton(
                onClick = { longPressValues.put("") },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("添加选项") }

            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = {
                    val bindingOut = JSONObject()
                    tap?.let { bindingOut.put("tap", it) }
                    swipeUp?.let { bindingOut.put("swipe_up", it) }
                    swipeDown?.let { bindingOut.put("swipe_down", it) }
                    if (longPressValues.length() > 0) {
                        bindingOut.put(
                            "long_press",
                            JSONObject().put("display", lpDisplay).put("values", longPressValues),
                        )
                    }
                    onSave(bindingOut)
                }) { Text("保存") }
            }
        }
    }
}
