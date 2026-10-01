import { useMemo } from 'react'
import { resolvePalette } from '../palette'

interface Props {
  config: any
  dark: boolean
  /** 预览指定方案（预览面板的方案切换器）；缺省跟随 style.color_scheme */
  schemeId?: string
}

// 调色板逻辑移至 src/palette.ts，预览与色卡选择器共用

/** 手势显示文本：字符串直出；对象取 label/value；display=bubble 不显示在键帽。 */
function gestureLabel(v: unknown): string | null {
  if (typeof v === 'string') return v
  if (v && typeof v === 'object') {
    const o = v as any
    // label 支持数组（多行显示，join("\n")），键帽提示取首个非空行
    const raw = Array.isArray(o.label) ? o.label.join('\n') : o.label ?? o.value
    const s = String(raw ?? '')
    const first = s.split('\n').find(l => l.trim() !== '')
    return first ?? null
  }
  return null
}

function gestureField(tap: any, field: string): string | null {
  if (!tap || typeof tap !== 'object') return null
  const v = tap[field]
  if (typeof v === 'string') return v.trim() || null
  if (Array.isArray(v)) {
    const s = v.filter(Boolean).join('\n').trim()
    return s || null
  }
  return null
}

function gestureDisplay(v: unknown): string | undefined {
  return v && typeof v === 'object' ? (v as any).display : undefined
}

/** 键面提示完整文本（保留 "\n" 多行，如小鹤双拼 "ue\nve"；裁剪在渲染层按布局模式执行）。 */
function gestureSurfaceText(v: unknown): string | null {
  if (typeof v === 'string') return v
  if (v && typeof v === 'object') {
    const o = v as any
    const raw = Array.isArray(o.label) ? o.label.join('\n') : o.label ?? o.value
    const s = String(raw ?? '')
    return s.trim() !== '' ? s : null
  }
  return null
}

// ── 布局模型：对齐 Xime 3.0（layout.rows 是含功能键的完整可配置行列表）──

/** 可被 layout.rows 引用的功能键 id（对齐 Xime KeysConfigHelper.FUNCTION_KEY_IDS）。 */
const FUNCTION_KEY_IDS = new Set([
  'shift', 'delete', 'enter', 'space', 'mode_change', 'symbol', 'emoji', 'earth', 'voice', 'comma',
])
/** 用普通按键底色的功能键（其余用特殊键底色），对齐 Xime 各 Cell 组件的取色。 */
const SOFT_FUNCTION_KEYS = new Set(['comma', 'earth', 'space'])
/** 功能键内置列宽：显式 keys.<id>.width 优先（对齐 Xime functionKeyWidth）。 */
const DEFAULT_FUNCTION_WIDTH: Record<string, number> = {
  shift: 1.4, delete: 1.4, mode_change: 1.2, enter: 1.2, earth: 0.8, comma: 0.8, space: 3,
}
/** 无 layout.rows 时的兜底行 = 内置 xime.yaml 的行布局（Xime 回退链：custom rows →
    内置 rows（含 shift/delete）→ 裸字母行）。模板类 custom yaml 不写 rows，实际渲染带功能键。 */
const DEFAULT_ROW_IDS = [
  ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
  ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
  ['shift', 'z', 'x', 'c', 'v', 'b', 'n', 'm', 'delete'],
  ['mode_change', 'comma', 'space', 'earth', 'enter'],
]

/** 标量行手动拆分：逗号为键分隔，方括号内为合并键组（组内逗号/空格丢弃，[x, c] → xc，对齐 Xime scalarRowToKeyIds）。 */
function splitScalarRow(content: string): string[] {
  const text = content.trim()
  if (!text) return []
  if (!text.includes(',') && !text.includes('[')) return [text]
  const items: string[] = []
  let buf = ''
  let depth = 0
  const flushItem = () => {
    const t = buf.trim()
    if (t) items.push(t)
    buf = ''
  }
  for (const ch of text) {
    if (ch === '[') { depth++; if (depth === 1) { buf = '' } else { buf += ch } }
    else if (ch === ']') {
      depth--
      if (depth === 0) flushItem()
      else if (depth < 0) depth = 0
      else buf += ch
    } else if (ch === ',' && depth === 0) flushItem()
    else if (ch === ',' && depth > 0) { /* 组内逗号丢弃 */ }
    else if (ch === ' ' && depth > 0) { /* 组内空格丢弃 */ }
    else buf += ch
  }
  flushItem()
  return items
}

/** 解析 layout.rows：行内子数组为合并键（组内 id 拼接，如 [q, w] → "qw"）。 */
function parseLayoutRows(qwerty: any): string[][] {
  const configured = qwerty?.layout?.rows
  if (!Array.isArray(configured)) return []
  const rows: string[][] = []
  for (const r of configured) {
    let row: string[] = []
    if (Array.isArray(r)) {
      row = r.map((item: unknown) => {
        if (typeof item === 'string') return item.trim()
        if (Array.isArray(item)) return item.map(x => String(x).trim()).filter(Boolean).join('')
        return ''
      }).filter(Boolean)
    } else if (typeof r === 'string') {
      row = splitScalarRow(r)
    }
    if (row.length > 0) rows.push(row)
  }
  return rows
}

/** 规范化行：最多 5 行，缺失行用内置默认补齐，空行截断（对齐 Xime normalizeQwertyRows）。 */
function normalizeRows(rows: string[][]): string[][] {
  const out: string[][] = []
  for (let i = 0; i < 5; i++) {
    const row = rows[i] ?? DEFAULT_ROW_IDS[i]
    if (!row || row.length === 0) break
    out.push(row)
  }
  return out.length > 0 ? out : DEFAULT_ROW_IDS
}

interface KeyInfo {
  id: string
  isFunction: boolean
  icon: 'none' | 'shift' | 'delete' | 'earth' | 'symbol' | 'emoji' | 'voice'
  label: string
  width: number
  swipeUp: string | null
  swipeDown: string | null
  compact: boolean
}

function keyWidth(keys: any, id: string, fallback: number): number {
  const w = keys?.[id]?.width
  return typeof w === 'number' && w > 0 ? w : fallback
}

/** 功能键键面信息：内置图标/标签，可被 keys.<id>.tap 覆盖（对齐 Xime FunctionKeyCell）。 */
function functionKeyInfo(keys: any, id: string, binding: any, compact: boolean): KeyInfo {
  const tap = binding?.tap && typeof binding.tap === 'object' ? binding.tap : null
  const label = gestureField(tap, 'label')
  const value = gestureField(tap, 'value')
  let icon: KeyInfo['icon'] = 'none'
  let main = ''
  switch (id) {
    case 'shift': icon = 'shift'; break
    case 'delete': icon = 'delete'; break
    case 'mode_change': main = label ?? '?123'; break
    case 'enter': main = label ?? '换行'; break
    // 键面显示 label 优先（对齐 Xime CommaCell：text = label ?: value）
    case 'comma': main = label ?? value ?? '，'; break
    // earth 恒带地球图标（icon 优先于文本，@ 前缀视为图标名）
    case 'earth': if (label && !label.startsWith('@')) { main = label } else { icon = 'earth' }; break
    case 'space': break // 渲染时显示方案名
    case 'symbol': icon = 'symbol'; break
    case 'emoji': icon = 'emoji'; break
    case 'voice': icon = 'voice'; break
  }
  const upFromCfg = gestureDisplay(binding?.swipe_up) !== 'bubble' ? gestureSurfaceText(binding?.swipe_up) : null
  const swipeUp = upFromCfg ?? (id === 'delete' && !('swipe_up' in (binding ?? {})) ? '清空' : null)
  const swipeDown = gestureDisplay(binding?.swipe_down) !== 'bubble' ? gestureSurfaceText(binding?.swipe_down) : null
  return {
    id, isFunction: true, icon, label: main,
    width: keyWidth(keys, id, DEFAULT_FUNCTION_WIDTH[id] ?? 1),
    swipeUp, swipeDown, compact,
  }
}

/** 键位 → 键面信息（预览与编辑器共用同一取值规则）。 */
function capInfo(keys: any, id: string, compact: boolean): KeyInfo {
  const binding = keys?.[id] ?? {}
  if (FUNCTION_KEY_IDS.has(id)) return functionKeyInfo(keys, id, binding, compact)
  const tap = binding?.tap
  let main: string
  if (typeof tap === 'string') main = tap
  else if (tap && typeof tap === 'object') {
    const raw = Array.isArray(tap.label) ? tap.label.join('\n') : tap.label ?? tap.value ?? ''
    main = String(raw).split('\n').find(l => l.trim() !== '') ?? ''
  } else main = ''
  if (!main) main = id
  if (main.startsWith('@')) main = main.slice(1)
  if (/[a-z]/i.test(main)) main = main.toUpperCase()
  // 上滑/下滑提示保留完整多行文本（如 "ue\nve"），裁剪在渲染层按布局模式执行
  const swipeUp = gestureDisplay(binding?.swipe_up) !== 'bubble' ? gestureSurfaceText(binding?.swipe_up) : null
  const swipeDown = gestureDisplay(binding?.swipe_down) !== 'bubble' ? gestureSurfaceText(binding?.swipe_down) : null
  return {
    id, isFunction: false, icon: 'none', label: main,
    width: keyWidth(keys, id, 1), swipeUp, swipeDown, compact,
  }
}

/** Compose Color.luminance() 的对应实现：线性化 sRGB 后按 2126/7152/722 加权。 */
function isLightColor(css: string): boolean {
  let r = 0, g = 0, b = 0
  if (css.startsWith('#')) {
    const hex = css.slice(1)
    const rgb = hex.length === 8 ? hex.slice(2) : hex
    r = parseInt(rgb.slice(0, 2), 16) || 0
    g = parseInt(rgb.slice(2, 4), 16) || 0
    b = parseInt(rgb.slice(4, 6), 16) || 0
  } else {
    const m = css.match(/rgba?\(([^)]+)\)/)
    if (m) [r, g, b] = m[1].split(',').map(s => parseFloat(s))
  }
  const lin = (c: number) => {
    const s = c / 255
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
  }
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b) > 0.5
}

/** 功能键内置图标（对齐 Xime FunctionKeyCell 的 Material 图标）。 */
function FuncIcon({ kind, color }: { kind: KeyInfo['icon']; color: string }) {
  const p = { width: 20, height: 20, display: 'block', viewBox: '0 0 24 24' } as const
  switch (kind) {
    case 'shift':
      return <svg {...p} fill={color}><path d="M12 4l8 8.5h-5.2V20H9.2v-7.5H4z" /></svg>
    case 'delete':
      return <svg {...p} fill={color}><path d="M22 5H9.4l-7.3 7 7.3 7H22a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1zm-4.2 9.6-1.4 1.4-2.4-2.4-2.4 2.4-1.4-1.4 2.4-2.6-2.4-2.6 1.4-1.4 2.4 2.4 2.4-2.4 1.4 1.4-2.4 2.6z" /></svg>
    case 'earth':
      return <svg {...p} fill="none" stroke={color} strokeWidth="1.8"><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c2.8 2.6 4 5.6 4 9s-1.2 6.4-4 9c-2.8-2.6-4-5.6-4-9s1.2-6.4 4-9z" /></svg>
    case 'symbol':
      return <svg {...p} fill={color}><path d="M20 5H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2zM7 9.5h2v2H7zm0 3.5h2v2H7zm3.5-3.5h2v2h-2zm0 3.5h2v2h-2zm3.5-3.5h2v2h-2zm0 3.5h2v2h-2z" /></svg>
    case 'emoji':
      return <svg {...p} fill={color}><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zM8.5 8.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm7 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zM12 18c-2.3 0-4.3-1.4-5.2-3.4h10.4C16.3 16.6 14.3 18 12 18z" /></svg>
    case 'voice':
      return <svg {...p} fill={color}><path d="M12 15a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V22h2v-3.08A7 7 0 0 0 19 12z" /></svg>
    default:
      return null
  }
}

export function PreviewPanel({ config, dark, schemeId }: Props) {
  const palette = useMemo(() => resolvePalette(config, dark, schemeId), [config, dark, schemeId])
  const keyboard = config?.keyboard ?? {}
  const qwerty = keyboard.qwerty ?? {}
  const keys = qwerty.keys ?? {}
  const compact = qwerty.button_layout === 'compact'
  const rows = useMemo(() => normalizeRows(parseLayoutRows(qwerty)), [qwerty])
  const schemaName = '五笔拼音'

  const keyCfg = keyboard.key ?? {}
  const cornerRadius = typeof keyCfg.corner_radius === 'number' ? keyCfg.corner_radius : 8
  const spacingX = typeof keyCfg.spacing_x === 'number' ? keyCfg.spacing_x : 2
  const spacingY = typeof keyCfg.spacing_y === 'number' ? keyCfg.spacing_y : 4.25
  const shadow = (keyboard.shadow ?? {}).enabled !== false
  const elevation = keyboard.shadow?.elevation ?? 0.5

  // 间距模型对齐 Xime：spacing_x/spacing_y 是每个键四周的 margin，
  // 相邻键水平间隙 = spacing_x*2，行间垂直间隙 = spacing_y*2。
  const keyStyle = (bg: string, flex: number): React.CSSProperties => ({
    flexGrow: flex,
    flexBasis: 0,
    height: 46,
    margin: `${spacingY}px ${spacingX}px`,
    borderRadius: cornerRadius,
    background: bg,
    color: palette.keyText,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 19,
    fontWeight: 500,
    position: 'relative',
    boxShadow: shadow ? `0 ${elevation}px ${elevation * 2}px rgba(0,0,0,0.2)` : undefined,
  })

  const keyCap = (info: KeyInfo, bg: string, contentColor?: string) => {
    const style = { ...keyStyle(bg, info.width), ...(contentColor ? { color: contentColor } : {}) }
    if (info.compact) {
      // compact：主字符左上，右上列放提示，下滑占右侧中部（对齐官方 SwipeableKeyButton）
      const hasChinese = !!info.swipeDown && [...info.swipeDown].some(c => c >= '\u4e00' && c <= '\u9fff')
      return (
        <div key={info.id} style={{ ...style, alignItems: 'flex-start', justifyContent: 'flex-start' }}>
          <span style={{ position: 'absolute', top: 2, left: 4, fontSize: 16, fontWeight: info.label.length > 2 ? 500 : 400, lineHeight: '16px' }}>
            {info.label}
          </span>
          <div style={{ position: 'absolute', top: 4, right: 4, bottom: 2, display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
            {info.swipeUp && (
              <span style={{ fontSize: 9, fontWeight: 500, opacity: 0.6 }}>{info.swipeUp.slice(0, 2)}</span>
            )}
            {info.swipeDown && (
              <div style={{ flexGrow: 1, display: 'flex', alignItems: 'flex-end' }}>
                <span style={{ fontSize: hasChinese ? 7.6 : 9, fontWeight: 500, opacity: 0.7, textAlign: 'right', whiteSpace: 'pre-line' }}>
                  {info.swipeDown.slice(0, 12)}
                </span>
              </div>
            )}
          </div>
        </div>
      )
    }
    // standard：主字符居中，提示以中心为锚 ±14px 偏移（对齐官方）
    return (
      <div key={info.id} style={style}>
        <span style={{ fontSize: info.label.length > 2 ? 14 : 18, fontWeight: info.label.length > 2 ? 500 : 400 }}>
          {info.label}
        </span>
        {info.swipeUp && (
          <span style={{ position: 'absolute', fontSize: 9, fontWeight: 500, opacity: 0.6, transform: 'translateY(-14px)' }}>
            {info.swipeUp.slice(0, 4).split('\n')[0]}
          </span>
        )}
        {info.swipeDown && (
          <span style={{ position: 'absolute', fontSize: 9, opacity: 0.5, transform: 'translateY(14px)' }}>
            {info.swipeDown.slice(0, 4).split('\n')[0]}
          </span>
        )}
      </div>
    )
  }

  const functionKey = (info: KeyInfo) => {
    const soft = SOFT_FUNCTION_KEYS.has(info.id)
    const bg = soft ? palette.keyBg : palette.specialKeyBg
    // comma/earth/space 用按键文字色；其余按特殊键背景亮度自适应（对齐 Xime getSpecialKeyTextColorForBackground）
    const contentColor = soft || isLightColor(bg) ? palette.keyText : '#E8EAED'
    const style = keyStyle(bg, info.width)
    return (
      <div key={info.id} style={style}>
        {info.icon !== 'none' ? (
          <FuncIcon kind={info.icon} color={contentColor} />
        ) : info.id === 'space' ? (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <span style={{ position: 'absolute', left: 10, fontSize: 11, opacity: 0.45 }}>空格</span>
            <span style={{ fontSize: 16 }}>{schemaName}</span>
          </div>
        ) : (
          <span style={{ fontSize: info.label.length > 2 ? 14 : 16, fontWeight: info.label.length > 2 ? 500 : 400 }}>
            {info.label}
          </span>
        )}
        {info.swipeUp && (
          <span style={{ position: 'absolute', top: -4, left: 0, right: 0, fontSize: 9, fontWeight: 500, opacity: 0.6, textAlign: 'center' }}>
            {info.swipeUp.slice(0, 4)}
          </span>
        )}
        {info.swipeDown && (
          <span style={{ position: 'absolute', bottom: -4, left: 0, right: 0, fontSize: 9, opacity: 0.5, textAlign: 'center' }}>
            {info.swipeDown.slice(0, 4)}
          </span>
        )}
      </div>
    )
  }

  const firstRowSize = rows[0]?.length ?? 10

  return (
    <div style={{
      width: 420, margin: '0 auto', borderRadius: 16, overflow: 'hidden',
      boxShadow: '0 8px 32px rgba(0,0,0,0.3)',
      background: palette.gradient ?? (palette.isImage ? (dark ? '#3A3F4C' : '#8D93A1') : palette.keyboardBg),
      padding: '4px 4px 8px',
    }}>
      {/* 候选栏 */}
      <div style={{ height: 44, display: 'flex', alignItems: 'center', padding: '0 8px' }}>
        <div style={{
          width: 34, height: 34, borderRadius: 17, background: palette.primary + '29',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <div style={{ width: 16, height: 16, borderRadius: 4, background: palette.primary }} />
        </div>
        <div style={{ flex: 1 }} />
        <span style={{ color: palette.candidateText, fontSize: 16, padding: '0 10px' }}>五笔</span>
        <span style={{ color: palette.candidateText, opacity: 0.75, fontSize: 16, padding: '0 10px' }}>拼音</span>
        <span style={{ color: palette.candidateText, opacity: 0.75, fontSize: 16, padding: '0 10px' }}>输入法</span>
        <div style={{ flex: 0.6 }} />
        <span style={{ color: palette.keyText, opacity: 0.55, fontSize: 18 }}>⌄</span>
      </div>

      {/* 按键行：行数与内容完全由 layout.rows 驱动（最多 5 行，含功能键行），
          行内按 id 分派功能键/字母键——全部可配置，无硬编码行（对齐 Xime QwertyRow） */}
      {rows.map((rowIds, ri) => {
        // 9 键纯字母行（如 asdf 行）整行居中缩进，宽度取 9/首行键数比例（对齐 Xime）
        const indent = rowIds.length === 9 && rowIds.every(k => !FUNCTION_KEY_IDS.has(k))
        const rowStyle: React.CSSProperties = {
          display: 'flex',
          ...(indent ? { width: `${(9 / firstRowSize) * 100}%`, margin: '0 auto' } : {}),
        }
        return (
          <div key={ri} style={rowStyle}>
            {rowIds.map(id => {
              const info = capInfo(keys, id, compact)
              return info.isFunction
                ? functionKey(info)
                : keyCap(info, palette.keyBg)
            })}
          </div>
        )
      })}
    </div>
  )
}
