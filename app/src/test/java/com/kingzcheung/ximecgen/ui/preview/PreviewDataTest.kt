package com.kingzcheung.ximecgen.ui.preview

import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** buildPreviewData 的取色回退链与背景三型解析。 */
class PreviewDataTest {

    private fun build(yamlishJson: String, dark: Boolean = false) =
        buildPreviewData(JSONObject(yamlishJson), dark)

    @Test
    fun `key colors follow scheme then keyboard colors then built-in defaults`() {
        // 只有哪些都没有时才用内置默认
        val empty = build("{}")
        assertEquals(0xFFFFFFFF.toInt(), empty.keyBg.toArgb())
        assertEquals(0xFF202124.toInt(), empty.keyText.toArgb())
        assertEquals(0xFFE3E4E8.toInt(), empty.background.color(false).toArgb())
        assertEquals(0xFF202125.toInt(), empty.background.color(true).toArgb())

        // keyboard.colors（全局后备）
        val keyboardOnly = build("""{"keyboard":{"colors":{"key_bg_color":4473925,"key_text_color":11119017}}}""")
        assertEquals(0xFF444445.toInt(), keyboardOnly.keyBg.toArgb())
        assertEquals(0xFFA9A9A9.toInt(), keyboardOnly.keyText.toArgb())

        // color_schemes 方案优先于 keyboard.colors
        val withScheme = build(
            """
            {"color_schemes":{"ocean":{"key_bg_color":16711680}},
             "style":{"color_scheme":"ocean"},
             "keyboard":{"colors":{"key_bg_color":255}}}
            """.trimIndent(),
        )
        assertEquals(0xFFFF0000.toInt(), withScheme.keyBg.toArgb())
    }

    @Test
    fun `dark mode reads dark variants with light fallback chain`() {
        val config = """
            {"color_schemes":{"t":{"key_bg_color":16711680,"key_bg_color_dark":255,
                                   "key_text_color":255}},
             "style":{"color_scheme":"t"}}
        """.trimIndent()
        val dark = build(config, dark = true)
        // key_bg_color_dark 显式存在
        assertEquals(0xFF0000FF.toInt(), dark.keyBg.toArgb())
        // key_text_color_dark 缺失 → 不回退浅色（对齐 Xime：dark 只读 _dark 键），用内置默认 E8EAED
        assertEquals(0xFFE8EAED.toInt(), dark.keyText.toArgb())

        // 补上 _dark 值后生效
        val full = build(
            """
            {"color_schemes":{"t":{"key_bg_color":16711680,"key_bg_color_dark":255,
                                   "key_text_color":255,"key_text_color_dark":65280}},
             "style":{"color_scheme":"t"}}
            """.trimIndent(),
            dark = true,
        )
        assertEquals(0xFF00FF00.toInt(), full.keyText.toArgb())
    }

    @Test
    fun `special key background explicit config wins otherwise theme-derived`() {
        val explicit = build(
            """{"color_schemes":{"t":{"special_key_bg_color":15658734}},
                "style":{"color_scheme":"t"}}""",
        )
        assertEquals(0xFFEEEEEE.toInt(), explicit.specialKeyBg.toArgb())

        // 未配置：浅色 = 主题色 28% 叠加键盘背景；深色 = 主题色本体
        val derived = build("""{"color_schemes":{"t":{"primary_color":9401314}},"style":{"color_scheme":"t"}}""")
        // 浅色派生底 = 主题色 28% 叠加键盘背景：每个通道都介于主题色与背景色之间
        val bg = derived.background.color(false)
        val primary = 0x8F73E2
        val derivedRgb = derived.specialKeyBg.toArgb() and 0x00FFFFFF
        fun channel(argb: Int, shift: Int) = (argb shr shift) and 0xFF
        for (shift in intArrayOf(16, 8, 0)) {
            val p = channel(primary, shift)
            val b = channel(bg.toArgb(), shift)
            val v = channel(derivedRgb, shift)
            assertTrue(
                "派生通道 $v 应介于主题色 $p 与背景 $b 之间",
                v >= minOf(p, b) && v <= maxOf(p, b),
            )
        }
        val darkDerived = build(
            """{"color_schemes":{"t":{"primary_color":9401314}},"style":{"color_scheme":"t"}}""",
            dark = true,
        )
        assertEquals(0xFF8F73E2.toInt(), darkDerived.specialKeyBg.toArgb())
    }

    @Test
    fun `keyboard background solid gradient and image variants`() {
        val solid = build(
            """{"color_schemes":{"t":{"keyboard_background":{"type":"solid","color":15658734,"color_dark":197379}}},
                "style":{"color_scheme":"t"}}""",
        )
        assertEquals("solid", solid.background.type)
        assertEquals(0xFFEEEEEE.toInt(), solid.background.color(false).toArgb())
        assertEquals(0xFF030303.toInt(), solid.background.color(true).toArgb())

        val gradient = build(
            """{"color_schemes":{"t":{"keyboard_background":{"type":"gradient",
                "colors":[16711680,255],"angle":45}}},
                "style":{"color_scheme":"t"}}""",
        )
        assertEquals("gradient", gradient.background.type)
        assertEquals(45f, gradient.background.angle)
        assertEquals(2, gradient.background.light.size)
        // 深色缺失时回退浅色色标
        assertEquals(2, gradient.background.dark.size)

        val image = build(
            """{"color_schemes":{"t":{"keyboard_background":{"type":"image","overlay_alpha":0.5}}},
                "style":{"color_scheme":"t"}}""",
        )
        assertTrue(image.background.isImage)
        assertEquals(0.5f, image.background.overlayLight)
    }

    @Test
    fun `spacing and corner radius read from key config with clamping`() {
        val plain = build("{}")
        assertEquals(8, plain.cornerRadius)
        assertEquals(2f, plain.spacingX)
        assertEquals(4.25f, plain.spacingY)

        val custom = build("""{"keyboard":{"key":{"corner_radius":24,"spacing_x":6,"spacing_y":8}}}""")
        assertEquals(24, custom.cornerRadius)
        assertEquals(6f, custom.spacingX)
        assertEquals(8f, custom.spacingY)

        // 越界钳制（与渲染一致，防止畸形配置破坏布局）
        val extreme = build("""{"keyboard":{"key":{"corner_radius":999,"spacing_x":100,"spacing_y":-5}}}""")
        assertEquals(48, extreme.cornerRadius)
        assertEquals(24f, extreme.spacingX)
        assertEquals(0f, extreme.spacingY)
    }

    @Test
    fun `shadow config read with defaults`() {
        val plain = build("{}")
        assertTrue(plain.shadowEnabled)
        assertEquals(0.5f, plain.elevation)

        val off = build("""{"keyboard":{"shadow":{"enabled":false,"elevation":2.5}}}""")
        assertEquals(false, off.shadowEnabled)
        assertEquals(2.5f, off.elevation)
    }
}
