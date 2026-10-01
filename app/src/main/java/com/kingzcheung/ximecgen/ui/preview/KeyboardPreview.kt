package com.kingzcheung.ximecgen.ui.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
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

data class PreviewKeyInfo(
    val mainLabel: String,
    val isIcon: Boolean = false,
    val swipeUpLabel: String? = null,
    val swipeDownLabel: String? = null,
    val compact: Boolean = false,
)

data class PreviewData(
    val rows: List<List<PreviewKeyInfo>> = emptyList(),
    val commaLabel: String = "，",
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

private val DEFAULT_KEYS = listOf("q","w","e","r","t","y","u","i","o","p").let { row0 ->
    listOf(row0, listOf("a","s","d","f","g","h","j","k","l"), listOf("z","x","c","v","b","n","m"))
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
    val rows = qwerty?.optJSONObject("layout")?.optJSONArray("rows")
        ?.let { arr -> (0 until arr.length()).take(3).map { arr.optJSONArray(it) } }
        ?.map { row -> (0 until row.length()).map { row.optString(it) } }
        ?: DEFAULT_KEYS

    val keys = qwerty?.optJSONObject("keys") ?: JSONObject()
    val compact = qwerty?.optString("button_layout", "standard") == "compact"

    fun keyInfo(id: String) = keyCapInfo(keys, id, compact)

    val key = keyboard?.optJSONObject("key") ?: JSONObject()
    return PreviewData(
        rows = rows.map { r -> r.map { keyInfo(it) } },
        commaLabel = keys.optJSONObject("'")?.let { gestureLabel(it.opt("tap")) } ?: "，",
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
 * - tap 取 label/value；@ 前缀为图标；单字母大写显示（对齐 Xime）
 * - display 为 "bubble" 的上滑/下滑手势是按键时弹出的气泡，不印在键面上
 */
fun keyCapInfo(keys: JSONObject, id: String, compact: Boolean = false): PreviewKeyInfo {
    val binding = keys.optJSONObject(id) ?: JSONObject()
    val tap = binding.opt("tap")
    var main = gestureLabel(tap) ?: id
    var isIcon = false
    if (main.startsWith("@")) {
        isIcon = true
        main = main.removePrefix("@")
    }
    if (main.length == 1 && main[0].isLetter()) {
        main = main.uppercase()
    }
    val swipeUp = binding.opt("swipe_up")
        ?.takeIf { gestureDisplay(it) != "bubble" }
        ?.let { gestureLabel(it) }?.takeIf { it.isNotEmpty() }
    val swipeDown = binding.opt("swipe_down")
        ?.takeIf { gestureDisplay(it) != "bubble" }
        ?.let { gestureLabel(it) }?.takeIf { it.isNotEmpty() }
    return PreviewKeyInfo(main, isIcon, swipeUp, swipeDown, compact)
}

private fun JSONObject.optColorsArray(name: String): List<Color> {
    val arr = optJSONArray(name) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i -> (arr.opt(i) as? Number)?.toLong()?.toColor() }
}

private fun gestureLabel(v: Any?): String? = when (v) {
    is String -> v
    is JSONObject -> sequenceOf("label", "value").mapNotNull { k ->
        when (val lv = v.opt(k)) {
            // label 支持数组（多行显示），与 Xime 解析一致：join("\n") 后取首行作键帽提示
            is org.json.JSONArray -> (0 until lv.length())
                .mapNotNull { lv.opt(it)?.toString() }
                .joinToString("\n").ifEmpty { null }
            else -> lv?.toString()?.ifEmpty { null }
        }
    }.firstOrNull()?.lineSequence()?.firstOrNull { it.isNotBlank() }
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
 * [onKeyClick] 传入时点击按键回调 keyId（第 4 行固定键回传固定 id），用于手势编辑入口。
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

            // ── 4 行按键（等高）──
            // 间距模型对齐 Xime：spacing_x/spacing_y 是每个键四周的 padding，
            // 相邻键水平间隙 = spacing_x*2，行间垂直间隙 = spacing_y*2。
            // 阴影/裁剪/背景在 KeyCap 内按官方顺序绘制。
            val keyModifierBase = Modifier.padding(
                horizontal = preview.spacingX.dp,
                vertical = preview.spacingY.dp,
            )

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                preview.rows.getOrElse(0) { emptyList() }.forEachIndexed { i, info ->
                    KeyCap(keyModifierBase, preview, info, Modifier.weight(1f).fillMaxHeight(), dark) {
                        onKeyClick?.invoke(defaultRow0Keys.getOrElse(i) { "?" })
                    }
                }
            }
            // 第二行（9 键）：整行左右各缩进 16dp，对齐 Xime KeyboardLayout
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
            ) {
                preview.rows.getOrElse(1) { emptyList() }.forEachIndexed { i, info ->
                    KeyCap(keyModifierBase, preview, info, Modifier.weight(1f).fillMaxHeight(), dark) {
                        onKeyClick?.invoke(defaultRow1Keys.getOrElse(i) { "?" })
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                FixedKey(
                    preview = preview,
                    modifier = keyModifierBase.weight(1.4f).fillMaxHeight(),
                    bg = preview.specialKeyBg, contentColor = preview.keyText,
                    onClick = { onKeyClick?.invoke("shift_l") },
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Shift", tint = preview.keyText)
                }
                Row(
                    modifier = Modifier.weight(7.2f).fillMaxHeight(),
                ) {
                    preview.rows.getOrElse(2) { emptyList() }.forEachIndexed { i, info ->
                        KeyCap(keyModifierBase, preview, info, Modifier.weight(1f).fillMaxHeight(), dark) {
                            onKeyClick?.invoke(defaultRow2Keys.getOrElse(i) { "?" })
                        }
                    }
                }
                // 删除键：浅色=淡紫底+主题色图标；深色=主题色底+白图标（对齐官方）
                FixedKey(
                    preview = preview,
                    modifier = keyModifierBase.weight(1.4f).fillMaxHeight(),
                    bg = if (dark) preview.primary else preview.specialKeyBg,
                    contentColor = if (dark) Color.White else preview.primary,
                    onClick = { onKeyClick?.invoke("delete") },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "删除",
                        tint = if (dark) Color.White else preview.primary,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                FixedKey(
                    preview = preview,
                    modifier = keyModifierBase.weight(1.2f).fillMaxHeight(),
                    bg = preview.specialKeyBg, contentColor = preview.keyText,
                    onClick = { onKeyClick?.invoke("?123") },
                ) { Text("?123", fontSize = 16.sp, color = preview.keyText) }
                FixedKey(
                    preview = preview,
                    modifier = keyModifierBase.weight(0.8f).fillMaxHeight(),
                    bg = preview.keyBg, contentColor = preview.keyText,
                    onClick = { onKeyClick?.invoke("'") },
                ) { Text(preview.commaLabel, fontSize = 18.sp, color = preview.keyText) }
                FixedKey(
                    preview = preview,
                    modifier = keyModifierBase.weight(3.2f).fillMaxHeight(),
                    bg = preview.keyBg, contentColor = preview.keyText,
                    onClick = { onKeyClick?.invoke("space") },
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            "空格",
                            fontSize = 11.sp,
                            color = preview.keyText.copy(alpha = 0.45f),
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                        )
                        Text(
                            schemaName,
                            fontSize = 16.sp,
                            color = preview.keyText,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                FixedKey(
                    preview = preview,
                    modifier = keyModifierBase.weight(0.8f).fillMaxHeight(),
                    bg = preview.keyBg, contentColor = preview.keyText,
                    onClick = { onKeyClick?.invoke("earth") },
                ) {
                    Icon(Icons.Outlined.Language, contentDescription = "中英切换", tint = preview.keyText)
                }
                FixedKey(
                    preview = preview,
                    modifier = keyModifierBase.weight(1.2f).fillMaxHeight(),
                    bg = preview.specialKeyBg, contentColor = preview.keyText,
                    onClick = { onKeyClick?.invoke("enter") },
                ) { Text("换行", fontSize = 16.sp, color = preview.keyText) }
            }
        }
    }
}

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
        // 占位候选词（真实输入法此处显示联想结果）
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

    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else Modifier.clickable(onClick = onClick)

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

private val defaultRow0Keys = listOf("q","w","e","r","t","y","u","i","o","p")
private val defaultRow1Keys = listOf("a","s","d","f","g","h","j","k","l")
private val defaultRow2Keys = listOf("z","x","c","v","b","n","m")

private fun PreviewBackground.colorList(isDark: Boolean): List<Color> = if (isDark) dark else light
