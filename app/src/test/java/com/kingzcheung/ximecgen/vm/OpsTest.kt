package com.kingzcheung.ximecgen.vm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ops 构造工具与颜色解析的纯逻辑。 */
class OpsTest {

    @Test
    fun `ops builders produce json pointer operations`() {
        assertEquals(
            JSONObject().put("op", "set").put("path", "/a/b").put("value", "x").toString(),
            Ops.set("/a/b", "x").toString(),
        )
        val arr = JSONArray().put(1)
        assertEquals(
            JSONObject().put("op", "add").put("path", "/rows/-").put("value", arr).toString(),
            Ops.add("/rows/-", arr).toString(),
        )
        assertEquals(
            JSONObject().put("op", "remove").put("path", "/a").toString(),
            Ops.remove("/a").toString(),
        )
    }

    @Test
    fun `op value accepts numbers and objects`() {
        assertEquals(2, Ops.set("/keyboard/key/spacing_x", 2).get("value"))
        assertEquals(4.25, Ops.set("/keyboard/key/spacing_y", 4.25).get("value"))
        assertEquals(true, Ops.set("/keyboard/shadow/enabled", true).get("value"))
        assertEquals(
            JSONObject.NULL,
            Ops.set("/keyboard/fonts/key_font", JSONObject.NULL).get("value"),
        )
    }

    @Test
    fun `parse color accepts hex decimal and hash formats`() {
        assertEquals(0x8F73E2L, ConfigViewModel.parseColor("0x8F73E2"))
        assertEquals(0x8F73E2L, ConfigViewModel.parseColor("0X8f73e2"))
        assertEquals(0xFF0000L, ConfigViewModel.parseColor("#FF0000"))
        assertEquals(0xFF0000L, ConfigViewModel.parseColor("16711680"))
        // 8 位 ARGB
        assertEquals(0x60FFFFFFL, ConfigViewModel.parseColor("0x60FFFFFF"))
    }

    @Test
    fun `parse color rejects invalid input`() {
        assertNull(ConfigViewModel.parseColor(""))
        assertNull(ConfigViewModel.parseColor("not-a-color"))
        assertNull(ConfigViewModel.parseColor("0x"))
        // 超出 32 位
        assertNull(ConfigViewModel.parseColor("0x1FFFFFFFF"))
    }
}
