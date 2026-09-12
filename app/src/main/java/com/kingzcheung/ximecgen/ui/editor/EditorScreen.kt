package com.kingzcheung.ximecgen.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kingzcheung.ximecgen.ui.editor.components.GestureEditorSheet
import com.kingzcheung.ximecgen.ui.preview.KeyboardPreview
import com.kingzcheung.ximecgen.ui.preview.buildPreviewData
import com.kingzcheung.ximecgen.vm.Ops
import com.kingzcheung.ximecgen.vm.ConfigUiState
import com.kingzcheung.ximecgen.vm.ConfigViewModel
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

// 窄屏/大字体下四个全称放不下：用 ScrollableTabRow 保证任何屏宽都不截断，
// 宽屏时四个 tab 仍然全部可见
private val TAB_TITLES = listOf("常规", "主题配色", "键盘外观", "布局与手势")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: ConfigUiState,
    vm: ConfigViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var previewDark by rememberSaveable { mutableStateOf(false) }
    var editingGestureKey by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.loadError) {
        state.loadError?.let {
            snackbar.showSnackbar(it)
            vm.clearLoadError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                title = {
                    Column {
                        Text(state.fileName ?: "新建配置", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                state.dirty -> "未保存"
                                state.internalName != null -> "已保存到配置库"
                                state.fileUri != null -> "已导出到外部文件"
                                else -> state.templateSource ?: "未保存"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.dirty) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    // 校验状态
                    BadgedBox(badge = {
                        val errCount = state.validation.errors.size
                        if (errCount > 0) Badge { Text("$errCount") }
                    }) {
                        Icon(
                            if (state.validation.valid) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = if (state.validation.valid) "校验通过" else "校验有错误",
                            tint = if (state.validation.valid) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "分享到 Xime")
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "导出到文件")
                    }
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Save, contentDescription = "保存到配置库")
                    }
                },
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                ) {
                    TAB_TITLES.forEachIndexed { i, title ->
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            if (wide) {
                // 宽屏：左编辑右实时预览（M3 adaptive two-pane）
                Row {
                    EditorColumn(state, vm, selectedTab, Modifier.weight(0.58f).fillMaxHeight())
                    PreviewPane(
                        state = state,
                        dark = previewDark,
                        onDarkChange = { previewDark = it },
                        onKeyClick = { editingGestureKey = it },
                        modifier = Modifier.weight(0.42f).fillMaxHeight(),
                    )
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    EditorColumn(state, vm, selectedTab, Modifier.fillMaxSize())
                    // 窄屏：悬浮球 + 预览浮层
                    FloatingPreview(
                        state = state,
                        dark = previewDark,
                        onDarkChange = { previewDark = it },
                        onKeyClick = { editingGestureKey = it },
                    )
                }
            }
        }
    }

    // 预览点按按键 → 手势编辑（中文 26 键；英文键盘请到布局与手势页）
    editingGestureKey?.let { keyId ->
        val config = remember(state.configJson) { JSONObject(state.configJson) }
        val binding = config.optJSONObject("keyboard")?.optJSONObject("qwerty")
            ?.optJSONObject("keys")?.optJSONObject(keyId)
        GestureEditorSheet(
            keyId = keyId,
            binding = binding,
            onDismiss = { editingGestureKey = null },
            onSave = { bindingOut ->
                vm.dispatch(
                    JSONArray().put(Ops.set("/keyboard/qwerty/keys/$keyId", bindingOut))
                )
                editingGestureKey = null
            },
        )
    }
}

@Composable
private fun EditorColumn(
    state: ConfigUiState,
    vm: ConfigViewModel,
    selectedTab: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ValidationBanner(state)
        when (selectedTab) {
            0 -> GeneralTab(state, vm)
            1 -> ThemeTab(state, vm)
            2 -> AppearanceTab(state, vm)
            else -> LayoutTab(state, vm)
        }
    }
}

@Composable
private fun ValidationBanner(state: ConfigUiState) {
    val v = state.validation
    if (!v.valid) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "配置有 ${v.errors.size} 个错误",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                v.errors.take(3).forEach {
                    Text(
                        "${it.path}: ${it.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (v.errors.size > 3) {
                    Text(
                        "…共 ${v.errors.size} 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    } else if (v.warnings.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("提示", style = MaterialTheme.typography.labelLarge)
                v.warnings.take(2).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (v.warnings.size > 2) {
                    Text("…共 ${v.warnings.size} 项", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 常驻预览面板（宽屏右栏 / 全屏预览页共用）。 */
@Composable
fun PreviewPane(
    state: ConfigUiState,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onKeyClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    schemaName: String = "五笔拼音",
) {
    Column(modifier.padding(8.dp)) {
        DarkToggle(dark, onDarkChange)
        val config = remember(state.configJson) { JSONObject(state.configJson) }
        val preview = remember(state.configJson, dark) { buildPreviewData(config, dark) }
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
        ) {
            KeyboardPreview(
                preview = preview,
                dark = dark,
                modifier = Modifier.fillMaxSize(),
                schemaName = schemaName,
                onKeyClick = onKeyClick,
            )
        }
    }
}

@Composable
fun DarkToggle(dark: Boolean, onDarkChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        listOf(false to "浅色", true to "深色").forEachIndexed { i, (v, label) ->
            SegmentedButton(
                selected = dark == v,
                onClick = { onDarkChange(v) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
            ) { Text(label) }
        }
    }
}

/**
 * 悬浮球 + 可展开预览浮层（纯应用内浮层，无需系统悬浮窗权限）。
 * 悬浮球可拖动，显示当前主题主色；点击展开实时预览卡片。
 */
@Composable
fun FloatingPreview(
    state: ConfigUiState,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onKeyClick: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
    val ballSizePx = with(density) { 52.dp.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    if (!expanded) {
        val config = remember(state.configJson) { JSONObject(state.configJson) }
        val preview = remember(state.configJson) { buildPreviewData(config, dark) }
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (screenW - ballSizePx - 40f + offsetX).roundToInt(),
                        (screenH * 0.62f + offsetY).roundToInt(),
                    )
                }
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .size(52.dp)
                .background(preview.primary)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("预", color = MaterialTheme.colorScheme.onPrimary)
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .shadow(12.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DarkToggle(dark, onDarkChange)
                    Box(Modifier.weight(1f))
                    IconButton(onClick = { expanded = false }) {
                        Icon(Icons.Default.Close, contentDescription = "收起预览")
                    }
                }
                val config = remember(state.configJson) { JSONObject(state.configJson) }
                val preview = remember(state.configJson, dark) { buildPreviewData(config, dark) }
                KeyboardPreview(
                    preview = preview,
                    dark = dark,
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    onKeyClick = onKeyClick,
                )
            }
        }
    }
}
