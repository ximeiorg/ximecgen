package com.kingzcheung.ximecgen.bridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * Rust 核心 (libxime_config_core) 的 Kotlin 绑定。
 *
 * 所有函数返回统一信封 {"ok":bool, "data"?:..., "error"?:...}，
 * 错误以数据返回而非异常，便于 UI 展示解析/校验详情。
 * 不再有 Kotlin fallback 解析器：native 加载失败时 [available] 为 false，
 * UI 层显示明确错误态（避免静默降级到行为不一致的解析）。
 */
object ConfigBridge {

    var available: Boolean = false
        private set

    val loadError: String?

    init {
        available = try {
            System.loadLibrary("xime_config_core")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        } catch (e: SecurityException) {
            false
        }
        loadError = if (available) null else "无法加载 libxime_config_core.so，请确认构建时包含 native 库"
    }

    /** YAML 文本 → 配置 JSON；失败返回 null（[lastError] 有原因）。 */
    fun parse(yaml: String): JSONObject? =
        unwrap(nativeParse(yaml)) { it.optJSONObject("data") }

    /** 配置 JSON → YAML 文本。 */
    fun toYaml(configJson: String): String? = unwrap(nativeToYaml(configJson)) { it.optString("data") }

    /** 对配置 JSON 做结构校验，返回 {valid, errors[], warnings[]}。 */
    fun validate(configJson: String): JSONObject? =
        unwrap(nativeValidate(configJson)) { it.optJSONObject("data") }

    /** 字段描述符数组。 */
    fun getFieldDescriptors(): JSONArray? =
        unwrap(nativeGetFieldDescriptors()) { it.optJSONArray("data") }

    /**
     * 批量应用修改操作，返回新的配置 JSON。
     * ops 形如 [{"op":"set","path":"/a/b","value":1}, ...]（JSON Pointer 寻址）。
     * 空配置视为 {}——新建空白配置后的第一批 ops 也能直接生效。
     */
    fun applyOps(configJson: String, ops: JSONArray): JSONObject? =
        unwrap(nativeApplyOps(configJson.ifEmpty { "{}" }, ops.toString())) { it.optJSONObject("data") }

    /** 统一解信封；失败时记录 [lastError] 并返回 null。 */
    private fun <T> unwrap(raw: String, extract: (JSONObject) -> T?): T? {
        if (raw.isEmpty()) {
            lastError = "native 返回为空"
            return null
        }
        val obj = JSONObject(raw)
        if (!obj.optBoolean("ok")) {
            lastError = obj.optString("error", "未知错误")
            return null
        }
        val data = extract(obj)
        lastError = if (data == null) "返回数据结构异常" else null
        return data
    }

    /** 最近一次失败的错误信息（用于 UI 展示）。 */
    var lastError: String? = null
        private set

    private external fun nativeParse(yamlStr: String): String
    private external fun nativeToYaml(jsonStr: String): String
    private external fun nativeValidate(jsonStr: String): String
    private external fun nativeGetFieldDescriptors(): String
    private external fun nativeApplyOps(jsonStr: String, opsStr: String): String
}
