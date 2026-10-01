package com.kingzcheung.ximecgen.ui.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.twotone.KeyboardAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

// ── 预览数据模型：从 configJson 一次性解析，重组时避免重复 JNI/JSON 工作 ──

data class PreviewBackground(
    val type: String = "solid",
    val light: List<Color> = emptyList(),
    val dark: List<Color> = emptyList(),
    val angle: Float = 90f,
    val overlayLight: Float = 0f,
    val overlayDark: Float = 0f,
    val isImage: Boolean = false,
) {
    fun color(isDark: Boolean): Color =
        (if (isDark) dark else light).firstOrNull()
            ?: (if (isDark) Color(0xFF202125) else Color(0xFFE3E4E8))
}

/** 功能键内置图标（渲染时映射到 Material 图标），NONE 表示显示文本键面。 */
enum class PreviewKeyIcon { NONE, SHIFT, DELETE, EARTH, SYMBOL, EMOJI, VOICE }

data class PreviewKeyInfo(
    val id: String,
    val mainLabel: String,
    val isIcon: Boolean = false,
    val isFunction: Boolean = false,
    val icon: PreviewKeyIcon = PreviewKeyIcon.NONE,
    val width: Float = 1f,
    val swipeUpLabel: String? = null,
    val swipeDownLabel: String? = null,
    val compact: Boolean = false,
)

data class PreviewData(
    val rows: List<List<PreviewKeyInfo>> = emptyList(),
    val cornerRadius: Int = 8,
    val spacingX: Float = 2f,
    val spacingY: Float = 4.25f,
    val shadowEnabled: Boolean = true,
    val elevation: Float = 0.5f,
    val background: PreviewBackground = PreviewBackground(),
    val keyBg: Color = Color.White,
    val specialKeyBg: Color = Color(0xFFE0DBF5),
    val keyText: Color = Color(0xFF202124),
    val candidateText: Color = Color(0xFF1A73E8),
    val primary: Color = Color(0xFF8F73E2),
)

// ── 布局模型：对齐 Xime 3.0（layout.rows 是含功能键的完整可配置行列表）──

/** 可被 layout.rows 引用的功能键 id（对齐 Xime KeysConfigHelper.FUNCTION_KEY_IDS）。 */
val FUNCTION_KEY_IDS = setOf(
    "shift", "delete", "enter", "space", "mode_change", "symbol", "emoji", "earth", "voice", "comma",
)

/** 用普通按键底色的功能键（其余用特殊键底色），对齐 Xime 各 Cell 组件的取色。 */
private val SOFT_FUNCTION_KEYS = setOf("comma", "earth", "space")

/** 功能键内置列宽：显式 keys.<id>.width 优先（对齐 Xime functionKeyWidth）。 */
private fun defaultFunctionKeyWidth(id: String): Float = when (id) {
    "shift", "delete" -> 1.4f
    "mode_change", "enter" -> 1.2f
    "earth", "comma" -> 0.8f
    "space" -> 3f
    else -> 1f
}

/**
 * 无 layout.rows 时的兜底行 = 内置 xime.yaml 的行布局。
 * Xime 的回退链：custom 的 rows → 内置 xime.yaml 的 rows（含 shift/delete）→
 * KeysConfigHelper.DEFAULT_ZH_ROWS（裸字母行，仅在连内置配置都缺 rows 时出现）。
 * 模板类 custom yaml（如小鹤双拼）不写 rows，实际渲染带功能键，因此兜底必须含 shift/delete。
 */
private val DEFAULT_ROW_IDS = listOf(
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("shift", "z", "x", "c", "v", "b", "n", "m", "delete"),
    listOf("mode_change", "comma", "space", "earth", "enter"),
)

private const val MAX_ROWS = 5

/**
 * 解析并规范化 layout.rows（合并键拼接为 id、最多 5 行、缺失行补内置默认），
 * 供预览与布局编辑器共用。
 */
fun layoutRowIds(section: JSONObject?): List<List<String>> = normalizeRows(parseLayoutRows(section))

/** 内置默认行（3 字母行 + 控制行），用于"创建默认行布局"。 */
fun defaultLayoutRows(): List<List<String>> = DEFAULT_ROW_IDS

/** keys.<id>.width 显式值（>0）优先，否则回退默认。 */
private fun configuredWidth(keys: JSONObject, id: String, fallback: Float): Float =
    ((keys.optJSONObject(id)?.opt("width")) as? Number)?.toFloat()?.takeIf { it > 0f } ?: fallback

/**
 * 解析 keyboard.<section>.layout.rows：每行是 id 列表；子数组为合并键（组内 id 拼接，
 * 如 [q, w] → "qw"）；整行字符串手动拆分兜底（如 "z, [x, c], v"）。
 */
private fun parseLayoutRows(section: JSONObject?): List<List<String>> {
    val arr = section?.optJSONObject("layout")?.optJSONArray("rows") ?: return emptyList()
    val rows = mutableListOf<List<String>>()
    for (i in 0 until arr.length()) {
        val row = when (val r = arr.opt(i)) {
            is JSONArray -> (0 until r.length()).mapNotNull { j ->
                when (val item = r.opt(j)) {
                    is String -> item.trim().takeIf { it.isNotEmpty() }
                    is JSONArray -> (0 until item.length())
                        .mapNotNull { k -> item.optString(k).trim().takeIf { it.isNotEmpty() } }
                        .joinToString("")
                        .takeIf { it.isNotEmpty() }
                    else -> null
                }
            }
            is String -> splitScalarRow(r)
            else -> emptyList()
        }
        if (row.isNotEmpty()) rows.add(row)
    }
    return rows
}

/** 标量行手动拆分：逗号为键分隔，方括号内为合并键组（组内逗号/空格丢弃，[x, c] → xc，对齐 Xime scalarRowToKeyIds）。 */
private fun splitScalarRow(content: String): List<String> {
    val text = content.trim()
    if (text.isEmpty()) return emptyList()
    if (!text.contains(',') && !text.contains('[')) return listOf(text)
    val items = mutableListOf<String>()
    val buf = StringBuilder()
    var depth = 0
    fun flushItem() {
        val t = buf.toString().trim()
        if (t.isNotEmpty()) items.add(t)
        buf.clear()
    }
    for (ch in text) {
        when {
            ch == '[' -> { depth++; if (depth == 1) buf.clear() else buf.append(ch) }
            ch == ']' -> {
                depth--
                when {
                    depth == 0 -> flushItem()
                    depth < 0 -> depth = 0
                    else -> buf.append(ch)
                }
            }
            ch == ',' && depth == 0 -> flushItem()
            ch == ',' && depth > 0 -> Unit // 组内逗号仅分隔字母，拼接时丢弃
            ch == ' ' && depth > 0 -> Unit // 组内空格一并丢弃（[x, c] → xc）
            else -> buf.append(ch)
        }
    }
    flushItem()
    return items
}

/**
 * 规范化行：最多 [MAX_ROWS] 行，缺失行用内置默认补齐（少于 4 行会补出默认控制行），
 * 空行截断；全部为空时用内置 4 行（对齐 Xime normalizeQwertyRows）。
 */
private fun normalizeRows(rows: List<List<String>>): List<List<String>> {
    val out = mutableListOf<List<String>>()
    for (i in 0 until MAX_ROWS) {
        val row = rows.getOrNull(i) ?: DEFAULT_ROW_IDS.getOrNull(i) ?: break
        if (row.isEmpty()) break
        out.add(row)
    }
    return out.ifEmpty { DEFAULT_ROW_IDS }
}

/** 从配置 JSON 构建预览数据。dark 决定取深色还是浅色分支。 */
fun buildPreviewData(configJson: JSONObject, dark: Boolean, keyboardId: String = "qwerty"): PreviewData {
    val style = configJson.optJSONObject("style")
    val schemes = configJson.optJSONObject("color_schemes")
    val keyboard = configJson.optJSONObject("keyboard")

    // ── 主题选择：style.color_scheme 为 {light, dark} 或标量 ──
    var schemeId: String? = null
    when (val cs = style?.opt("color_scheme")) {
        is JSONObject -> schemeId = cs.optString(if (dark) "dark" else "light").ifEmpty { null }
        is String -> schemeId = cs.ifEmpty { null }
    }
    val scheme = schemeId?.let { schemes?.optJSONObject(it) }

    fun schemeColor(name: String): Long? =
        scheme?.opt(name) as? Long ?: (scheme?.opt(name) as? Number)?.toLong()

    fun keyboardColor(name: String): Long? =
        (keyboard?.optJSONObject("colors")?.opt(name) as? Number)?.toLong()

    // ── 背景：scheme.keyboard_background 优先，回退 keyboard_bg_color ──
    var background = PreviewBackground()
    val bg = scheme?.optJSONObject("keyboard_background")
    if (bg != null) {
        when (bg.optString("type")) {
            "solid" -> background = PreviewBackground(
                type = "solid",
                light = listOf(bg.optLongOrColor("color")),
                dark = listOf(bg.optLongOrColor("color_dark", fallback = bg.optLongOrColor("color"))),
            )
            "gradient" -> background = PreviewBackground(
                type = "gradient",
                light = bg.optColorsArray("colors"),
                dark = bg.optColorsArray("colors_dark").ifEmpty { bg.optColorsArray("colors") },
                angle = bg.optDouble("angle", 90.0).toFloat(),
            )
            "image" -> background = PreviewBackground(
                type = "image",
                isImage = true,
                overlayLight = bg.optDouble("overlay_alpha", 0.0).toFloat(),
                overlayDark = bg.optDouble("overlay_alpha_dark", 0.0).toFloat(),
                light = listOf(Color(0xFF8D93A1)),
                dark = listOf(Color(0xFF3A3F4C)),
            )
        }
    } else {
        val kbBg = schemeColor("keyboard_bg_color") ?: keyboardColor("keyboard_bg_color")
        // 深色回退链：scheme → colors → 浅色值 → 内置深灰
        val kbBgDark: Color = schemeColor("keyboard_bg_color_dark")?.toColor()
            ?: keyboardColor("keyboard_bg_color_dark")?.toColor()
            ?: kbBg?.toColor()
            ?: Color(0xFF202125)
        background = PreviewBackground(
            light = listOf(kbBg?.toColor() ?: Color(0xFFE3E4E8)),
            dark = listOf(kbBgDark),
        )
    }

    val primary = schemeColor("primary_color")?.toColor() ?: Color(0xFF8F73E2)
    val keyboardBgColor = background.color(dark)

    val keyBg = (schemeColor(if (dark) "key_bg_color_dark" else "key_bg_color")
        ?: keyboardColor(if (dark) "key_bg_color_dark" else "key_bg_color")
        ?: if (dark) 0x60FFFFFF else 0xFFFFFF).toColor()

    // 特殊键：官方行为——浅色下主题色叠加键盘背景（淡紫），深色下直接用主题色（饱和紫）
    val explicitSpecial = schemeColor(if (dark) "special_key_bg_color_dark" else "special_key_bg_color")
        ?: keyboardColor(if (dark) "special_key_bg_color_dark" else "special_key_bg_color")
    val specialKeyBg = explicitSpecial?.toColor()
        ?: if (dark) primary else primary.copy(alpha = 0.28f).compositeOver(keyboardBgColor)

    val keyText = (schemeColor(if (dark) "key_text_color_dark" else "key_text_color")
        ?: keyboardColor(if (dark) "key_text_color_dark" else "key_text_color")
        ?: if (dark) 0xE8EAEDL else 0x202124L).toColor()

    val candidateText = (schemeColor(if (dark) "candidate_text_color_dark" else "candidate_text_color")
        ?: keyboardColor(if (dark) "candidate_text_color_dark" else "candidate_text_color")
        ?: if (dark) 0x8AB4F8L else 0x1A73E8L).toColor()

    // ── 布局与按键 ──
    val qwerty = keyboard?.optJSONObject(keyboardId)
    val keys = qwerty?.optJSONObject("keys") ?: JSONObject()
    val compact = qwerty?.optString("button_layout", "standard") == "compact"

    fun keyInfo(id: String) = keyCapInfo(keys, id, compact)

    val key = keyboard?.optJSONObject("key") ?: JSONObject()
    return PreviewData(
        rows = normalizeRows(parseLayoutRows(qwerty)).map { r -> r.map { keyInfo(it) } },
        cornerRadius = key.optInt("corner_radius", 8).coerceIn(0, 48),
        spacingX = key.optDouble("spacing_x", 2.0).toFloat().coerceIn(0f, 24f),
        spacingY = key.optDouble("spacing_y", 4.25).toFloat().coerceIn(0f, 24f),
        shadowEnabled = keyboard?.optJSONObject("shadow")?.optBoolean("enabled", true) ?: true,
        elevation = keyboard?.optJSONObject("shadow")?.optDouble("elevation", 0.5)?.toFloat() ?: 0.5f,
        background = background,
        keyBg = keyBg,
        specialKeyBg = specialKeyBg,
        keyText = keyText,
        candidateText = candidateText,
        primary = primary,
    )
}

// org.json 的 optLong 在缺字段时返回 0L，这里用可空读取
private fun JSONObject.optLongOrColor(name: String, fallback: Color? = null): Color =
    (opt(name) as? Number)?.toLong()?.toColor() ?: fallback ?: Color(0xFFE3E4E8)

/**
 * 手势绑定 → 键帽显示信息（预览与编辑器共用，保证两边显示一致）：
 * - 功能键按 id 分派内置图标/标签（可被 keys.<id>.tap 覆盖），见 [functionKeyInfo]
 * - 字母键 tap 取 label/value；@ 前缀为图标；含字母的显示统一大写（对齐 Xime getKeyDisplayLabel）
 * - display 为 "bubble" 的上滑/下滑手势是按键时弹出的气泡，不印在键面上
 */
fun keyCapInfo(keys: JSONObject, id: String, compact: Boolean = false): PreviewKeyInfo {
    val binding = keys.optJSONObject(id) ?: JSONObject()
    if (id in FUNCTION_KEY_IDS) return functionKeyInfo(id, keys, binding, compact)

    val tap = binding.opt("tap")
    var main = gestureLabel(tap) ?: id
    var isIcon = false
    if (main.startsWith("@")) {
        isIcon = true
        main = main.removePrefix("@")
    }
    if (main.any { c -> c in 'a'..'z' || c in 'A'..'Z' }) {
        main = main.uppercase()
    }
    // 上滑/下滑提示保留完整多行文本（如 "ue\nve"），裁剪策略在渲染层按布局模式执行
    val swipeUp = binding.opt("swipe_up")
        ?.takeIf { gestureDisplay(it) != "bubble" }
        ?.let { gestureSurfaceText(it) }?.takeIf { it.isNotBlank() }
    val swipeDown = binding.opt("swipe_down")
        ?.takeIf { gestureDisplay(it) != "bubble" }
        ?.let { gestureSurfaceText(it) }?.takeIf { it.isNotBlank() }
    return PreviewKeyInfo(
        id = id,
        mainLabel = main,
        isIcon = isIcon,
        width = configuredWidth(keys, id, 1f),
        swipeUpLabel = swipeUp,
        swipeDownLabel = swipeDown,
        compact = compact,
    )
}

/**
 * 功能键键面信息：内置图标/标签，可被 keys.<id> 覆盖（tap.label / tap.value / width），
 * 取值规则对齐 Xime FunctionKeyCell 各内置组件。delete 内置"上滑清空"提示。
 */
private fun functionKeyInfo(id: String, keys: JSONObject, binding: JSONObject, compact: Boolean): PreviewKeyInfo {
    val tap = binding.optJSONObject("tap")
    val label = gestureField(tap, "label")
    val value = gestureField(tap, "value")
    var icon = PreviewKeyIcon.NONE
    var main = ""
    when (id) {
        "shift" -> icon = PreviewKeyIcon.SHIFT
        "delete" -> icon = PreviewKeyIcon.DELETE
        "mode_change" -> main = label ?: "?123"
        "enter" -> main = label ?: "换行"
        // 键面显示 label 优先（对齐 Xime CommaCell：text = label ?: value）
        "comma" -> main = label ?: value ?: "，"
        // earth 的 SwipeableKeyButton 恒带地球图标（icon 优先于文本，@ 前缀视为图标名）
        "earth" -> if (label != null && !label.startsWith("@")) main = label else icon = PreviewKeyIcon.EARTH
        "space" -> Unit // 渲染时显示方案名
        "symbol" -> icon = PreviewKeyIcon.SYMBOL
        "emoji" -> icon = PreviewKeyIcon.EMOJI
        "voice" -> icon = PreviewKeyIcon.VOICE
    }
    val swipeUp = binding.opt("swipe_up")
        ?.takeIf { gestureDisplay(it) != "bubble" }
        ?.let { gestureLabel(it) }?.takeIf { it.isNotEmpty() }
        ?: if (id == "delete" && !binding.has("swipe_up")) "清空" else null
    val swipeDown = binding.opt("swipe_down")
        ?.takeIf { gestureDisplay(it) != "bubble" }
        ?.let { gestureLabel(it) }?.takeIf { it.isNotEmpty() }
    return PreviewKeyInfo(
        id = id,
        mainLabel = main,
        isFunction = true,
        icon = icon,
        width = configuredWidth(keys, id, defaultFunctionKeyWidth(id)),
        swipeUpLabel = swipeUp,
        swipeDownLabel = swipeDown,
        compact = compact,
    )
}

/** 手势对象的 label/value 字段，支持字符串与数组（多行 label join("\n")）。 */
private fun gestureField(tap: JSONObject?, field: String): String? = when (val v = tap?.opt(field)) {
    is String -> v.trim().takeIf { it.isNotEmpty() }
    is JSONArray -> (0 until v.length())
        .mapNotNull { i -> v.opt(i)?.toString() }
        .joinToString("\n").trim().takeIf { it.isNotEmpty() }
    else -> null
}

private fun JSONObject.optColorsArray(name: String): List<Color> {
    val arr = optJSONArray(name) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i -> (arr.opt(i) as? Number)?.toLong()?.toColor() }
}

private fun gestureLabel(v: Any?): String? = when (v) {
    is String -> v
    is JSONObject -> sequenceOf("label", "value").mapNotNull { k -> gestureField(v, k) }.firstOrNull()
        ?.lineSequence()?.firstOrNull { it.isNotBlank() }
    else -> null
}

/**
 * 键面提示的完整文本（保留 "\n" 多行，如小鹤双拼的 "ue\nve"）：
 * compact 模式下 Xime 按 ≤12 字符多行显示，数据层不得提前截断。
 */
private fun gestureSurfaceText(v: Any?): String? = when (v) {
    is String -> v
    is JSONObject -> sequenceOf("label", "value").mapNotNull { k -> gestureField(v, k) }.firstOrNull()
    else -> null
}

/** 手势对象是否为 {use: 预设名} 引用（无 label/value，键面显示键名）。 */
private fun gestureDisplay(v: Any?): String? = (v as? JSONObject)?.optString("display", "")?.ifEmpty { null }

/** 6 位 RGB（0xRRGGBB）补不透明 alpha；8 位 ARGB（0xAARRGGBB）原样使用。 */
private fun Long.toColor(): Color {
    val argb = if (this <= 0xFFFFFFL) this or 0xFF000000L else this
    return Color(argb.toInt())
}

// ── 渲染 ──

/**
 * 高保真键盘预览：布局权重/配色/圆角/阴影对齐 Xime 的 KeyboardLayout + KeyButton。
 * 行数与行内容完全由 layout.rows 驱动（最多 5 行，含功能键行）——所有按键都可配置，
 * 无硬编码行；行内按 id 分派功能键/字母键。见 Xime QwertyRow + FunctionKeyCell。
 *
 * [onKeyClick] 传入时点击按键回调 keyId，用于手势编辑入口。
 */
@Composable
fun KeyboardPreview(
    preview: PreviewData,
    dark: Boolean,
    modifier: Modifier = Modifier,
    schemaName: String = "五笔拼音",
    onKeyClick: ((String) -> Unit)? = null,
) {
    val keyboardBg = preview.background
    Surface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    when {
                        keyboardBg.isImage -> Modifier.background(keyboardBg.color(dark))
                        keyboardBg.type == "gradient" && keyboardBg.colorList(dark).size >= 2 -> {
                            val (start, end) = gradientOffsets(keyboardBg.angle)
                            Modifier.background(
                                Brush.linearGradient(keyboardBg.colorList(dark), start, end)
                            )
                        }
                        else -> Modifier.background(keyboardBg.color(dark))
                    }
                )
                .then(
                    if (keyboardBg.isImage) {
                        val alpha = if (dark) keyboardBg.overlayDark else keyboardBg.overlayLight
                        Modifier.background(Color.Black.copy(alpha = alpha))
                    } else Modifier
                )
                // 对齐 Xime：键盘内容区 padding(start=4, end=4, bottom=8)
                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        ) {
            // ── 候选栏 ──
            CandidateStrip(
                primary = preview.primary,
                textColor = preview.candidateText,
                onKeyClick = onKeyClick,
            )

            // ── 按键行（等高）──
            // 间距模型对齐 Xime：spacing_x/spacing_y 是每个键四周的 padding，
            // 相邻键水平间隙 = spacing_x*2，行间垂直间隙 = spacing_y*2。
            // 阴影/裁剪/背景在 KeyCap/FunctionKeyCap 内按官方顺序绘制。

            val firstRowSize = preview.rows.firstOrNull()?.size ?: 10
            preview.rows.forEach { rowInfos ->
                // 9 键纯字母行（如 asdf 行）两端缩进、视觉居中；宽度取 9/首行键数比例，
                // 任意行宽下该行键宽都与首行一致（对齐 Xime KeyboardLayout 的 indent 逻辑）
                val rowWidth = if (rowInfos.size == 9 && rowInfos.none { it.isFunction }) {
                    (9f / firstRowSize).coerceIn(0.5f, 1f)
                } else 1f
                Row(
                    modifier = Modifier
                        .fillMaxWidth(rowWidth)
                        .weight(1f)
                        .align(Alignment.CenterHorizontally),
                ) {
                    rowInfos.forEach { info ->
                        // padding 只叠加一次：KeyCap 的 base 保持裸 Modifier，
                        // 间距+权重统一在 modifier 上（重复叠加会使列/行间距翻倍）
                        val keySize = Modifier
                            .padding(
                                horizontal = preview.spacingX.dp,
                                vertical = preview.spacingY.dp,
                            )
                            .weight(info.width)
                            .fillMaxHeight()
                        if (info.isFunction) {
                            FunctionKeyCap(preview, info, keySize, schemaName, onKeyClick)
                        } else {
                            KeyCap(Modifier, preview, info, keySize, dark) {
                                onKeyClick?.invoke(info.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 候选栏占位（真实输入法此处显示联想结果）。 */
@Composable
private fun CandidateStrip(primary: Color, textColor: Color, onKeyClick: ((String) -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(primary.copy(alpha = 0.16f))
                .clickable { onKeyClick?.invoke("__logo") },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(primary)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.9f)),
            )
        }
        Box(modifier = Modifier.weight(1f))
        Text("五笔", fontSize = 16.sp, color = textColor, modifier = Modifier.padding(horizontal = 10.dp))
        Text("拼音", fontSize = 16.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(horizontal = 10.dp))
        Text("输入法", fontSize = 16.sp, color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(horizontal = 10.dp))
        Box(modifier = Modifier.weight(0.6f))
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.55f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 功能键渲染：按 id 分派内置图标/文本（对齐 Xime FunctionKeyCell）。
 * 取色：comma/earth/space 用普通键底色+按键文字色（Xime SpaceKey/CommaCell/EarthCell），
 * 其余用特殊键底色，内容颜色按背景亮度自适应（Xime getSpecialKeyTextColorForBackground）。
 * 预览与布局编辑器共用。
 */
@Composable
fun FunctionKeyCap(
    preview: PreviewData,
    info: PreviewKeyInfo,
    modifier: Modifier,
    schemaName: String = "五笔拼音",
    onKeyClick: ((String) -> Unit)? = null,
) {
    val soft = info.id in SOFT_FUNCTION_KEYS
    val bg = if (soft) preview.keyBg else preview.specialKeyBg
    val contentColor = if (soft || bg.luminance() > 0.5f) preview.keyText else Color(0xFFE8EAED)
    FixedKey(
        preview = preview,
        modifier = modifier,
        bg = bg,
        contentColor = contentColor,
        onClick = { onKeyClick?.invoke(info.id) },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            when (info.icon) {
                PreviewKeyIcon.SHIFT ->
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Shift", tint = contentColor)
                PreviewKeyIcon.DELETE ->
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "删除", tint = contentColor)
                PreviewKeyIcon.EARTH ->
                    Icon(Icons.Outlined.Language, contentDescription = "中英切换", tint = contentColor)
                PreviewKeyIcon.SYMBOL ->
                    Icon(Icons.TwoTone.KeyboardAlt, contentDescription = "符号", tint = contentColor)
                PreviewKeyIcon.EMOJI ->
                    Icon(Icons.Filled.EmojiEmotions, contentDescription = "表情", tint = contentColor)
                PreviewKeyIcon.VOICE ->
                    Icon(Icons.Filled.Mic, contentDescription = "语音", tint = contentColor)
                PreviewKeyIcon.NONE -> if (info.id == "space") {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            "空格",
                            fontSize = 11.sp,
                            color = contentColor.copy(alpha = 0.45f),
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                        )
                        Text(
                            schemaName,
                            fontSize = 16.sp,
                            color = contentColor,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                } else {
                    Text(
                        text = info.mainLabel,
                        color = contentColor,
                        fontSize = if (info.mainLabel.length > 2) 14.sp else 16.sp,
                        fontWeight = if (info.mainLabel.length > 2) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            info.swipeUpLabel?.let {
                Text(
                    text = it.take(4),
                    color = contentColor.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-4).dp),
                )
            }
            info.swipeDownLabel?.let {
                Text(
                    text = it.take(4),
                    color = contentColor.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 4.dp),
                )
            }
        }
    }
}

/**
 * 键帽渲染，1:1 对齐 Xime SwipeableKeyButton 的视觉结构：
 * - standard：主字符居中（18sp，>2 字符 14sp/Medium），上滑提示居中锚点向上偏移 14dp，下滑向下 14dp
 * - compact：主字符左上（top=2, start=4），右上列放提示，下滑提示占右侧中部（中文 ×0.85 字号）
 * - 阴影为 drawBehind 实心圆角矩形下移 elevation（官方 crispShadowColor 方案），非模糊光晕
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyCap(
    base: Modifier,
    preview: PreviewData,
    info: PreviewKeyInfo,
    modifier: Modifier,
    dark: Boolean,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shadowModifier = if (preview.shadowEnabled) {
        val elevationPx = with(LocalDensity.current) { preview.elevation.dp.toPx() }
        val cornerPx = with(LocalDensity.current) { preview.cornerRadius.dp.toPx() }
        val shadowColor = crispShadowColor(preview.keyBg)
        Modifier.drawBehind {
            drawRoundRect(
                color = shadowColor,
                topLeft = Offset(0f, elevationPx),
                size = size,
                cornerRadius = CornerRadius(cornerPx),
            )
        }
    } else Modifier

    // enabled=false 时不消费点击（如布局模式卡片里的预览键帽，让点击穿透到外层卡片）
    val clickModifier = when {
        onLongClick != null -> Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        enabled -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }

    Box(
        modifier = base
            .then(modifier)
            .then(shadowModifier)
            .clip(RoundedCornerShape(preview.cornerRadius.dp))
            .background(preview.keyBg)
            .then(clickModifier),
        contentAlignment = if (info.compact) Alignment.TopStart else Alignment.Center,
    ) {
        if (info.compact) {
            // ── compact：主字符左上，提示靠右列 ──
            Text(
                text = info.mainLabel,
                color = preview.keyText,
                fontSize = if (info.mainLabel.length > 2) 13.sp else 16.sp,
                fontWeight = if (info.mainLabel.length > 2) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Start,
                maxLines = 1,
                lineHeight = 1.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 2.dp, start = 4.dp),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .padding(top = 4.dp, end = 4.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.End,
            ) {
                info.swipeUpLabel?.let {
                    Text(
                        text = it.take(2),
                        color = preview.keyText.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        lineHeight = 1.sp,
                    )
                }
                info.swipeDownLabel?.let {
                    val hasChinese = it.any { c -> c in '\u4e00'..'\u9fff' || c in '\u3400'..'\u4dbf' }
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomEnd) {
                        Text(
                            text = it.take(12),
                            color = preview.keyText.copy(alpha = 0.7f),
                            fontSize = if (hasChinese) 9.sp * 0.85f else 9.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Right,
                            maxLines = 3,
                            lineHeight = if (hasChinese) 9.sp * 0.85f else 9.sp,
                        )
                    }
                }
            }
        } else {
            // ── standard：主字符居中，提示以中心为锚 ±14dp 偏移 ──
            Text(
                text = info.mainLabel,
                color = preview.keyText,
                fontSize = if (info.mainLabel.length > 2) 14.sp else 18.sp,
                fontWeight = if (info.mainLabel.length > 2) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            info.swipeUpLabel?.let {
                Text(
                    text = it.take(4),
                    color = preview.keyText.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.offset(y = (-14).dp),
                )
            }
            info.swipeDownLabel?.let {
                Text(
                    text = it.take(4),
                    color = preview.keyText.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.offset(y = 14.dp),
                )
            }
        }
    }
}

/** 官方 crispShadowColor：低色差浅底用黑 10%，深底用白 12%，彩色底加深 5%。 */
private fun crispShadowColor(backgroundColor: Color): Color {
    val r = backgroundColor.red
    val g = backgroundColor.green
    val b = backgroundColor.blue
    val maxChroma = maxOf(r, g, b) - minOf(r, g, b)
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    return if (maxChroma > 0.05f) {
        Color(r * 0.95f, g * 0.95f, b * 0.95f, backgroundColor.alpha)
    } else if (luminance > 0.5f) {
        Color.Black.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }
}

@Composable
private fun FixedKey(
    preview: PreviewData,
    modifier: Modifier,
    bg: Color,
    contentColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(preview.cornerRadius.dp)
    val shadowModifier = if (preview.shadowEnabled) {
        val elevationPx = with(LocalDensity.current) { preview.elevation.dp.toPx() }
        val cornerPx = with(LocalDensity.current) { preview.cornerRadius.dp.toPx() }
        val shadowColor = crispShadowColor(bg)
        Modifier.drawBehind {
            drawRoundRect(
                color = shadowColor,
                topLeft = Offset(0f, elevationPx),
                size = size,
                cornerRadius = CornerRadius(cornerPx),
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .then(shadowModifier)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun gradientOffsets(angleDegrees: Float): Pair<Offset, Offset> {
    val rad = Math.toRadians(angleDegrees.toDouble())
    val dx = Math.cos(rad).toFloat()
    val dy = Math.sin(rad).toFloat()
    return Offset(0.5f - dx / 2, 0.5f - dy / 2) to Offset(0.5f + dx / 2, 0.5f + dy / 2)
}

private fun PreviewBackground.colorList(isDark: Boolean): List<Color> = if (isDark) dark else light
