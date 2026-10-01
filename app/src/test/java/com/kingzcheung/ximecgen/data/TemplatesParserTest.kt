package com.kingzcheung.ximecgen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模板子索引逐行状态机解析（真实 index.yaml 结构快照）。 */
class TemplatesParserTest {

    private val realIndexSample = """
        updated_at: '2026-09-30'
        layouts:
        - id: flypy
          name: 小鹤双拼带韵母
          author: kingzcheung
          description: 专属于小鹤双拼版本。
          tags:
          - 布局
          - 全键盘
          repo: https://github.com/cz-archive/flypy
          license: GPL-3.0
          appVersion: '>=3.0.0'
          versions:
          - version: 1.0.0
            changelog: 首个版本。
            downloadUrl:
            - url: https://cdn.example.org/flypy@1.0.0/xime.custom.yaml
              size: 11106
        - id: number_rows
          name: 数字行
          author: kingzcheung
          description: 在标准全键盘上方新增一行数字，中文键盘长按出符号、英文键盘直接上屏。
          versions:
          - version: 1.0.0
            downloadUrl:
            - url: https://cdn.example.org/number_rows@master/xime.custom.yaml
        - id: broken_entry
          name: 没有 URL 的条目
    """.trimIndent()

    @Test
    fun `parses real index structure into entries`() {
        val entries = TemplateFetcher.parseLayoutsIndex(realIndexSample)
        assertEquals(2, entries.size)

        assertEquals("flypy", entries[0].id)
        assertEquals("小鹤双拼带韵母", entries[0].title)
        assertEquals("专属于小鹤双拼版本。", entries[0].description)
        assertEquals("https://cdn.example.org/flypy@1.0.0/xime.custom.yaml", entries[0].downloadUrl)

        assertEquals("number_rows", entries[1].id)
        assertEquals("数字行", entries[1].title)
        // description 非空且含中文顿号句式时原样保留
        assertEquals("在标准全键盘上方新增一行数字，中文键盘长按出符号、英文键盘直接上屏。", entries[1].description)
    }

    @Test
    fun `entry without url is skipped`() {
        val entries = TemplateFetcher.parseLayoutsIndex(realIndexSample)
        assertTrue(entries.none { it.id == "broken_entry" })
    }

    @Test
    fun `missing name or description fall back to defaults`() {
        val entries = TemplateFetcher.parseLayoutsIndex(
            """
            layouts:
            - id: bare
              versions:
              - downloadUrl:
                - url: https://example.org/bare.yaml
            """.trimIndent(),
        )
        assertEquals(1, entries.size)
        assertEquals("name 缺失时用 id", "bare", entries[0].title)
        assertEquals("键盘布局模板", entries[0].description)
    }

    @Test
    fun `quoted values and multiple download urls take first`() {
        val entries = TemplateFetcher.parseLayoutsIndex(
            """
            layouts:
            - id: quoted
              name: "带引号名字"
              versions:
              - downloadUrl:
                - url: 'https://a.example.org/first.yaml'
                - url: https://b.example.org/second.yaml
            """.trimIndent(),
        )
        assertEquals("带引号名字", entries[0].title)
        assertEquals("https://a.example.org/first.yaml", entries[0].downloadUrl)
    }

    @Test
    fun `blank and malformed input yield empty list`() {
        assertEquals(0, TemplateFetcher.parseLayoutsIndex("").size)
        assertEquals(0, TemplateFetcher.parseLayoutsIndex("just: some: yaml").size)
    }
}
