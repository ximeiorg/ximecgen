package com.kingzcheung.ximecgen.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    configJson: String,
    validationJson: String,
    onFieldUpdate: (String, String) -> Unit,
    onAddColor: ((String, String) -> Unit)? = null,
    onRemoveColor: ((String) -> Unit)? = null,
) {
    val validationResult = remember(validationJson) {
        try { JSONObject(validationJson) } catch (_: Exception) { null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Xime Config Generator") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    ) { padding ->
        FormEditor(
            modifier = Modifier.fillMaxSize().padding(padding),
            configJson = configJson,
            onFieldUpdate = onFieldUpdate,
            onAddColor = onAddColor,
            onRemoveColor = onRemoveColor,
            errors = validationResult?.optJSONArray("errors") ?: JSONArray(),
            warnings = validationResult?.optJSONArray("warnings") ?: JSONArray(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormEditor(
    modifier: Modifier = Modifier,
    configJson: String,
    onFieldUpdate: (String, String) -> Unit,
    onAddColor: ((String, String) -> Unit)? = null,
    onRemoveColor: ((String) -> Unit)? = null,
    errors: JSONArray,
    warnings: JSONArray,
) {
    val config = remember(configJson) {
        try { JSONObject(configJson) } catch (_: Exception) { JSONObject() }
    }
    var isDark by remember { mutableStateOf(false) }
    val visualConfig = remember(config, isDark) { configToVisualConfig(config, isDark) }
    val keyboard = config.optJSONObject("keyboard")
    val qwerty = keyboard?.optJSONObject("qwerty") ?: keyboard?.optJSONObject("qwerty_en")

    var tab by remember { mutableStateOf(0) }

    Column(modifier = modifier) {
        // Sticky keyboard preview
        Surface(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("实时预览", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = !isDark, onClick = { isDark = false }, label = { Text("浅色", fontSize = 11.sp) })
                        FilterChip(selected = isDark, onClick = { isDark = true }, label = { Text("深色", fontSize = 11.sp) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                KeyboardVisualizer(config = visualConfig, qwertyKeys = qwerty?.optJSONObject("keys"))
            }
        }

        // Tab row below preview
        val tabTitles = listOf("常规", "主题色", "键盘颜色", "键盘设置")
        TabRow(selectedTabIndex = tab) {
            tabTitles.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }

        // Tab content
        val scrollState = rememberScrollState()
        when (tab) {
            0 -> Column(modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 12.dp)) {
                if (errors.length() > 0 || warnings.length() > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            for (i in 0 until errors.length()) {
                                Text("[${errors.getJSONObject(i).optString("severity", "error")}] ${errors.getJSONObject(i).optString("path")}: ${errors.getJSONObject(i).optString("message")}", fontSize = 12.sp)
                            }
                            for (i in 0 until warnings.length()) {
                                Text("[warning] ${warnings.getJSONObject(i).optString("path")}: ${warnings.getJSONObject(i).optString("message")}", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val meta = config.optJSONObject("metadata") ?: JSONObject()
                Text("元数据", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                FieldRow("metadata.app_name", "应用名称", meta.optString("app_name", "Xime"), onFieldUpdate)
                FieldRow("metadata.app_version", "应用版本", meta.optString("app_version", ">=2.5.0"), onFieldUpdate)

                val style = config.optJSONObject("style") ?: JSONObject()
                Text("样式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FieldRow("style.color_scheme", "配色方案", style.optString("color_scheme", "lavender_purple"), onFieldUpdate)
                FieldRow("style.font_size", "字体大小", style.optString("font_size", ""), onFieldUpdate)

                val kb = config.optJSONObject("keyboard") ?: JSONObject()
                val key = kb.optJSONObject("key") ?: JSONObject()
                val shadow = kb.optJSONObject("shadow") ?: JSONObject()
                Text("键盘设置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FieldSliderRow("keyboard.key.corner_radius", "圆角半径", key.optString("corner_radius", "8"), 0f..20f, onFieldUpdate)
                FieldBoolRow("keyboard.shadow.enabled", "阴影开关", shadow.optString("enabled", "true"), onFieldUpdate)
                FieldSliderRow("keyboard.shadow.elevation", "阴影高度", shadow.optString("elevation", "1"), 0f..20f, onFieldUpdate)

                Spacer(Modifier.height(32.dp))
            }
            1 -> Column(modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 12.dp)) {
                ColorSchemesEditor(config.optJSONObject("color_schemes") ?: JSONObject())
                Spacer(Modifier.height(32.dp))
            }
            2 -> Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                val colors = config.optJSONObject("keyboard")?.optJSONObject("colors") ?: JSONObject()
                ColorGridEditor(
                    colorsJson = colors,
                    onAddColor = { key, value -> onAddColor?.invoke(key, value) ?: onFieldUpdate("keyboard.colors.$key", value) },
                    onUpdateColor = { key, value -> onFieldUpdate("keyboard.colors.$key", value) },
                    onRemoveColor = { key -> onRemoveColor?.invoke(key) },
                )
                Spacer(Modifier.height(32.dp))
            }
            3 -> Column(modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 12.dp)) {
                val qwertyData = keyboard?.optJSONObject("qwerty") ?: keyboard?.optJSONObject("qwerty_en")
                Text("手势绑定", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                if (qwertyData != null) {
                    val keys = qwertyData.optJSONObject("keys") ?: JSONObject()
                    val keyNames = keys.keys().asSequence().sorted().toList()
                    if (keyNames.isEmpty()) {
                        Text("暂无手势配置", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        keyNames.forEach { keyName ->
                            val gesture = keys.optJSONObject(keyName) ?: return@forEach
                            FieldRow("qwerty_key_$keyName", keyName, gesture.toString(), onFieldUpdate)
                        }
                    }
                } else {
                    Text("未配置手势数据", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ColorSchemesEditor(schemes: JSONObject) {
    val keys = remember(schemes) { schemes.keys().asSequence().toList() }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (keys.isEmpty()) {
                Text("无颜色方案", fontSize = 13.sp)
            } else {
                keys.forEach { key ->
                    val scheme = schemes.optJSONObject(key) ?: return@forEach
                    val color = try {
                        val hex = scheme.optString("primary_color", "0x000000").trimStart('#', ' ').removePrefix("0x").removePrefix("0X")
                        Color(0xFF000000 or hex.toLong(16))
                    } catch (_: Exception) { Color.Gray }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(color))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(scheme.optString("name", key), fontWeight = FontWeight.Medium)
                            Text("primary_color: ${scheme.optString("primary_color", "")}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (key != keys.last()) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FieldRow(path: String, label: String, value: String, onFieldUpdate: (String, String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    if (showDialog) {
        var input by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("值") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onFieldUpdate(path, input)
                    showDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FieldBoolRow(path: String, label: String, value: String, onFieldUpdate: (String, String) -> Unit) {
    val currentBool = value == "true"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFieldUpdate(path, if (currentBool) "false" else "true") }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Switch(checked = currentBool, onCheckedChange = { onFieldUpdate(path, if (it) "true" else "false") })
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun FieldSliderRow(path: String, label: String, value: String, range: ClosedFloatingPointRange<Float>, onFieldUpdate: (String, String) -> Unit) {
    val currentValue = value.toFloatOrNull() ?: range.start
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("%.0f".format(currentValue), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%.0f".format(range.start), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = currentValue,
            onValueChange = { onFieldUpdate(path, "%.0f".format(it)) },
            valueRange = range,
            steps = ((range.endInclusive - range.start).toInt() - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text("%.0f".format(range.endInclusive), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}


