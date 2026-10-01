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

/** Xime 3.0 支持的手势动作（与 validator 的 GESTURE_ACTIONS / KeyActionRegistry 一致）。 */
val GESTURE_ACTIONS = listOf(
    "commit", "send_rime", "command", "select_all", "copy", "cut", "paste",
    "line_start", "line_end", "undo", "none", "repeat",
    "switch_route", "toggle_ascii", "delete", "toggle_symbols",
    "enter", "newline", "space", "repeat_space",
    "clear_all", "undo_clear", "toggle_shift", "voice",
)
/** command 动作可用的命令值（Xime 3.0 键盘按键路由命令）。 */
val COMMAND_VALUES = listOf(
    "clear_composition", "show_ime_picker",
    "shift_single", "shift_caps", "toggle_shift",
    "mode_change", "mode_change_number", "mode_change_common_symbol",
)
/** switch_route 可用的面板路由值。 */
val SWITCH_ROUTE_VALUES = listOf("emoji", "symbol", "clipboard")
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
            } else if (action == "switch_route") {
                DropdownRow("面板", value.ifEmpty { SWITCH_ROUTE_VALUES.first() }, SWITCH_ROUTE_VALUES) {
                    value = it
                    onChanged(buildObj(label, action, value, display))
                }
            } else if (action != "select_all" && action != "copy" && action != "cut" && action != "paste" &&
                action != "line_start" && action != "line_end" && action != "undo" && action != "none" &&
                action != "repeat" && action != "toggle_ascii" && action != "delete" && action != "toggle_symbols" &&
                action != "enter" && action != "newline" && action != "space" && action != "clear_all" &&
                action != "undo_clear" && action != "toggle_shift" && action != "voice"
            ) {
                // commit / send_rime / repeat_space 需要值（其余动作无需参数）
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
 * 按键手势编辑弹层：tap / double_tap / 四向滑动 / long_press（Xime 3.0 全手势）。
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
    var doubleTap by remember { mutableStateOf(binding?.opt("double_tap")) }
    var swipeUp by remember { mutableStateOf(binding?.opt("swipe_up")) }
    var swipeDown by remember { mutableStateOf(binding?.opt("swipe_down")) }
    var swipeLeft by remember { mutableStateOf(binding?.opt("swipe_left")) }
    var swipeRight by remember { mutableStateOf(binding?.opt("swipe_right")) }
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
            Text("double_tap（双击）", style = MaterialTheme.typography.labelLarge)
            GestureEditor("double_tap", doubleTap, onChanged = { doubleTap = it })

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("swipe_up（上滑）", style = MaterialTheme.typography.labelLarge)
            GestureEditor("swipe_up", swipeUp, onChanged = { swipeUp = it })

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("swipe_down（下滑）", style = MaterialTheme.typography.labelLarge)
            GestureEditor("swipe_down", swipeDown, onChanged = { swipeDown = it })

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("swipe_left（左滑，接管光标移动）", style = MaterialTheme.typography.labelLarge)
            GestureEditor("swipe_left", swipeLeft, onChanged = { swipeLeft = it })

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("swipe_right（右滑，接管光标移动）", style = MaterialTheme.typography.labelLarge)
            GestureEditor("swipe_right", swipeRight, onChanged = { swipeRight = it })

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
                    doubleTap?.let { bindingOut.put("double_tap", it) }
                    swipeUp?.let { bindingOut.put("swipe_up", it) }
                    swipeDown?.let { bindingOut.put("swipe_down", it) }
                    swipeLeft?.let { bindingOut.put("swipe_left", it) }
                    swipeRight?.let { bindingOut.put("swipe_right", it) }
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
