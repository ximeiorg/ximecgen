import { describe, expect, it } from 'vitest'
import { resolvePalette, schemePalette, toCss } from './palette'

describe('toCss', () => {
  it('24-bit color treated as opaque', () => {
    expect(toCss(0x8f73e2, '#000')).toBe('#8f73e2')
  })

  it('32-bit ARGB keeps alpha', () => {
    expect(toCss(0x60ffffff, '#000')).toBe('#ffffff60')
    expect(toCss(0xffe3e4e8, '#000')).toBe('#e3e4e8')
  })

  it('invalid values fall back', () => {
    expect(toCss(undefined, '#fb')).toBe('#fb')
    expect(toCss(-1, '#fb')).toBe('#fb')
  })
})

describe('schemePalette fallback chain', () => {
  it('dark mode uses _dark variants, no light fallback (aligned with Xime)', () => {
    const p = schemePalette(
      { key_bg_color: 0xff0000, key_bg_color_dark: 0x0000ff, key_text_color: 0x202124 },
      {},
      true,
    )
    expect(p.keyBg).toBe('#0000ff')
    // 深色只读 _dark 键，缺失不回退浅色（对齐 Xime），用内置深色文字
    expect(p.keyText).toBe('#E8EAED')

    const lightOnly = schemePalette({ key_bg_color: 0xff0000 }, {}, true)
    // 深色下缺 _dark 变体 → 直接用内置深色兜底，不回退浅色值
    expect(lightOnly.keyBg).toBe('rgba(255,255,255,0.38)')
    expect(lightOnly.keyText).toBe('#E8EAED')

    const empty = schemePalette(undefined, undefined, true)
    expect(empty.keyBg).toBe('rgba(255,255,255,0.38)')
    expect(empty.keyboardBg).toBe('#202125')
  })

  it('keyboard.colors global fallback applies when scheme misses', () => {
    const p = schemePalette({}, { colors: { key_bg_color: 0x123456 } }, false)
    expect(p.keyBg).toBe('#123456')
  })

  it('special key bg explicit wins, dark derives to primary, light overlays primary', () => {
    const explicit = schemePalette({ special_key_bg_color: 0xeeeeee }, {}, false)
    expect(explicit.specialKeyBg).toBe('#eeeeee')

    const darkDerived = schemePalette({ primary_color: 0x8f73e2 }, {}, true)
    expect(darkDerived.specialKeyBg).toBe('#8f73e2')

    const lightDerived = schemePalette({ primary_color: 0x8f73e2 }, {}, false)
    expect(lightDerived.specialKeyBg).toBe('#8f73e247')
  })

  it('gradient background builds css gradient', () => {
    const p = schemePalette(
      { keyboard_background: { type: 'gradient', colors: [0xff0000, 0x0000ff], angle: 45 } },
      {},
      false,
    )
    expect(p.gradient).toBe('linear-gradient(45deg, #ff0000, #0000ff)')
  })
})

describe('resolvePalette', () => {
  it('style.color_scheme object picks variant by dark flag', () => {
    const config = {
      style: { color_scheme: { light: 'a', dark: 'b' } },
      color_schemes: { a: { key_bg_color: 0x111111 }, b: { key_bg_color: 0x222222, key_bg_color_dark: 0x333333 } },
    }
    expect(resolvePalette(config, false).keyBg).toBe('#111111')
    expect(resolvePalette(config, true).keyBg).toBe('#333333')
  })

  it('scalar color_scheme and explicit override', () => {
    const config = {
      style: { color_scheme: 'a' },
      color_schemes: { a: { key_bg_color: 0x111111 }, b: { key_bg_color: 0x222222, key_bg_color_dark: 0x333333 } },
    }
    expect(resolvePalette(config, false).keyBg).toBe('#111111')
    expect(resolvePalette(config, false, 'b').keyBg).toBe('#222222')
  })
})
