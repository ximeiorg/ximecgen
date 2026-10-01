package com.kingzcheung.ximecgen.vm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.ximecgen.bridge.ConfigBridge
import com.kingzcheung.ximecgen.data.DocumentIo
import com.kingzcheung.ximecgen.data.InternalFile
import com.kingzcheung.ximecgen.data.InternalStore
import com.kingzcheung.ximecgen.data.RecentFile
import com.kingzcheung.ximecgen.data.RecentFilesStore
import com.kingzcheung.ximecgen.data.TemplateEntry
import com.kingzcheung.ximecgen.data.TemplateFetcher
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class ValidationErrorUi(val path: String, val message: String, val severity: String)

data class ValidationUi(
    val valid: Boolean = true,
    val errors: List<ValidationErrorUi> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class FieldDescriptorUi(
    val path: String,
    val label: String,
    val section: String,
    val fieldType: String,
    val description: String = "",
    val default: String = "",
    val options: List<String> = emptyList(),
)

data class ConfigUiState(
    val engineReady: Boolean = ConfigBridge.available,
    val engineError: String? = ConfigBridge.loadError,
    val configJson: String = "",
    val validation: ValidationUi = ValidationUi(),
    val descriptors: List<FieldDescriptorUi> = emptyList(),
    val dirty: Boolean = false,
    val fileName: String? = null,
    val fileUri: Uri? = null,
    val internalName: String? = null,
    val isTemplate: Boolean = false,
    val templateSource: String? = null,
    val loadError: String? = null,
    val recentFiles: List<RecentFile> = emptyList(),
    val internalConfigs: List<InternalFile> = emptyList(),
    val loadingTemplate: String? = null,
    /** 模板目录（来自 index.ximei.me 布局子索引；null=尚未拉取，空列表=拉取失败）。 */
    val templates: List<TemplateEntry>? = null,
    val yamlOut: String = "",
) {
    val hasFile: Boolean get() = configJson.isNotEmpty()
}

class ConfigViewModel : ViewModel() {

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state

    private val recents = mutableMapOf<Context, RecentFilesStore>()
    private val applyMutex = Mutex()
    private var validationJob: Job? = null

    fun recentStore(context: Context): RecentFilesStore =
        recents.getOrPut(context.applicationContext) { RecentFilesStore(context) }

    // ── 文件操作 ──

    /** 新建：内置模板立即加载，再异步尝试刷新网络最新模板。 */
    fun newFromTemplate(context: Context) {
        val bundled = TemplateFetcher.loadBundled(context)
        loadYaml(bundled, fileName = null, uri = null, isTemplate = true, source = "内置模板", internalName = null)
        refreshTemplate()
        refreshRecents(context)
    }

    /** 从模板目录新建（模板内容按 index.ximei.me 子索引给出的 URL 拉取）；成功后 onReady 再进编辑器。 */
    fun newFromExample(context: Context, entry: TemplateEntry, onReady: () -> Unit = {}) {
        if (_state.value.loadingTemplate != null) return
        _state.update { it.copy(loadingTemplate = entry.id) }
        viewModelScope.launch {
            val yaml = try {
                TemplateFetcher.fetchTemplate(entry)
            } finally {
                _state.update { it.copy(loadingTemplate = null) }
            }
            if (yaml == null) {
                android.widget.Toast.makeText(context, "模板拉取失败，请检查网络", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            loadYaml(yaml, fileName = null, uri = null, isTemplate = true, source = "网络模板（${entry.title}）", internalName = null)
            scheduleValidation()
            onReady()
        }
    }

    /** 异步拉取网络模板，成功且当前仍是模板态时替换。 */
    fun refreshTemplate() {
        viewModelScope.launch {
            val remote = TemplateFetcher.fetchRemote() ?: return@launch
            _state.update { s ->
                if (!s.isTemplate) return@update s
                val parsed = ConfigBridge.parse(remote) ?: return@update s.copy(loadError = ConfigBridge.lastError)
                s.copy(
                    configJson = parsed.toString(),
                    templateSource = "网络模板（ximeiorg/Xime@main）",
                    loadError = null,
                )
            }
            scheduleValidation()
        }
    }

    fun openUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            DocumentIo.read(context, uri)
                .onFailure { _state.update { s -> s.copy(loadError = "读取失败: ${it.message}") } }
                .onSuccess { text ->
                    val name = DocumentIo.displayName(context, uri)
                        ?: uri.lastPathSegment?.substringAfterLast('/')
                        ?: "未命名"
                    if (ConfigBridge.parse(text) == null) {
                        _state.update { s -> s.copy(loadError = "解析失败: ${ConfigBridge.lastError}") }
                        return@onSuccess
                    }
                    recentStore(context).add(context, uri, name)
                    loadYaml(text, fileName = name, uri = uri, isTemplate = false, internalName = null)
                    refreshRecents(context)
                }
        }
    }

    /** 保存到 App 内部配置库（filesDir/configs）。导出到外部文件用 export/saveAs。 */
    fun save(context: Context) {
        viewModelScope.launch {
            val yaml = currentYaml() ?: return@launch
            val s0 = _state.value
            val name = (s0.internalName ?: s0.fileName ?: "xime.custom").removeSuffix(".yaml")
            InternalStore.write(context, name, yaml)
                .onFailure { _state.update { s -> s.copy(loadError = "保存失败: ${it.message}") } }
                .onSuccess {
                    _state.update { s -> s.copy(dirty = false, yamlOut = yaml, internalName = name, loadError = null) }
                    android.widget.Toast.makeText(context, "已保存到配置库：$name", android.widget.Toast.LENGTH_SHORT).show()
                }
            refreshInternal(context)
        }
    }

    /** 从内部配置库打开。 */
    fun openInternal(context: Context, name: String) {
        val text = InternalStore.read(context, name)
        if (text == null) {
            _state.update { s -> s.copy(loadError = "配置不存在: $name") }
            return
        }
        if (ConfigBridge.parse(text) == null) {
            _state.update { s -> s.copy(loadError = "解析失败: ${ConfigBridge.lastError}") }
            return
        }
        loadYaml(text, fileName = name, uri = null, isTemplate = false, internalName = name)
        scheduleValidation()
        refreshInternal(context)
    }

    fun deleteInternal(context: Context, name: String) {
        InternalStore.delete(context, name)
        if (_state.value.internalName == name) {
            _state.update { it.copy(internalName = null, dirty = true) }
        }
        refreshInternal(context)
    }

    fun refreshInternal(context: Context) {
        _state.update { it.copy(internalConfigs = InternalStore.list(context)) }
    }

    /** 拉取布局子索引更新模板目录（已拉取过则跳过；失败置空列表以显示错误提示）。 */
    fun refreshTemplates(force: Boolean = false) {
        if (!force && _state.value.templates != null) return
        viewModelScope.launch {
            val list = TemplateFetcher.fetchLayoutIndex()
            _state.update { it.copy(templates = list.orEmpty()) }
        }
    }

    /** 导出到外部文件（SAF URI）：从外部打开的配置写回原文件，否则由导出对话框提供新 URI。 */
    fun saveAs(context: Context, uri: Uri) {
        viewModelScope.launch {
            val yaml = currentYaml() ?: return@launch
            DocumentIo.write(context, uri, yaml)
                .onFailure { _state.update { s -> s.copy(loadError = "导出失败: ${it.message}") } }
                .onSuccess {
                    val name = DocumentIo.displayName(context, uri)
                        ?: uri.lastPathSegment?.substringAfterLast('/')
                        ?: "未命名"
                    recentStore(context).add(context, uri, name)
                    _state.update { s -> s.copy(dirty = false, yamlOut = yaml, fileUri = uri, fileName = name, loadError = null) }
                    refreshRecents(context)
                    android.widget.Toast.makeText(context, "已导出：$name", android.widget.Toast.LENGTH_SHORT).show()
                }
        }
    }

    /** 分享到 Xime（ACTION_SEND + EXTRA_STREAM）；未安装 Xime 时退回系统分享面板。 */
    fun shareToXime(context: Context) {
        viewModelScope.launch {
            val yaml = currentYaml() ?: return@launch
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "xime.custom.yaml").apply { writeText(yaml) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/x-yaml"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val targets = context.packageManager.queryIntentActivities(share, 0)
            if (targets.isEmpty()) {
                android.widget.Toast.makeText(context, "未找到可接收配置的应用（未安装 Xime？）", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                context.startActivity(share.setPackage("com.kingzcheung.xime"))
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent.createChooser(share, "分享配置"))
            }
        }
    }

    fun removeRecent(context: Context, uri: String) {
        recentStore(context).remove(uri)
        refreshRecents(context)
    }

    fun refreshRecents(context: Context) {
        _state.update { it.copy(recentFiles = recentStore(context).all()) }
    }

    fun clearLoadError() = _state.update { it.copy(loadError = null) }

    // ── 编辑操作 ──

    /**
     * 应用一批 ops。失败时静默保留原状态（UI 通过 lastError 提示）。
     * configJson 是唯一状态源：编辑不再经过 YAML 往返。
     */
    fun dispatch(ops: JSONArray) {
        if (ops.length() == 0) return
        viewModelScope.launch {
            applyMutex.withLock {
                val current = _state.value.configJson
                val updated = ConfigBridge.applyOps(current, ops) ?: run {
                    _state.update { s -> s.copy(loadError = "修改失败: ${ConfigBridge.lastError}") }
                    return@withLock
                }
                _state.update { s -> s.copy(configJson = updated.toString(), dirty = true) }
                scheduleValidation()
            }
        }
    }

    /** 便捷方法：按描述符类型把字符串值类型化后 set 到指定路径。 */
    fun setField(path: String, value: String) {
        val descriptor = _state.value.descriptors.firstOrNull { it.path == path }
        dispatch(JSONArray().put(opSet(path, coerce(descriptor?.fieldType, value, path))))
    }

    fun setColorField(path: String, argb: Long) {
        dispatch(JSONArray().put(opSet(path, argb)))
    }

    fun clearField(path: String) {
        dispatch(JSONArray().put(opRemove(path)))
    }

    // ── YAML 导出 ──

    suspend fun currentYaml(): String? {
        val json = _state.value.configJson
        val yaml = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            ConfigBridge.toYaml(json)
        }
        if (yaml == null) {
            _state.update { s -> s.copy(loadError = "导出 YAML 失败: ${ConfigBridge.lastError}") }
        }
        return yaml
    }

    // ── 内部 ──

    private fun loadYaml(
        yaml: String,
        fileName: String?,
        uri: Uri?,
        isTemplate: Boolean,
        source: String? = null,
        internalName: String? = null,
    ) {
        val parsed = ConfigBridge.parse(yaml)
        if (parsed == null) {
            _state.update { s -> s.copy(loadError = "解析失败: ${ConfigBridge.lastError}") }
            return
        }
        _state.update { s ->
            s.copy(
                configJson = parsed.toString(),
                fileName = fileName,
                fileUri = uri,
                internalName = internalName,
                isTemplate = isTemplate,
                templateSource = if (isTemplate) source else null,
                dirty = false,
                loadError = null,
            )
        }
        ensureDescriptors()
        scheduleValidation()
    }

    private fun ensureDescriptors() {
        if (_state.value.descriptors.isNotEmpty()) return
        val arr = ConfigBridge.getFieldDescriptors() ?: return
        val list = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                FieldDescriptorUi(
                    path = o.getString("path"),
                    label = o.getString("label"),
                    section = o.optString("section"),
                    fieldType = o.optString("fieldType", "text"),
                    description = o.optString("description"),
                    default = o.optString("default"),
                    options = o.optJSONArray("options")?.let { ja -> (0 until ja.length()).map { ja.optString(it) } } ?: emptyList(),
                )
            }
        }
        _state.update { it.copy(descriptors = list) }
    }

    /** 校验防抖：拖动滑块时每次都过 JNI 会卡，300ms 合并一次。 */
    private fun scheduleValidation() {
        validationJob?.cancel()
        validationJob = viewModelScope.launch {
            delay(300)
            val json = _state.value.configJson
            val v = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                ConfigBridge.validate(json)
            }
            val ui = v?.let {
                val errors = it.optJSONArray("errors") ?: JSONArray()
                ValidationUi(
                    valid = it.optBoolean("valid"),
                    errors = (0 until errors.length()).mapNotNull { i ->
                        errors.optJSONObject(i)?.let { e ->
                            ValidationErrorUi(
                                path = e.optString("path"),
                                message = e.optString("message"),
                                severity = e.optString("severity", "error"),
                            )
                        }
                    },
                    warnings = it.optJSONArray("warnings")?.let { wa -> (0 until wa.length()).map { i -> wa.optString(i) } } ?: emptyList(),
                )
            } ?: ValidationUi(valid = false, errors = listOf(ValidationErrorUi("", ConfigBridge.lastError ?: "校验失败", "error")))
            _state.update { s -> s.copy(validation = ui) }
        }
    }

    private fun coerce(fieldType: String?, value: String, path: String): Any = when (fieldType) {
        "number" -> when {
            value.isEmpty() -> JSONObject.NULL
            value.contains('.') -> value.toDoubleOrNull() ?: value
            else -> value.toLongOrNull() ?: value
        }
        "boolean" -> value.equals("true", ignoreCase = true)
        "color" -> parseColor(value) ?: value
        else -> value
    }

    companion object {
        /** "0xRRGGBB"/"#RRGGBB"/十进制 → Long；无法解析返回 null（保持字符串让校验报错）。 */
        fun parseColor(text: String): Long? {
            val t = text.trim().removePrefix("#")
            val hex = when {
                t.startsWith("0x", ignoreCase = true) -> t.substring(2)
                else -> t
            }
            return hex.toLongOrNull(16)?.takeIf { it in 1..0xFFFF_FFFF }
                ?: text.trim().toLongOrNull()?.takeIf { it in 1..0xFFFF_FFFF }
        }
    }
}

/** ops 构造工具（JSON Pointer 寻址）。 */
object Ops {
    fun set(path: String, value: Any?): JSONObject =
        JSONObject().put("op", "set").put("path", path)
            .put("value", value ?: JSONObject.NULL)

    fun add(path: String, value: Any?): JSONObject =
        JSONObject().put("op", "add").put("path", path)
            .put("value", value ?: JSONObject.NULL)

    fun remove(path: String): JSONObject =
        JSONObject().put("op", "remove").put("path", path)

    fun setRootChild(path: String, map: Map<String, Any?>): JSONObject =
        set(path, JSONObject(map))
}

/** vm.setField 用的顶层便捷函数。 */
fun opSet(path: String, value: Any?): JSONObject = Ops.set(path, value)
fun opAdd(path: String, value: Any?): JSONObject = Ops.add(path, value)
fun opRemove(path: String): JSONObject = Ops.remove(path)
