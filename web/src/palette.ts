/** 配色解析：Android 端 buildPreviewData 的对应实现，供预览与色卡选择器共用。 */

/** 0xRRGGBB(AA) 数值 → CSS 颜色。 */
export function toCss(num: number | undefined | null, fallback: string): string {
  if (num === undefined || num === null || num < 0) return fallback
  const hex = num.toString(16).padStart(8, '0')
  // hex 恒为 8 位：低 6 位是 RGB；24 位色（≤0xFFFFFF）没有 alpha 位，视为不透明
  const rgb = hex.slice(2)
  const a = num <= 0xffffff ? 'ff' : hex.slice(0, 2)
  return `#${rgb}${a === 'ff' ? '' : a}`
}

const num = (v: unknown): number | undefined =>
  typeof v === 'number' && v > 0 ? v : undefined

export interface SchemePalette {
  keyboardBg: string
  keyBg: string
  specialKeyBg: string
  keyText: string
  candidateText: string
  primary: string
  gradient: string | null
  isImage: boolean
}

const FALLBACK_DARK = { keyboardBg: '#202125', keyBg: 'rgba(255,255,255,0.38)', keyText: '#E8EAED' }
const FALLBACK_LIGHT = { keyboardBg: '#E3E4E8', keyBg: '#FFFFFF', keyText: '#202124' }

/** 从单个 color_schemes 条目解析调色板（dark 决定用 _dark 变体与深浅兜底）。 */
export function schemePalette(scheme: any, keyboard: any, dark: boolean): SchemePalette {
  const fb = dark ? FALLBACK_DARK : FALLBACK_LIGHT

  let keyboardBg = toCss(
    num(scheme?.keyboard_bg_color) ?? num(keyboard?.colors?.[dark ? 'keyboard_bg_color_dark' : 'keyboard_bg_color']),
    fb.keyboardBg,
  )
  let gradient: string | null = null
  let isImage = false
  const bg = scheme?.keyboard_background
  if (bg?.type === 'gradient') {
    const stops = (dark ? bg.colors_dark ?? bg.colors : bg.colors) ?? []
    if (Array.isArray(stops) && stops.length >= 2) {
      gradient = `linear-gradient(${bg.angle ?? 90}deg, ${stops.map((s: number) => toCss(s, '#888')).join(', ')})`
    }
  } else if (bg?.type === 'solid') {
    keyboardBg = toCss(num(dark ? bg.color_dark ?? bg.color : bg.color), keyboardBg)
  } else if (bg?.type === 'image') {
    isImage = true
  }

  const primary = toCss(num(scheme?.primary_color), '#8F73E2')
  const keyBg = toCss(
    num(scheme?.[dark ? 'key_bg_color_dark' : 'key_bg_color'])
      ?? num(keyboard?.colors?.[dark ? 'key_bg_color_dark' : 'key_bg_color']),
    fb.keyBg,
  )
  const explicitSpecial = num(scheme?.[dark ? 'special_key_bg_color_dark' : 'special_key_bg_color'])
    ?? num(keyboard?.colors?.[dark ? 'special_key_bg_color_dark' : 'special_key_bg_color'])
  // 官方行为：浅色=主题色 25% 叠加，深色=直接主题色
  const specialKeyBg = explicitSpecial
    ? toCss(explicitSpecial, primary)
    : dark ? primary : primary + '47'

  return {
    keyboardBg,
    keyBg,
    specialKeyBg,
    keyText: toCss(
      num(scheme?.[dark ? 'key_text_color_dark' : 'key_text_color'])
        ?? num(keyboard?.colors?.[dark ? 'key_text_color_dark' : 'key_text_color']),
      fb.keyText,
    ),
    candidateText: toCss(
      num(scheme?.[dark ? 'candidate_text_color_dark' : 'candidate_text_color'])
        ?? num(keyboard?.colors?.[dark ? 'candidate_text_color_dark' : 'candidate_text_color']),
      dark ? '#8AB4F8' : '#1A73E8',
    ),
    primary,
    gradient,
    isImage,
  }
}

/** config + 可选方案 id（缺省走 style.color_scheme）→ 当前生效调色板。 */
export function resolvePalette(config: any, dark: boolean, schemeOverride?: string): SchemePalette {
  const style = config?.style ?? {}
  const schemes = config?.color_schemes ?? {}
  const keyboard = config?.keyboard ?? {}

  let schemeId: string | undefined = schemeOverride || undefined
  if (!schemeId) {
    const cs = style.color_scheme
    if (cs && typeof cs === 'object') schemeId = dark ? cs.dark : cs.light
    else if (typeof cs === 'string') schemeId = cs
  }
  return schemePalette(schemeId ? schemes[schemeId] : undefined, keyboard, dark)
}

/** 供色卡选择器用：列出所有方案 id（保持 YAML 顺序）。 */
export function schemeIds(config: any): string[] {
  const schemes = config?.color_schemes
  return schemes && typeof schemes === 'object' ? Object.keys(schemes).filter(k => k !== 'dynamic') : []
}
