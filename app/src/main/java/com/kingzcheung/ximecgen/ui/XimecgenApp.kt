package com.kingzcheung.ximecgen.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kingzcheung.ximecgen.data.TemplateFetcher
import com.kingzcheung.ximecgen.ui.editor.EditorScreen
import com.kingzcheung.ximecgen.vm.ConfigViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 顶层导航：home（文件管理）/ editor（四分区编辑 + 悬浮预览）/ preview（全屏预览）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XimecgenApp() {    val nav = rememberNavController()
    val vm: ConfigViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            vm.openUri(context, it)
            nav.navigate("editor")
        }
    }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-yaml")) { uri ->
        uri?.let { vm.saveAs(context, it) }
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                state = state,
                vm = vm,
                onNew = {
                    vm.newFromTemplate(context)
                    nav.navigate("editor")
                },
                onOpen = { openLauncher.launch(arrayOf("text/*", "application/x-yaml", "application/octet-stream")) },
                onOpenRecent = { uri ->
                    vm.openUri(context, android.net.Uri.parse(uri))
                    nav.navigate("editor")
                },
                onExample = { id ->
                    vm.newFromExample(context, id) { nav.navigate("editor") }
                },
                onOpenInternal = { name ->
                    vm.openInternal(context, name)
                    nav.navigate("editor")
                },
            )
        }
        composable("editor") {
            var confirmExit by remember { mutableStateOf(false) }
            BackHandler(enabled = state.dirty) { confirmExit = true }
            EditorScreen(
                state = state,
                vm = vm,
                onBack = {
                    if (state.dirty) confirmExit = true else nav.popBackStack()
                },
                onSave = { vm.save(context) },
                onExport = {
                    val uri = state.fileUri
                    if (uri != null) vm.saveAs(context, uri) else createLauncher.launch(defaultExportName(state))
                },
                onShare = { vm.shareToXime(context) },
            )
            if (confirmExit) {
                AlertDialog(
                    onDismissRequest = { confirmExit = false },
                    title = { Text("有未保存的修改") },
                    text = { Text("离开将丢失这些修改，确定吗？") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmExit = false
                            nav.popBackStack()
                        }) { Text("离开") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            confirmExit = false
                            vm.save(context)
                        }) { Text("保存") }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: com.kingzcheung.ximecgen.vm.ConfigUiState,
    vm: ConfigViewModel,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onExample: (String) -> Unit,
    onOpenInternal: (String) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.refreshRecents(context)
        vm.refreshInternal(context)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Xime 配置生成器") }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (!state.engineReady) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                state.engineError ?: "native 引擎不可用",
                                modifier = Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    onClick = onNew,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("新建配置", style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.templateSource ?: "基于 Xime 默认配置模板",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FolderOpen, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("打开文件", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "打开 xime.custom.yaml / xime.yaml",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Extension, null, modifier = Modifier.size(18.dp))
                    Text(
                        "常用配置模板",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            items(TemplateFetcher.CATALOG.size) { i ->
                val entry = TemplateFetcher.CATALOG[i]
                val loading = state.loadingTemplate == entry.id
                ListItem(
                    headlineContent = { Text(entry.title) },
                    supportingContent = { Text(entry.description) },
                    leadingContent = { Icon(Icons.Default.Description, null) },
                    trailingContent = {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp))
                    },
                    modifier = Modifier.clickable(enabled = state.loadingTemplate == null) {
                        onExample(entry.id)
                    },
                )
            }
            if (state.internalConfigs.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Text(
                            "我的配置（App 内部）",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                items(state.internalConfigs.size) { i ->
                    val cfg = state.internalConfigs[i]
                    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
                    ListItem(
                        headlineContent = { Text(cfg.name) },
                        supportingContent = { Text("保存于 ${fmt.format(Date(cfg.time))}") },
                        leadingContent = { Icon(Icons.Default.Description, null) },
                        trailingContent = {
                            IconButton(onClick = { vm.deleteInternal(context, cfg.name) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.clickable { onOpenInternal(cfg.name) },
                    )
                }
            }
            if (state.recentFiles.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                        Text(
                            "最近打开",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                items(state.recentFiles.size) { i ->
                    val file = state.recentFiles[i]
                    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
                    ListItem(
                        headlineContent = { Text(file.name) },
                        supportingContent = { Text(fmt.format(Date(file.time))) },
                        leadingContent = { Icon(Icons.Default.Description, null) },
                        modifier = Modifier.clickable { onOpenRecent(file.uri) },
                    )
                }
            }
            item {
                Text(
                    "编辑后可在编辑器右上角保存；预览支持浅色/深色与手势编辑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/** 导出对话框默认文件名（内部名 / 文件名 / 默认，自动补 .yaml）。 */
private fun defaultExportName(state: com.kingzcheung.ximecgen.vm.ConfigUiState): String {
    val base = state.internalName ?: state.fileName ?: "xime.custom"
    return if (base.endsWith(".yaml")) base else "$base.yaml"
}
