import { describe, expect, it } from 'vitest'
import { parseLayoutsIndex } from './templates'

/** 与 Android 端 TemplatesParserTest 共用同一份真实 index.yaml 结构快照。 */
const REAL_INDEX = `
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
`.trim()

describe('parseLayoutsIndex', () => {
  it('parses real index structure into entries', () => {
    const entries = parseLayoutsIndex(REAL_INDEX)
    expect(entries).toHaveLength(2)
    expect(entries[0]).toMatchObject({
      id: 'flypy',
      title: '小鹤双拼带韵母',
      description: '专属于小鹤双拼版本。',
      downloadUrl: 'https://cdn.example.org/flypy@1.0.0/xime.custom.yaml',
    })
    expect(entries[1].id).toBe('number_rows')
    expect(entries[1].downloadUrl).toBe('https://cdn.example.org/number_rows@master/xime.custom.yaml')
  })

  it('entry without url is skipped', () => {
    expect(parseLayoutsIndex(REAL_INDEX).some(e => e.id === 'broken_entry')).toBe(false)
  })

  it('missing name falls back to id, description to default', () => {
    const entries = parseLayoutsIndex(`
      layouts:
      - id: bare
        versions:
        - downloadUrl:
          - url: https://example.org/bare.yaml
    `)
    expect(entries).toHaveLength(1)
    expect(entries[0].title).toBe('bare')
    expect(entries[0].description).toBe('键盘布局模板')
  })

  it('quoted values and multiple download urls take first', () => {
    const entries = parseLayoutsIndex(`
      layouts:
      - id: quoted
        name: "带引号名字"
        versions:
        - downloadUrl:
          - url: 'https://a.example.org/first.yaml'
          - url: https://b.example.org/second.yaml
    `)
    expect(entries[0].title).toBe('带引号名字')
    expect(entries[0].downloadUrl).toBe('https://a.example.org/first.yaml')
  })

  it('blank and malformed input yield empty list', () => {
    expect(parseLayoutsIndex('')).toHaveLength(0)
    expect(parseLayoutsIndex('just: some: yaml')).toHaveLength(0)
  })
})
