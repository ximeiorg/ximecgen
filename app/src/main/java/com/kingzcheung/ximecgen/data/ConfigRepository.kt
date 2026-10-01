package com.kingzcheung.ximecgen.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RecentFile(val uri: String, val name: String, val time: Long)

data class InternalFile(val name: String, val time: Long)

/** App 内部配置库：保存到 filesDir/configs，离线可用，导出时再走 SAF。 */
object InternalStore {
    private fun dir(context: Context) = File(context.filesDir, "configs").apply { mkdirs() }

    fun list(context: Context): List<InternalFile> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".yaml") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { InternalFile(it.name.removeSuffix(".yaml"), it.lastModified()) }
            ?: emptyList()

    fun write(context: Context, name: String, yaml: String): Result<Unit> = runCatching {
        val safe = name.replace(Regex("[/\\\\]"), "_").ifEmpty { "xime.custom" }
        File(dir(context), "$safe.yaml").writeText(yaml)
    }

    fun read(context: Context, name: String): String? =
        File(dir(context), "$name.yaml").takeIf { it.isFile }?.readText()

    fun delete(context: Context, name: String): Boolean =
        File(dir(context), "$name.yaml").delete()
}

/** 最近打开的文件列表（SharedPreferences 持久化 SAF URI）。 */
class RecentFilesStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun all(): List<RecentFile> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            JSONArray(raw).let { arr ->
                (0 until arr.length()).map { i ->
                    arr.optJSONObject(i)!!.let {
                        RecentFile(it.getString("uri"), it.getString("name"), it.getLong("time"))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, uri: Uri, name: String) {
        // 持久化读写授权，重启后仍可直接打开
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val rest = all().filter { it.uri != uri.toString() }
        val updated = listOf(RecentFile(uri.toString(), name, System.currentTimeMillis())) + rest
        val trimmed = updated.take(MAX)
        prefs.edit().putString(KEY, JSONArray().apply { trimmed.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name).put("time", it.time)) } }.toString()).apply()
    }

    fun remove(uri: String) {
        val updated = all().filter { it.uri != uri }
        prefs.edit().putString(KEY, JSONArray().apply { updated.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name).put("time", it.time)) } }.toString()).apply()
    }

    private companion object {
        const val KEY = "list"
        const val MAX = 10
    }
}

/** SAF 文档读写。 */
object DocumentIo {

    /** 查询文档的显示名（URI lastPathSegment 对 DocumentsUI 常是数字 id，不可直接用）。 */
    fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    fun read(context: Context, uri: Uri): Result<String> = runCatching {
        context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.use { it.readText() }
        ?: error("无法打开文件")
    }

    fun write(context: Context, uri: Uri, text: String): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")
            ?.bufferedWriter()?.use { it.write(text) }
        ?: error("无法写入文件")
    }
}

/** 常用配置模板条目（来自 Xime 官方布局子索引 index.ximei.me/layouts/index.yaml）。 */
data class TemplateEntry(
    val id: String,
    val title: String,
    val description: String,
    val downloadUrl: String,
)

object TemplateFetcher {

    const val TEMPLATE_URL =
        "https://raw.githubusercontent.com/ximeiorg/Xime/main/app/src/main/assets/xime.yaml"
    private const val GITHUB_REDIRECT_URL =
        "https://github.com/ximeiorg/Xime/raw/main/app/src/main/assets/xime.yaml"

    /** Xime 官方布局子索引（xime-index 仓库 scripts/ci-update.py 自动生成）。 */
    const val LAYOUTS_INDEX_URL = "https://index.ximei.me/layouts/index.yaml"

    /**
     * 拉取布局子索引并解析为模板目录。失败返回 null（调用方提示网络问题）。
     * 索引字段：id/name/description/currentVersion/versions[].downloadUrl[].url。
     */
    suspend fun fetchLayoutIndex(): List<TemplateEntry>? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val raw = fetchRaw(LAYOUTS_INDEX_URL) ?: return@runCatching null
                parseLayoutsIndex(raw)
            }.onFailure {
                android.util.Log.w("TemplateFetcher", "layouts index error", it)
            }.getOrNull()
        }

    /** 极简解析（避免引入 YAML 依赖）：逐行状态机，抓取布局条目的 id/name/description/首个 downloadUrl。 */
    internal fun parseLayoutsIndex(raw: String): List<TemplateEntry> {
        val entries = mutableListOf<TemplateEntry>()
        var curId: String? = null
        var curName = ""
        var curDesc = ""
        var curUrl: String? = null
        val lines = raw.lines()
        fun flush() {
            val id = curId
            if (id != null && curUrl != null) {
                entries.add(TemplateEntry(id, curName.ifEmpty { id }, curDesc.ifEmpty { "键盘布局模板" }, curUrl!!))
            }
            curId = null; curName = ""; curDesc = ""; curUrl = null
        }
        for (line in lines) {
            val t = line.trim()
            when {
                t.startsWith("- id:") -> { flush(); curId = t.removePrefix("- id:").trim().trim('"', '\'') }
                t.startsWith("name:") && curId != null && curName.isEmpty() ->
                    curName = t.removePrefix("name:").trim().trim('"', '\'')
                t.startsWith("description:") && curId != null ->
                    curDesc = t.removePrefix("description:").trim().trim('"', '\'')
                t.startsWith("- url:") && curId != null && curUrl == null ->
                    curUrl = t.removePrefix("- url:").trim().trim('"', '\'')
            }
        }
        flush()
        return entries
    }

    /** 内置默认模板（新建配置用）。 */
    fun loadBundled(context: Context): String =
        context.assets.open("default_xime.yaml").bufferedReader().use { it.readText() }

    /** 拉取仓库最新默认模板；任何网络问题返回 null（调用方回退内置副本）。 */
    suspend fun fetchRemote(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            fetch(TEMPLATE_URL) ?: fetch(GITHUB_REDIRECT_URL)
        }.getOrNull()
    }

    /** 按 index.yaml 给出的 URL 直接拉取模板内容；无效内容返回 null。 */
    suspend fun fetchTemplate(entry: TemplateEntry): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fetch(entry.downloadUrl)
        }

    private fun fetch(url: String): String? = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "ximecgen")
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            conn.inputStream.bufferedReader().use { it.readText() }
                .takeIf { it.contains("color_schemes") || it.contains("keyboard") }  // 简单有效性检查（防代理劫持页）
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun fetchRaw(url: String): String? = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "ximecgen")
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
