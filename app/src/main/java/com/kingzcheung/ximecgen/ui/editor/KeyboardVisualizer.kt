package com.kingzcheung.ximecgen.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private val KEYBOARD_ROW1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
private val KEYBOARD_ROW2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
private val KEYBOARD_ROW3 = listOf("z", "x", "c", "v", "b", "n", "m")

private val LocalVisualKeyPadding = staticCompositionLocalOf {
    PaddingValues(horizontal = 2.dp, vertical = 4.25.dp)
}

data class KeyboardVisualConfig(
    val keyboardBgColor: Color = Color(0xFFE3E4E8),
    val keyBgColor: Color = Color(0xFFFFFFFF),
    val specialKeyBgColor: Color = Color(0xFFD3D4D8),
    val keyTextColor: Color = Color(0xFF202124),
    val cornerRadius: Dp = 8.dp,
    val shadowEnabled: Boolean = true,
    val shadowElevation: Dp = 1.dp,
    val isDark: Boolean = false,
    val buttonLayout: String = "standard",
)

fun configToVisualConfig(json: JSONObject, isDark: Boolean = false): KeyboardVisualConfig {
    val keyboard = json.optJSONObject("keyboard")
    val colors = keyboard?.optJSONObject("colors")
    val key = keyboard?.optJSONObject("key")
    val shadow = keyboard?.optJSONObject("shadow")
    val qwerty = keyboard?.optJSONObject("qwerty")
    val style = json.optJSONObject("style")
    val colorSchemes = json.optJSONObject("color_schemes")

    // Determine active theme primary color for special keys
    val activeScheme = style?.optString("color_scheme", "")
    val themeColor: Long? = if (!activeScheme.isNullOrEmpty() && colorSchemes != null) {
        val scheme = colorSchemes.optJSONObject(activeScheme)
        scheme?.optString("primary_color")?.let { hex ->
            try {
                val clean = hex.trimStart('#', ' ').removePrefix("0x").removePrefix("0X")
                clean.toLong(16)
            } catch (_: Exception) { null }
        }
    } else null

    fun parseHex(hex: String?): Color? {
        if (hex == null) return null
        val clean = hex.trimStart('#', ' ').removePrefix("0x").removePrefix("0X")
        return try { Color(0xFF000000 or clean.toLong(16)) } catch (_: Exception) { null }
    }

    // If theme color is set, use it as special key background; otherwise fall back to config
    val specialBg = if (themeColor != null) Color(0xFF000000 or themeColor)
        else if (isDark)
            parseHex(colors?.optString("special_key_bg_color_dark")) ?: Color(0xFF3A3A3A)
        else
            parseHex(colors?.optString("special_key_bg_color")) ?: Color(0xFFD3D4D8)

    return KeyboardVisualConfig(
        keyboardBgColor = if (isDark)
            parseHex(colors?.optString("keyboard_bg_color_dark")) ?: Color(0xFF202020)
        else
            parseHex(colors?.optString("keyboard_bg_color")) ?: Color(0xFFE3E4E8),
        keyBgColor = if (isDark)
            parseHex(colors?.optString("key_bg_color_dark")) ?: Color(0xFF4A4A4A)
        else
            parseHex(colors?.optString("key_bg_color")) ?: Color(0xFFFFFFFF),
        specialKeyBgColor = specialBg,
        keyTextColor = if (isDark)
            parseHex(colors?.optString("key_text_color_dark")) ?: Color(0xFFE8EAED)
        else
            parseHex(colors?.optString("key_text_color")) ?: Color(0xFF202124),
        cornerRadius = (key?.optInt("corner_radius") ?: 8).dp,
        shadowEnabled = shadow?.optBoolean("enabled", true) ?: true,
        shadowElevation = (shadow?.optInt("elevation") ?: 1).dp,
        isDark = isDark,
        buttonLayout = qwerty?.optString("button_layout", "standard") ?: "standard",
    )
}

@Composable
fun KeyboardVisualizer(
    config: KeyboardVisualConfig,
    qwertyKeys: JSONObject? = null,
    onKeyClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isCompact = config.buttonLayout == "compact"

    CompositionLocalProvider(LocalVisualKeyPadding provides PaddingValues(horizontal = 2.dp, vertical = 4.25.dp)) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .background(config.keyboardBgColor, RoundedCornerShape(12.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // Row 1: q-p
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            KEYBOARD_ROW1.forEach { key ->
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    LetterKeyContent(key, config, qwertyKeys?.optJSONObject(key))
                                }
                            }
                        }
                    }

                    // Row 2: a-l (indented)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            KEYBOARD_ROW2.forEach { key ->
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    LetterKeyContent(key, config, qwertyKeys?.optJSONObject(key))
                                }
                            }
                        }
                    }

                    // Row 3: shift + z-m + backspace
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1.6f).fillMaxHeight()) {
                                ShiftKeyContent(config)
                            }
                            KEYBOARD_ROW3.forEach { key ->
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    LetterKeyContent(key, config, qwertyKeys?.optJSONObject(key))
                                }
                            }
                            Box(modifier = Modifier.weight(1.6f).fillMaxHeight()) {
                                BackspaceKeyContent(config)
                            }
                        }
                    }

                    // Row 4: control row (shorter than letter rows)
                    Box(modifier = Modifier.weight(0.8f).fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
                                SpecialKeyContent("?123", config, bold = false)
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                val cd = qwertyKeys?.optJSONObject("'")
                                val cl = cd?.optJSONObject("tap")?.optString("label")
                                    ?: cd?.optJSONObject("tap")?.optString("value")
                                    ?: (if (!isCompact) "，" else ",")
                                NormalKeyContent(cl, config)
                            }
                            Box(modifier = Modifier.weight(3.6f).fillMaxHeight()) {
                                SpaceKeyContent(config)
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                val sld = qwertyKeys?.optJSONObject("shift_l")
                                val sl = sld?.optJSONObject("tap")?.optString("label")
                                    ?: if (!config.isDark) "英" else "中"
                                NormalKeyContent(sl, config)
                            }
                            Box(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
                                SpecialKeyContent("↵", config, bold = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterKeyContent(
    key: String,
    config: KeyboardVisualConfig,
    keyData: JSONObject?,
) {
    val shape = RoundedCornerShape(config.cornerRadius)
    val tapData = keyData?.optJSONObject("tap")
    val swipeUpData = keyData?.optJSONObject("swipe_up")
    val swipeDownData = keyData?.optJSONObject("swipe_down")
    val longPressData = keyData?.optJSONObject("long_press")
    val hasLongPress = longPressData != null && longPressData.optJSONArray("values")?.length() ?: 0 > 0
    val tapValue = tapData?.optString("value") ?: tapData?.optString("label") ?: key.uppercase()
    val swipeUpLabel = swipeUpData?.optString("label") ?: swipeUpData?.optString("value")
    val swipeDownLabel = swipeDownData?.optString("label") ?: swipeDownData?.optString("value")

    val keyPadding = LocalVisualKeyPadding.current
    val isCompact = config.buttonLayout == "compact"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(keyPadding)
            .then(if (config.shadowEnabled) Modifier.shadow(config.shadowElevation, shape) else Modifier)
            .clip(shape)
            .background(config.keyBgColor),
        contentAlignment = if (isCompact) Alignment.TopStart else Alignment.Center,
    ) {
        if (isCompact) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = tapValue,
                    color = config.keyTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 2.dp, start = 4.dp),
                )
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight()
                        .padding(top = 4.dp, end = 4.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (swipeUpLabel != null) {
                        Text(swipeUpLabel.take(2), color = config.keyTextColor.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    if (swipeDownLabel != null) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                            Text(swipeDownLabel.take(4), color = config.keyTextColor.copy(alpha = 0.5f), fontSize = 9.sp, maxLines = 3)
                        }
                    }
                }
            }
            if (hasLongPress) {
                Box(Modifier.align(Alignment.TopEnd).size(4.dp).clip(CircleShape).background(config.keyTextColor.copy(alpha = 0.35f)).padding(2.dp))
            }
        } else {
            Text(
                text = tapValue,
                color = config.keyTextColor,
                fontSize = if (tapValue.length > 2) 14.sp else 18.sp,
                fontWeight = if (tapValue.length > 2) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (swipeUpLabel != null) {
                Text(
                    text = swipeUpLabel.take(4),
                    color = config.keyTextColor.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.offset(y = (-14).dp),
                )
            }
            if (hasLongPress) {
                Box(Modifier.align(Alignment.BottomEnd).size(4.dp).clip(CircleShape).background(config.keyTextColor.copy(alpha = 0.35f)).padding(2.dp))
            }
        }
    }
}

@Composable
private fun ShiftKeyContent(config: KeyboardVisualConfig) {
    val shape = RoundedCornerShape(config.cornerRadius)
    val keyPadding = LocalVisualKeyPadding.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(keyPadding)
            .then(if (config.shadowEnabled) Modifier.shadow(config.shadowElevation, shape) else Modifier)
            .clip(shape)
            .background(config.specialKeyBgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text("⇧", color = config.keyTextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BackspaceKeyContent(config: KeyboardVisualConfig) {
    val shape = RoundedCornerShape(config.cornerRadius)
    val keyPadding = LocalVisualKeyPadding.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(keyPadding)
            .then(if (config.shadowEnabled) Modifier.shadow(config.shadowElevation, shape) else Modifier)
            .clip(shape)
            .background(config.specialKeyBgColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null, tint = config.keyTextColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SpecialKeyContent(
    label: String,
    config: KeyboardVisualConfig,
    bold: Boolean = false,
) {
    val shape = RoundedCornerShape(config.cornerRadius)
    val keyPadding = LocalVisualKeyPadding.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(keyPadding)
            .then(if (config.shadowEnabled) Modifier.shadow(config.shadowElevation, shape) else Modifier)
            .clip(shape)
            .background(config.specialKeyBgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = config.keyTextColor, fontSize = if (bold) 15.sp else 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun NormalKeyContent(
    label: String,
    config: KeyboardVisualConfig,
) {
    val shape = RoundedCornerShape(config.cornerRadius)
    val keyPadding = LocalVisualKeyPadding.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(keyPadding)
            .then(if (config.shadowEnabled) Modifier.shadow(config.shadowElevation, shape) else Modifier)
            .clip(shape)
            .background(config.keyBgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = config.keyTextColor, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun SpaceKeyContent(config: KeyboardVisualConfig) {
    val shape = RoundedCornerShape(config.cornerRadius)
    val keyPadding = LocalVisualKeyPadding.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(keyPadding)
            .then(if (config.shadowEnabled) Modifier.shadow(config.shadowElevation, shape) else Modifier)
            .clip(shape)
            .background(config.keyBgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text("空格", color = config.keyTextColor.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}
