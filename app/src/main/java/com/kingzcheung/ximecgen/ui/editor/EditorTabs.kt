package com.kingzcheung.ximecgen.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.ximecgen.ui.editor.components.ColorPickerSheet
import com.kingzcheung.ximecgen.ui.editor.components.ColorRow
import com.kingzcheung.ximecgen.ui.editor.components.DropdownRow
import com.kingzcheung.ximecgen.ui.editor.components.FieldRow
import com.kingzcheung.ximecgen.ui.editor.components.GESTURE_ACTIONS
import com.kingzcheung.ximecgen.ui.editor.components.BoolRow
import com.kingzcheung.ximecgen.ui.editor.components.GestureEditorSheet
import com.kingzcheung.ximecgen.ui.editor.components.SliderRow
import com.kingzcheung.ximecgen.ui.preview.FUNCTION_KEY_IDS
import com.kingzcheung.ximecgen.ui.preview.FunctionKeyCap
import com.kingzcheung.ximecgen.ui.preview.KeyCap
import com.kingzcheung.ximecgen.ui.preview.PreviewKeyInfo
import com.kingzcheung.ximecgen.ui.preview.buildPreviewData
import com.kingzcheung.ximecgen.ui.preview.defaultLayoutRows
import com.kingzcheung.ximecgen.ui.preview.keyCapInfo
import com.kingzcheung.ximecgen.ui.preview.layoutRowIds
import com.kingzcheung.ximecgen.vm.ConfigUiState
import com.kingzcheung.ximecgen.vm.ConfigViewModel
import com.kingzcheung.ximecgen.vm.Ops
import org.json.JSONArray
import org.json.JSONObject

// ── 工具：JSON Pointer 取值 ──

private fun JSONObject.at(pointer: String): Any? {
    if (pointer.isEmpty()) return this
    val tokens = pointer.removePrefix("/").split("/")
    var cur: Any? = this
    for (t in tokens) {
        cur = when (cur) {
            is JSONObject -> if (cur.has(t)) cur.opt(t) else null
            is JSONArray -> t.toIntOrNull()?.let { i -> if (i < cur.length()) cur.opt(i) else null }
            else -> null
        } ?: return null
    }
    return cur
}

private fun Any?.asStringOrEmpty(): String = when (this) {
    null -> ""
    is String -> this
    is Number -> if (this.toDouble() % 1.0 == 0.0 && this is Int) toString() else toString()
    else -> toString()
}

private fun Any?.asLongOrNull(): Long? = (this as? Number)?.toLong()

private fun Any?.asBool(default: Boolean = false): Boolean = (this as? Boolean) ?: default

private fun dispatch(vm: ConfigViewModel, vararg ops: JSONObject) {
    vm.dispatch(JSONArray().apply { ops.forEach { put(it) } })
}

// ── Tab 1：常规 ──

@Composable
fun GeneralTab(state: ConfigUiState, vm: ConfigViewModel) {
    val config = remember(state.configJson) { JSONObject(state.configJson) }
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
        Text("元数据", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp))
        state.descriptors.filter { it.section == "metadata" }.forEach { d ->
            FieldRow(
                label = d.label,
                value = config.at(d.path).asStringOrEmpty(),
                hint = d.description,
            ) { vm.setField(d.path, it) }
        }

        HorizontalDivider()

        Text("显示模式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
        val darkMode = config.at("/style/dark_mode").asStringOrEmpty().ifEmpty { "2" }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("0" to "浅色", "1" to "深色", "2" to "跟随系统").forEachIndexed { i, (v, label) ->
                SegmentedButton(
                    selected = darkMode == v,
                    onClick = { vm.setField("/style/dark_mode", v) },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text(label) }
            }
        }

        Text("主题引用", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        val schemeIds = config.optJSONObject("color_schemes")?.keys()?.asSequence()?.toList() ?: emptyList()
        val cs = config.optJSONObject("style")?.opt("color_scheme")
        val currentLight = when (cs) {
            is JSONObject -> cs.optString("light")
            is String -> cs
            else -> ""
        }
        val currentDark = (cs as? JSONObject)?.optString("dark") ?: ""
        SchemePickerRow(
            label = "浅色主题",
            current = currentLight,
            schemeIds = schemeIds + "dynamic",
        ) { picked ->
            if (cs is JSONObject) {
                val next = JSONObject(cs.toString()).put("light", picked)
                dispatch(vm, Ops.set("/style/color_scheme", next))
            } else {
                dispatch(vm, Ops.set("/style/color_scheme", JSONObject().put("light", picked).put("dark", currentDark.ifEmpty { picked })))
            }
        }
        SchemePickerRow(
            label = "深色主题",
            current = currentDark,
            schemeIds = schemeIds + "dynamic",
        ) { picked ->
            if (cs is JSONObject) {
                val next = JSONObject(cs.toString()).put("dark", picked)
                dispatch(vm, Ops.set("/style/color_scheme", next))
            } else {
                dispatch(vm, Ops.set("/style/color_scheme", JSONObject().put("light", currentLight).put("dark", picked)))
            }
        }

        HorizontalDivider(Modifier.padding(top = 12.dp))

        Text("市场索引端点", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp))
        val urls = config.optJSONObject("xime_index")?.optJSONArray("base_urls")
        if (urls != null) {
            (0 until urls.length()).forEach { i ->
                val url = urls.optString(i)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    TextButton(onClick = { dispatch(vm, Ops.remove("/xime_index/base_urls/$i")) }) { Text("删除") }
                }
            }
        }
        AddTextButton("添加端点") { text ->
            if (urls == null) {
                dispatch(vm, Ops.set("/xime_index", JSONObject().put("base_urls", JSONArray().put(text))))
            } else {
                dispatch(vm, Ops.add("/xime_index/base_urls/-", text))
            }
        }
    }
}

/**
 * 迷你键盘色板：用方案的 background / key_bg / special_key / key_text 真实值
 * 渲染一个微缩键盘条，一眼看清方案实际效果。
 */
@Composable
private fun SchemeSwatch(scheme: JSONObject, dark: Boolean, modifier: Modifier = Modifier) {
    fun longColor(name: String): Color? {
        // 先取 _dark 变体（dark 模式），再回退基础字段
        val v = if (dark) (scheme.opt("${name}_dark") as? Number) else null
        return ((v ?: scheme.opt(name)) as? Number)?.toLong()?.let { argb ->
            Color((if (argb <= 0xFFFFFFL) argb or 0xFF000000L else argb).toInt())
        }
    }

    val fallbackKeyBg = if (dark) Color(0x99FFFFFF) else Color.White
    val fallbackText = if (dark) Color(0xFFE8EAED) else Color(0xFF202124)
    val primary = longColor("primary_color") ?: Color(0xFF8F73E2)
    val keyBg = longColor("key_bg_color") ?: fallbackKeyBg
    val specialBg = longColor("special_key_bg_color")
        ?: if (dark) primary else primary.copy(alpha = 0.28f).compositeOver(Color(0xFFE3E4E8))
    val keyText = longColor("key_text_color") ?: fallbackText

    val bg = scheme.optJSONObject("keyboard_background")
    val bgModifier: Modifier = when (bg?.optString("type")) {
        "gradient" -> {
            val arr = bg?.optJSONArray(if (dark) "colors_dark" else "colors")
                ?: bg?.optJSONArray("colors")
            val stops = arr?.let { a -> (0 until a.length()).mapNotNull { (a.opt(it) as? Number)?.toLong() } }
                ?.map { argb -> Color((if (argb <= 0xFFFFFFL) argb or 0xFF000000L else argb).toInt()) }
                ?: emptyList()
            if (stops.size >= 2) Modifier.background(Brush.linearGradient(stops))
            else Modifier.background(Color(0xFFE3E4E8))
        }
        "solid" -> {
            val c = ((if (dark) bg?.opt("color_dark") else bg?.opt("color")) as? Number
                ?: bg?.opt("color") as? Number)?.toLong()?.let { argb ->
                    Color((if (argb <= 0xFFFFFFL) argb or 0xFF000000L else argb).toInt())
                }
            Modifier.background(c ?: Color(0xFFE3E4E8))
        }
        "image" -> Modifier.background(if (dark) Color(0xFF3A3F4C) else Color(0xFF8D93A1))
        else -> Modifier.background(if (dark) Color(0xFF1E1838) else Color(0xFFE3E4E8))
    }

    Box(
        modifier = modifier
            .size(width = 72.dp, height = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(bgModifier)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 12.dp, height = 26.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(keyBg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (it == 1) {
                        Box(
                            Modifier
                                .size(width = 6.dp, height = 2.dp)
                                .background(keyText.copy(alpha = 0.85f)),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .size(width = 12.dp, height = 26.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(specialBg),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 6.dp, height = 2.dp)
                        .background(if (dark) Color.White.copy(alpha = 0.9f) else keyText.copy(alpha = 0.85f)),
                )
            }
        }
    }
}

@Composable
private fun SchemePickerRow(label: String, current: String, schemeIds: List<String>, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(0.3f))
        Box(Modifier.weight(0.7f)) {
            DropdownRow(label = "", selected = current.ifEmpty { "未设置" }, options = schemeIds, onSelect = onPick)
        }
    }
}

@Composable
private fun AddTextButton(label: String, onAdd: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    OutlinedButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("+ $label")
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) onAdd(text.trim())
                    text = ""
                    adding = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } },
        )
    }
}

// ── Tab 2：主题配色 ──

@Composable
fun ThemeTab(state: ConfigUiState, vm: ConfigViewModel) {
    val config = remember(state.configJson) { JSONObject(state.configJson) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newId by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    val schemes = config.optJSONObject("color_schemes") ?: JSONObject()

    Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
        Text("配色方案", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
        schemes.keys().asSequence().sorted().forEach { id ->
            val entry = schemes.optJSONObject(id) ?: return@forEach
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { selectedId = if (selectedId == id) null else id },
            ) {
                // 两行布局：第一行名称+操作，第二行浅/深色板——窄屏也放得下
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.optString("name", id))
                            Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (entry.optBoolean("dynamic_color")) {
                            Text("动态", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            // 复制方案
                            val copyId = "${id}_copy"
                            dispatch(vm, Ops.add("/color_schemes/$copyId", JSONObject(entry.toString())))
                            selectedId = copyId
                        }) { Icon(Icons.Default.ContentCopy, contentDescription = "复制") }
                        IconButton(onClick = { deletingId = id }) { Icon(Icons.Default.Delete, contentDescription = "删除") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SchemeSwatch(entry, dark = false)
                        SchemeSwatch(entry, dark = true)
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { adding = true },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) { Text("+ 新增方案") }

        selectedId?.let { id ->
            if (schemes.has(id)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("编辑：$id", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 4.dp))
                SchemeEditor(config, state, vm, id)
            }
        }
    }

    if (deletingId != null) {
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("删除配色方案") },
            text = { Text("确定删除 «${deletingId}» ？引用它的 style.color_scheme 需要手动调整。") },
            confirmButton = {
                TextButton(onClick = {
                    dispatch(vm, Ops.remove("/color_schemes/$deletingId"))
                    if (selectedId == deletingId) selectedId = null
                    deletingId = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消") } },
        )
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("新增配色方案") },
            text = {
                OutlinedTextField(
                    value = newId,
                    onValueChange = { newId = it },
                    label = { Text("方案 id（英文）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = newId.trim()
                    if (id.isNotEmpty() && !schemes.has(id)) {
                        dispatch(vm, Ops.add("/color_schemes/$id", JSONObject().put("name", id)))
                        selectedId = id
                    }
                    newId = ""
                    adding = false
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SchemeEditor(config: JSONObject, state: ConfigUiState, vm: ConfigViewModel, id: String) {
    val scheme = config.optJSONObject("color_schemes")?.optJSONObject(id) ?: return
    val dynamic = scheme.optBoolean("dynamic_color")

    // 方案名与动态配色
    state.descriptors.filter { it.section == "color_scheme" && it.fieldType != "color" }.forEach { d ->
        val path = d.path.replace("{id}", id)
        when (d.fieldType) {
            "boolean" -> BoolRow(d.label, scheme.optBoolean(d.path.substringAfterLast('/')), d.description) { v ->
                dispatch(vm, Ops.set(path, v))
            }
            else -> FieldRow(d.label, scheme.optString(d.path.substringAfterLast('/')), d.description) {
                vm.setField(path, it)
            }
        }
    }

    if (!dynamic) {
        // 颜色字段（含取色器弹层）
        ColorSchemeRows(config, vm, id, scheme)

        // 背景配置
        listOf(
            "keyboard_background" to "键盘背景",
            "key_background" to "按键背景",
            "candidate_bar_background" to "候选栏背景",
        ).forEach { (field, label) ->
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp))
            BackgroundEditor(
                current = scheme.optJSONObject(field),
            ) { value ->
                dispatch(vm, Ops.set("/color_schemes/$id/$field", value))
            }
        }
    }
}

/** 颜色行 + 取色器弹层（作用于具体方案 id）。 */
@Composable
private fun ColorSchemeRows(config: JSONObject, vm: ConfigViewModel, id: String, scheme: JSONObject) {
    var picking by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val colorFields = listOf(
        "primary_color" to "主题强调色",
        "keyboard_bg_color" to "键盘背景后备色",
        "key_bg_color" to "按键底色（浅色）",
        "key_bg_color_dark" to "按键底色（深色）",
        "special_key_bg_color" to "特殊键底色（浅色）",
        "special_key_bg_color_dark" to "特殊键底色（深色）",
        "candidate_bar_bg_color" to "候选栏底色",
        "key_text_color" to "按键文字色（浅色）",
        "key_text_color_dark" to "按键文字色（深色）",
        "candidate_text_color" to "候选文字色（浅色）",
        "candidate_text_color_dark" to "候选文字色（深色）",
        "candidate_selected_text_color" to "候选选中文字色（浅色）",
        "candidate_selected_text_color_dark" to "候选选中文字色（深色）",
    )
    colorFields.forEach { (field, label) ->
        ColorRow(
            label = label,
            argb = (scheme.opt(field) as? Number)?.toLong(),
            onPick = { picking = field to ((scheme.opt(field) as? Number)?.toLong() ?: 0xFF8F73E2) },
            onClear = { dispatch(vm, Ops.remove("/color_schemes/$id/$field")) },
        )
    }
    picking?.let { (field, current) ->
        ColorPickerSheet(
            title = "«${scheme.optString("name", id)}» $field",
            initial = current,
            onDismiss = { picking = null },
            onPicked = { argb ->
                dispatch(vm, Ops.set("/color_schemes/$id/$field", argb))
                picking = null
            },
        )
    }
}

/** solid/gradient/image 背景编辑器；value == null 表示清除。 */
@Composable
private fun BackgroundEditor(current: JSONObject?, onCommit: (Any?) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }
    val type = current?.optString("type", "").orEmpty()

    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("solid" to "纯色", "gradient" to "渐变", "image" to "图片").forEach { (t, label) ->
                FilterChip(
                    selected = type == t,
                    onClick = {
                        val bg = when (t) {
                            "solid" -> JSONObject().put("type", "solid").put("color", 0xFFE3E4E8).put("color_dark", 0xFF202125)
                            "gradient" -> JSONObject().put("type", "gradient")
                                .put("colors", JSONArray().put(0xFFE0F7FA).put(0xFFB2EBF2))
                                .put("colors_dark", JSONArray().put(0xFF00332E).put(0xFF015C56))
                                .put("angle", 90)
                            else -> JSONObject().put("type", "image").put("src", "themes/custom.jpg").put("fit", "cover")
                        }
                        onCommit(bg)
                    },
                    label = { Text(label) },
                )
            }
            if (current != null) {
                TextButton(onClick = { onCommit(null) }) { Text("清除") }
            }
        }

        when (type) {
            "solid" -> {
                ColorRow("颜色（浅色）", (current?.opt("color") as? Number)?.toLong(), onPick = { picking = "color" })
                ColorRow("颜色（深色）", (current?.opt("color_dark") as? Number)?.toLong(), onPick = { picking = "color_dark" })
            }
            "gradient" -> {
                val colors = current?.optJSONArray("colors")
                val colorsDark = current?.optJSONArray("colors_dark")
                listOf("colors" to "浅色断点", "colors_dark" to "深色断点").forEach { (arrName, label) ->
                    val arr = if (arrName == "colors") colors else colorsDark
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    if (arr != null) {
                        (0 until arr.length()).forEach { i ->
                            val argb = (arr.opt(i) as? Number)?.toLong()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(argb?.let { Color(it.toInt()) } ?: Color.Gray)
                                        .clickable { picking = "$arrName/$i" },
                                )
                                Text("  断点 ${i + 1}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                if (arr.length() > 2) {
                                    TextButton(onClick = {
                                        val next = JSONArray()
                                        (0 until arr.length()).forEach { j -> if (j != i) next.put(arr.opt(j)) }
                                        onCommit(JSONObject(current.toString()).put(arrName, next))
                                    }) { Text("删") }
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        val next = JSONArray()
                        if (arr != null) (0 until arr.length()).forEach { j -> next.put(arr.opt(j)) }
                        next.put(0xFFFFFFFF)
                        onCommit(JSONObject(current.toString()).put(arrName, next))
                    }) { Text("+ 添加断点") }
                }
                FieldRow("角度（0=左→右，90=上→下）", current?.opt("angle").asStringOrEmpty()) { text ->
                    val bg = JSONObject(current.toString())
                    bg.put("angle", text.toDoubleOrNull() ?: 90.0)
                    onCommit(bg)
                }
            }
            "image" -> {
                FieldRow("图片路径（相对 rime/ 目录）", current?.optString("src") ?: "") { text ->
                    onCommit(JSONObject(current.toString()).put("src", text))
                }
                DropdownRow(
                    "适配模式",
                    current?.optString("fit", "cover") ?: "cover",
                    listOf("cover", "contain", "fill", "fit_width", "fit_height", "none"),
                ) { fit ->
                    onCommit(JSONObject(current.toString()).put("fit", fit))
                }
                listOf("overlay_alpha" to "遮罩透明度（浅色）", "overlay_alpha_dark" to "遮罩透明度（深色）").forEach { (k, label) ->
                    FieldRow(label, current?.opt(k).asStringOrEmpty()) { text ->
                        val bg = JSONObject(current.toString())
                        bg.put(k, text.toDoubleOrNull() ?: 0.0)
                        onCommit(bg)
                    }
                }
            }
        }
    }

    picking?.let { path ->
        val target = if (path.contains('/')) {
            val (arrName, idxS) = path.split('/')
            current?.optJSONArray(arrName)?.opt(idxS.toInt()) as? Number
        } else {
            current?.opt(path) as? Number
        }
        ColorPickerSheet(
            title = path,
            initial = target?.toLong(),
            onDismiss = { picking = null },
            onPicked = { argb ->
                val bg = JSONObject(current.toString())
                if (path.contains('/')) {
                    val (arrName, idxS) = path.split('/')
                    val arr = bg.optJSONArray(arrName) ?: JSONArray()
                    arr.put(idxS.toInt(), argb)
                    bg.put(arrName, arr)
                } else {
                    bg.put(path, argb)
                }
                onCommit(bg)
                picking = null
            },
        )
    }
}

// ── Tab 3：键盘外观 ──

@Composable
fun AppearanceTab(state: ConfigUiState, vm: ConfigViewModel) {
    val config = remember(state.configJson) { JSONObject(state.configJson) }
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
        val sections = listOf(
            "keyboard_colors" to "全局颜色（后备）",
            "keyboard_key" to "按键形状",
            "keyboard_shadow" to "按键阴影",
            "keyboard_fonts" to "字体",
        )
        sections.forEach { (section, title) ->
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp))
            state.descriptors.filter { it.section == section }.forEach { d ->
                // 颜色行统一由 KeyboardColorPickers 渲染（带取色器）
                if (d.fieldType == "color") return@forEach
                val current = config.at(d.path)
                when (d.fieldType) {
                    "boolean" -> BoolRow(d.label, current.asBool(true), d.description) { vm.setField(d.path, it.toString()) }
                    // 数值字段用滑块：拖动即时预览，松手提交（范围对齐校验规则）
                    "number" -> {
                        val (range, fmt) = sliderSpecFor(d.path)
                        val value = (current as? Number)?.toFloat()
                            ?: current.asStringOrEmpty().toFloatOrNull()
                            ?: range.start
                        SliderRow(
                            label = d.label,
                            value = value.coerceIn(range.start, range.endInclusive),
                            range = range,
                            format = fmt,
                        ) { vm.setField(d.path, it) }
                    }
                    else -> FieldRow(d.label, current.asStringOrEmpty().ifEmpty { "" }, d.description) { vm.setField(d.path, it) }
                }
            }
        }
        // 全局颜色 + 取色器
        KeyboardColorPickers(config, vm)
    }
}

/** 数值字段的滑杆范围与显示格式（单位已在 label 中）。 */
private fun sliderSpecFor(path: String): Pair<ClosedFloatingPointRange<Float>, (Float) -> String> = when (path) {
    "/keyboard/key/corner_radius" -> 0f..20f to { it.toInt().toString() }
    "/keyboard/shadow/elevation" -> 0f..16f to { "%.1f".format(it) }
    "/keyboard/key/spacing_x" -> 0f..8f to { "%.2f".format(it) }
    "/keyboard/key/spacing_y" -> 0f..10f to { "%.2f".format(it) }
    else -> 0f..100f to { "%.2f".format(it) }
}

@Composable
private fun KeyboardColorPickers(config: JSONObject, vm: ConfigViewModel) {
    var picking by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val colors = config.optJSONObject("keyboard")?.optJSONObject("colors") ?: JSONObject()
    val known = listOf(
        "key_bg_color" to "按键底色（浅色）",
        "key_bg_color_dark" to "按键底色（深色）",
        "special_key_bg_color" to "特殊键底色（浅色）",
        "special_key_bg_color_dark" to "特殊键底色（深色）",
        "candidate_bar_bg_color" to "候选栏底色（浅色）",
        "candidate_bar_bg_color_dark" to "候选栏底色（深色）",
        "key_text_color" to "按键文字色（浅色）",
        "key_text_color_dark" to "按键文字色（深色）",
        "candidate_text_color" to "候选文字色（浅色）",
        "candidate_text_color_dark" to "候选文字色（深色）",
        "keyboard_bg_color" to "键盘背景后备色（浅色）",
        "keyboard_bg_color_dark" to "键盘背景后备色（深色）",
    )
    known.forEach { (field, label) ->
        ColorRow(
            label = label,
            argb = (colors.opt(field) as? Number)?.toLong(),
            onPick = { picking = field to ((colors.opt(field) as? Number)?.toLong() ?: 0xFFFFFFFF) },
            onClear = { vm.clearField("/keyboard/colors/$field") },
        )
    }
    picking?.let { (field, current) ->
        ColorPickerSheet(
            title = field,
            initial = current,
            onDismiss = { picking = null },
            onPicked = { argb ->
                vm.setColorField("/keyboard/colors/$field", argb)
                picking = null
            },
        )
    }
}

// ── Tab 4：布局与手势 ──

@Composable
fun LayoutTab(state: ConfigUiState, vm: ConfigViewModel) {
    val config = remember(state.configJson) { JSONObject(state.configJson) }
    var keyboardIdPref by rememberSaveable { mutableStateOf("qwerty") }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var addKeyDialog by remember { mutableStateOf(false) }
    var addKeyTargetRow by remember { mutableStateOf(0) }
    var manageTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scroll = rememberScrollState()

    val keyboard = config.optJSONObject("keyboard") ?: JSONObject()
    // 键盘布局动态列表：qwerty / qwerty_en / t9 同级，未来新增布局自动出现
    val layoutIds = run {
        val nonLayout = setOf("colors", "key", "shadow", "fonts")
        val known = listOf("qwerty", "qwerty_en", "t9")
        known.filter { keyboard.has(it) } +
            keyboard.keys().asSequence()
                .filter { it !in nonLayout && it !in known }
                .sorted()
                .toList()
    }
    val keyboardId = if (keyboardIdPref in layoutIds) keyboardIdPref else layoutIds.firstOrNull() ?: ""
    val layout = keyboard.optJSONObject(keyboardId) ?: JSONObject()
    val keys = layout.optJSONObject("keys") ?: JSONObject()
    val isQwertyLike = layout.has("keys") || layout.has("layout") || layout.has("button_layout")

    // 与预览同源的键帽配色/圆角/间距/阴影，编辑区所见即所得
    val dark = isSystemInDarkTheme()
    val preview = remember(state.configJson, dark, keyboardId) { buildPreviewData(config, dark, keyboardId) }

    Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
        Text("键盘布局", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp))
        WrapRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            layoutIds.forEach { id ->
                FilterChip(
                    selected = keyboardId == id,
                    onClick = { keyboardIdPref = id },
                    label = { Text(layoutLabel(id)) },
                )
            }
        }

        if (keyboardId.isEmpty()) {
            Text(
                "配置中没有键盘布局段落",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (isQwertyLike) {
            Text("布局模式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp))
            // 用真实键帽样式预览两种模式，所见即所得；卡片按内容宽度居中排列
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).wrapContentWidth(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val currentMode = layout.optString("button_layout", "standard") ?: "standard"
                LayoutModeCard(
                    title = "标准",
                    selected = currentMode == "standard",
                    onClick = { vm.setField("/keyboard/$keyboardId/button_layout", "standard") },
                ) {
                    // 标准：主文字居中，上滑提示在顶部（真实键帽渲染）
                    // enabled=false：键帽不消费点击，整张卡片任意位置都能选中
                    KeyCap(
                        base = Modifier,
                        preview = preview,
                        info = PreviewKeyInfo(id = "a", mainLabel = "A", swipeUpLabel = "1"),
                        modifier = Modifier.size(width = 68.dp, height = 56.dp),
                        dark = dark,
                        enabled = false,
                        onClick = {},
                    )
                }
                LayoutModeCard(
                    title = "紧凑",
                    selected = currentMode == "compact",
                    onClick = { vm.setField("/keyboard/$keyboardId/button_layout", "compact") },
                ) {
                    // 紧凑：主文字左上，提示右上，字根占右侧中部（真实键帽渲染）
                    KeyCap(
                        base = Modifier,
                        preview = preview,
                        info = PreviewKeyInfo(id = "a", mainLabel = "A", swipeUpLabel = "1", swipeDownLabel = "工", compact = true),
                        modifier = Modifier.size(width = 68.dp, height = 56.dp),
                        dark = dark,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }

        if (isQwertyLike) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("行布局（layout.rows，全部行可配置）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                "行内可混合功能键与字母键（对齐 Xime 3.0，最多 5 行）；点击键位编辑手势。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            val rowsArr = layout.optJSONObject("layout")?.optJSONArray("rows")
            if (rowsArr != null) {
                val compact = layout.optString("button_layout", "standard").ifEmpty { "standard" } == "compact"
                // 展示与预览同源（缺失行补内置默认、合并键拼接）；编辑操作只作用于配置中真实存在的行
                val configuredRowCount = rowsArr.length()
                val rowIds = remember(layout.toString()) { layoutRowIds(layout) }
                val firstRowSize = rowIds.firstOrNull()?.size ?: 10
                // 整块迷你键盘渲染（与预览同源配色/圆角/间距/阴影），所见即所得
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(preview.background.color(dark))
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    Column {
                        rowIds.forEachIndexed { ri, ids ->
                            // 9 键纯字母行整行居中缩进（与预览同款）
                            val rowWidth = if (ids.size == 9 && ids.none { it in FUNCTION_KEY_IDS }) {
                                (9f / firstRowSize).coerceIn(0.5f, 1f)
                            } else 1f
                            // 行高 56dp 使键帽视觉高度（扣 spacing_y）≈ 预览/真机的 47dp
                            Row(
                                Modifier.fillMaxWidth(rowWidth).height(56.dp)
                                    .align(Alignment.CenterHorizontally),
                            ) {
                                ids.forEachIndexed { ki, keyId ->
                                    val info = keyCapInfo(keys, keyId, compact)
                                    val keyMod = Modifier
                                        .padding(
                                            horizontal = preview.spacingX.dp,
                                            vertical = preview.spacingY.dp,
                                        )
                                        .weight(info.width)
                                        .fillMaxHeight()
                                    if (info.isFunction) {
                                        FunctionKeyCap(
                                            preview = preview,
                                            info = info,
                                            modifier = keyMod,
                                            onKeyClick = { editingKey = keyId },
                                        )
                                    } else {
                                        KeyCap(
                                            base = keyMod,
                                            preview = preview,
                                            // 与预览同一份键面逻辑（display=bubble 不上键面、@图标、大写）
                                            info = info,
                                            modifier = Modifier,
                                            dark = dark,
                                            onClick = { editingKey = keyId },
                                            onLongClick = if (ri < configuredRowCount) {
                                                { manageTarget = ri to ki }
                                            } else null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    "点键改手势 · 长按移动 / 移除（补充行来自内置默认，写入配置后才可调整）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 0.dp),
                )
                WrapRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    rowIds.indices.forEach { ri ->
                        TextButton(
                            enabled = ri < configuredRowCount,
                            onClick = { addKeyTargetRow = ri; addKeyDialog = true },
                        ) {
                            Text("＋ 第 ${ri + 1} 行")
                        }
                    }
                    if (configuredRowCount < 5) {
                        TextButton(onClick = {
                            val template = defaultLayoutRows().getOrElse(configuredRowCount) {
                                defaultLayoutRows().last()
                            }
                            val newRow = JSONArray()
                            template.forEach { newRow.put(it) }
                            dispatch(vm, Ops.add("/keyboard/$keyboardId/layout/rows/-", newRow))
                        }) { Text("＋ 新行") }
                    }
                }
            } else {
            Text(
                "未定义 layout.rows，使用内置默认布局",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
            TextButton(onClick = {
                val def = JSONArray()
                defaultLayoutRows().forEach { row ->
                    val r = JSONArray()
                    row.forEach { r.put(it) }
                    def.put(r)
                }
                dispatch(vm, Ops.set("/keyboard/$keyboardId/layout/rows", def))
            }) { Text("  创建默认行布局", modifier = Modifier.padding(horizontal = 16.dp)) }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("按键手势", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            "点击按键编辑 tap / 上滑 / 下滑 / 长按；也可在预览中直接点按键。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        keys.keys().asSequence().sorted().forEach { keyId ->
            val binding = keys.optJSONObject(keyId)
            val mainLabel = when (val tap = binding?.opt("tap")) {
                is String -> tap
                is JSONObject -> tap.optString("label", keyId)
                else -> keyId
            }
            Row(
                Modifier.fillMaxWidth().clickable { editingKey = keyId }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(mainLabel, modifier = Modifier.size(width = 48.dp, height = 24.dp))
                Text(keyId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        AddTextButton("添加按键定义") { text ->
            dispatch(vm, Ops.add("/keyboard/$keyboardId/keys/$text", JSONObject().put("tap", text)))
        }
        }

        // 布局附带的独立段落（如 t9 的 side_symbols）
        if (layout.has("side_symbols")) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("左侧快捷符号", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                "长按符号移除",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            val sideSymbols = layout.optJSONArray("side_symbols")
            if (sideSymbols != null) {
                // 真实键帽渲染，底色同键盘背景
                Box(
                    Modifier
                        .wrapContentWidth(Alignment.Start)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(preview.background.color(dark))
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    Row(Modifier.height(56.dp)) {
                        (0 until sideSymbols.length()).forEach { i ->
                            KeyCap(
                                base = Modifier.padding(
                                    horizontal = preview.spacingX.dp,
                                    vertical = preview.spacingY.dp,
                                ),
                                preview = preview,
                                info = PreviewKeyInfo(id = sideSymbols.optString(i), mainLabel = sideSymbols.optString(i)),
                                modifier = Modifier.size(width = 44.dp, height = 48.dp),
                                dark = dark,
                                onClick = {},
                                onLongClick = { dispatch(vm, Ops.remove("/keyboard/$keyboardId/side_symbols/$i")) },
                            )
                        }
                    }
                }
            }
            AddTextButton("添加符号") { text ->
                dispatch(vm, Ops.add("/keyboard/$keyboardId/side_symbols/-", text))
            }
        }
    }

    // 行内添加键位对话框
    if (addKeyDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addKeyDialog = false },
            title = { Text("添加键到第 ${addKeyTargetRow + 1} 行") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("键位 id（如 q、'、earth）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = text.trim()
                    if (id.isNotEmpty()) {
                        dispatch(vm, Ops.add("/keyboard/$keyboardId/layout/rows/$addKeyTargetRow/-", id))
                    }
                    text = ""
                    addKeyDialog = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { addKeyDialog = false }) { Text("取消") } },
        )
    }

    // 长按键位：手势编辑 / 左移 / 右移 / 移除（操作原始行元素，保留合并键子数组）
    manageTarget?.let { (ri, ki) ->
        val rowsArr = layout.optJSONObject("layout")?.optJSONArray("rows")
        val rowItems = rowsArr?.optJSONArray(ri) ?: JSONArray()
        val len = rowItems.length()
        fun rawId(item: Any?): String = when (item) {
            is JSONArray -> (0 until item.length())
                .mapNotNull { item.optString(it).trim().takeIf { s -> s.isNotEmpty() } }
                .joinToString("")
            is String -> item.trim()
            else -> "?"
        }
        val keyId = rawId(rowItems.opt(ki))
        fun sendRow(newItems: List<Any?>) {
            val newRow = JSONArray()
            newItems.forEach { item ->
                if (item is JSONArray) newRow.put(item) else newRow.put(item.toString())
            }
            dispatch(vm, Ops.set("/keyboard/$keyboardId/layout/rows/$ri", newRow))
        }
        AlertDialog(
            onDismissRequest = { manageTarget = null },
            title = { Text("键位「$keyId」· 第 ${ri + 1} 行") },
            text = {
                Column {
                    TextButton(onClick = {
                        editingKey = keyId
                        manageTarget = null
                    }) { Text("编辑手势") }
                    TextButton(
                        enabled = ki > 0,
                        onClick = {
                            val items = (0 until len).map { rowItems.opt(it) }.toMutableList()
                            val tmp = items[ki]
                            items[ki] = items[ki - 1]
                            items[ki - 1] = tmp
                            sendRow(items)
                            manageTarget = null
                        },
                    ) { Text("← 左移") }
                    TextButton(
                        enabled = ki < len - 1,
                        onClick = {
                            val items = (0 until len).map { rowItems.opt(it) }.toMutableList()
                            val tmp = items[ki]
                            items[ki] = items[ki + 1]
                            items[ki + 1] = tmp
                            sendRow(items)
                            manageTarget = null
                        },
                    ) { Text("右移 →") }
                    TextButton(onClick = {
                        sendRow((0 until len).filter { it != ki }.map { rowItems.opt(it) })
                        manageTarget = null
                    }) { Text("从行中移除", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { manageTarget = null }) { Text("关闭") }
            },
        )
    }

    editingKey?.let { keyId ->
        GestureEditorSheet(
            keyId = keyId,
            binding = keys.optJSONObject(keyId),
            onDismiss = { editingKey = null },
            onSave = { bindingOut ->
                dispatch(vm, Ops.set("/keyboard/$keyboardId/keys/$keyId", bindingOut))
                editingKey = null
            },
        )
    }
}

/** 布局模式选择卡片：内嵌真实键帽样式预览。 */
@Composable
private fun LayoutModeCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    keyPreview: @Composable () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        keyPreview()
        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(2.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 简易流式换行布局：不依赖 foundation 1.7+ 的 FlowRow，
 * 避免旧版本 Compose 运行时上的 NoSuchMethodError。
 */
@Composable
private fun WrapRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            val gap = spacing.roundToPx()
            val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
            val current = mutableListOf<androidx.compose.ui.layout.Placeable>()
            var currentWidth = 0
            measurables.forEach { m ->
                val p = m.measure(androidx.compose.ui.unit.Constraints(maxWidth = constraints.maxWidth))
                val extra = if (current.isEmpty()) 0 else gap + p.width
                if (current.isNotEmpty() && currentWidth + extra > constraints.maxWidth) {
                    rows.add(current.toMutableList())
                    current.clear()
                    currentWidth = 0
                }
                current.add(p)
                currentWidth += if (current.size == 1) p.width else gap + p.width
            }
            if (current.isNotEmpty()) rows.add(current)

            val rowHeights = rows.map { row -> row.maxOf { it.height } }
            val totalHeight = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
            layout(constraints.maxWidth, totalHeight) {
                var y = 0
                rows.forEachIndexed { ri, row ->
                    var x = 0
                    row.forEach { p ->
                        p.place(x, y)
                        x += p.width + gap
                    }
                    y += rowHeights[ri] + gap
                }
            }
        },
    )
}

private fun layoutLabel(id: String) = when (id) {
    "qwerty" -> "中文 26 键"
    "qwerty_en" -> "英文 26 键"
    "t9" -> "九键 (T9)"
    else -> id
}

