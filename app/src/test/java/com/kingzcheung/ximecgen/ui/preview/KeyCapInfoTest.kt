package com.kingzcheung.ximecgen.ui.preview

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 键面信息解析：功能键内置值、keys.&lt;id&gt; 覆盖、手势 label/value 优先级与显示策略。 */
class KeyCapInfoTest {

    private fun info(keysJson: String, id: String, compact: Boolean = false) =
        keyCapInfo(JSONObject(keysJson), id, compact)

    // ── 功能键 ──

    @Test
    fun `function keys get built-in width defaults`() {
        val keys = "{}"
        assertEquals(1.4f, info(keys, "shift").width)
        assertEquals(1.4f, info(keys, "delete").width)
        assertEquals(1.2f, info(keys, "mode_change").width)
        assertEquals(1.2f, info(keys, "enter").width)
        assertEquals(0.8f, info(keys, "earth").width)
        assertEquals(0.8f, info(keys, "comma").width)
        assertEquals(3f, info(keys, "space").width)
        assertEquals(1f, info(keys, "voice").width)
    }

    @Test
    fun `explicit width in keys config overrides default`() {
        val keys = """{"shift":{"width":2.0},"q":{"width":1.5},"qw":{"width":2.5}}"""
        assertEquals(2f, info(keys, "shift").width)
        assertEquals(1.5f, info(keys, "q").width)
        assertEquals(2.5f, info(keys, "qw").width)
        // 非法 width（≤0）回退默认
        assertEquals(1.4f, info("""{"delete":{"width":0}}""", "delete").width)
    }

    @Test
    fun `mode_change and enter use tap label with fallbacks`() {
        assertEquals("?123", info("{}", "mode_change").mainLabel)
        assertEquals("数字", info("""{"mode_change":{"tap":{"label":"数字"}}}""", "mode_change").mainLabel)
        assertEquals("换行", info("{}", "enter").mainLabel)
        assertEquals("确定", info("""{"enter":{"tap":{"label":"确定"}}}""", "enter").mainLabel)
    }

    @Test
    fun `comma displays label first then value with default`() {
        assertEquals("，", info("{}", "comma").mainLabel)
        assertEquals(
            "键面显示 label 优先（对齐 Xime CommaCell）",
            "，",
            info("""{"comma":{"tap":{"label":"，","value":","}}}""", "comma").mainLabel,
        )
        assertEquals(",", info("""{"comma":{"tap":{"value":","}}}""", "comma").mainLabel)
    }

    @Test
    fun `earth shows globe icon by default and label text when configured`() {
        val default = info("{}", "earth")
        assertTrue(default.isFunction)
        assertEquals(PreviewKeyIcon.EARTH, default.icon)

        // 官方内置配置：label 为 @language 图标约定 → 仍渲染地球图标
        val atIcon = info("""{"earth":{"tap":{"label":"@language","action":"toggle_ascii"}}}""", "earth")
        assertEquals(PreviewKeyIcon.EARTH, atIcon.icon)

        // 普通文本 label → 显示文字
        val labeled = info("""{"earth":{"tap":{"label":"中"}}}""", "earth")
        assertEquals(PreviewKeyIcon.NONE, labeled.icon)
        assertEquals("中", labeled.mainLabel)
    }

    @Test
    fun `delete carries default clear hint unless configured`() {
        val default = info("{}", "delete")
        assertEquals("清空", default.swipeUpLabel)

        val overridden = info("""{"delete":{"swipe_up":{"label":"删词","action":"clear_all"}}}""", "delete")
        assertEquals("删词", overridden.swipeUpLabel)

        // bubble 策略的下滑提示不印在键面
        val bubbled = info("""{"delete":{"swipe_down":{"label":"撤回","display":"bubble"}}}""", "delete")
        assertNull(bubbled.swipeDownLabel)
    }

    @Test
    fun `letter key label from tap with uppercase normalization`() {
        assertEquals("Q", info("{}", "q").mainLabel)
        assertEquals("Q", info("""{"q":{"tap":"q"}}""", "q").mainLabel)
        assertEquals("。",
            info("""{"q":{"tap":{"label":"。","value":"."}}}""", "q").mainLabel)
        // 含字母的显示统一大写（对齐 Xime getKeyDisplayLabel）
        assertEquals("QW", info("""{"qw":{"tap":{"value":"qw"}}}""", "qw").mainLabel)
    }

    @Test
    fun `multi line swipe labels are preserved for compact rendering`() {
        val info = info("""{"t":{"tap":"t","swipe_down":{"label":"ue\nve","action":"none","display":"key"}}}""", "t")
        assertEquals("双拼韵母多行 label 不得提前截断", "ue\nve", info.swipeDownLabel)

        val plain = info("""{"r":{"tap":"r","swipe_up":"4"}}""", "r")
        assertEquals("4", plain.swipeUpLabel)
    }

    @Test
    fun `icon prefix and bubble display are handled`() {
        val icon = info("""{"q":{"tap":{"label":"@mic"}}}""", "q")
        assertTrue(icon.isIcon)
        // 含字母的显示统一大写（对齐 Xime getKeyDisplayLabel，@ 前缀剥离后同样大写）
        assertEquals("MIC", icon.mainLabel)

        val bubbled = info("""{"q":{"swipe_up":{"label":"1","display":"bubble"}}}""", "q")
        assertNull(bubbled.swipeUpLabel)
    }

    @Test
    fun `compact flag propagates and function flag distinguishes keys`() {
        val compact = info("{}", "q", compact = true)
        assertTrue(compact.compact)
        assertFalse(info("{}", "q").compact)

        val letter = info("{}", "q")
        assertFalse(letter.isFunction)
        assertTrue(info("{}", "space").isFunction)
    }
}
