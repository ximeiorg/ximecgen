package com.kingzcheung.ximecgen.ui.preview

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** layout.rows 解析与规范化：合并键、标量行、无 rows 兜底、行数上限。 */
class LayoutRowsTest {

    private fun rows(sectionJson: String?): List<List<String>> =
        layoutRowIds(sectionJson?.let { JSONObject(it) })

    @Test
    fun `explicit rows parsed as id lists`() {
        val rows = rows(
            """
            {"layout":{"rows":[
                ["q","w","e"],
                ["a","s","d"],
                ["shift","z","delete"],
                ["mode_change","comma","space","earth","enter"]
            ]}}
            """.trimIndent(),
        )
        assertEquals(4, rows.size)
        assertEquals(listOf("q", "w", "e"), rows[0])
        assertEquals(listOf("shift", "z", "delete"), rows[2])
        assertEquals(listOf("mode_change", "comma", "space", "earth", "enter"), rows[3])
    }

    @Test
    fun `missing rows fall back to built-in xime yaml layout including function keys`() {
        // 无 layout.rows：兜底行必须带 shift/delete（Xime 回退到内置 xime.yaml 的行）
        val rows = rows("""{"button_layout":"compact","keys":{}}""")
        assertEquals(4, rows.size)
        assertEquals(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), rows[0])
        assertEquals(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), rows[1])
        assertEquals(listOf("shift", "z", "x", "c", "v", "b", "n", "m", "delete"), rows[2])
        assertEquals(listOf("mode_change", "comma", "space", "earth", "enter"), rows[3])

        // 整个 qwerty 段缺失也一样
        val noSection = rows(null)
        assertEquals(4, noSection.size)
        assertTrue(noSection[2].contains("shift"))
    }

    @Test
    fun `partial rows are padded with defaults up to four`() {
        // 只有 3 个字母行（如旧版双拼模板）：自动补出控制行
        val rows = rows("""{"layout":{"rows":[["q","w","e"],["a","s","d"],["z","x","c"]]}}""")
        assertEquals(4, rows.size)
        assertEquals(listOf("mode_change", "comma", "space", "earth", "enter"), rows[3])
    }

    @Test
    fun `merged key subarrays join into single id`() {
        val rows = rows("""{"layout":{"rows":[[["q","w"],["e","r"],"t"]]}}""")
        assertEquals(listOf("qw", "er", "t"), rows[0])
    }

    @Test
    fun `scalar rows are split manually with bracket groups`() {
        val rows = rows("""{"layout":{"rows":["z, [x, c], v"]}}""")
        assertEquals(listOf("z", "xc", "v"), rows[0])
    }

    @Test
    fun `more than five rows are truncated and empty rows are skipped`() {
        val truncated = rows(
            """
            {"layout":{"rows":[["a"],["b"],["c"],["d"],["e"],["f"]]}}
            """.trimIndent(),
        )
        assertEquals(5, truncated.size)

        // 空行在解析层被丢弃（对齐 Xime parseLayoutRowsNode），随后由兜底行补齐
        val withEmpty = rows("""{"layout":{"rows":[["a","b"],[],["c"]]}}""")
        assertEquals(listOf("a", "b"), withEmpty[0])
        assertEquals(listOf("c"), withEmpty[1])
        assertEquals(4, withEmpty.size)
        assertEquals(listOf("mode_change", "comma", "space", "earth", "enter"), withEmpty[3])
    }

    @Test
    fun `number row template yields five rows`() {
        val rows = rows(
            """
            {"layout":{"rows":[
                ["1","2","3","4","5","6","7","8","9","0"],
                ["q","w","e","r","t","y","u","i","o","p"],
                ["a","s","d","f","g","h","j","k","l"],
                ["shift","z","x","c","v","b","n","m","delete"],
                ["mode_change","comma","space","earth","enter"]
            ]}}
            """.trimIndent(),
        )
        assertEquals(5, rows.size)
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), rows[0])
    }

    @Test
    fun `default layout rows expose shift and delete`() {
        val defaults = defaultLayoutRows()
        assertEquals(4, defaults.size)
        assertTrue(defaults[2].contains("shift") && defaults[2].contains("delete"))
    }
}
