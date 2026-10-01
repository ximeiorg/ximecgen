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

function gestureDisplay(v: unknown): string | undefined {
  return v && typeof v === 'object' ? (v as any).display : undefined
}

const DEFAULT_ROWS = [
  ['q','w','e','r','t','y','u','i','o','p'],
  ['a','s','d','f','g','h','j','k','l'],
  ['z','x','c','v','b','n','m'],
]

export function PreviewPanel({ config, dark, schemeId }: Props) {
  const palette = useMemo(() => resolvePalette(config, dark, schemeId), [config, dark, schemeId])
  const keyboard = config?.keyboard ?? {}
  const qwerty = keyboard.qwerty ?? {}
  const keys = qwerty.keys ?? {}
  const compact = qwerty.button_layout === 'compact'
  const rows: string[][] = useMemo(() => {
    const configured: unknown = qwerty.layout?.rows
    if (Array.isArray(configured)) {
      return configured.slice(0, 3).map((row: unknown) =>
        Array.isArray(row) ? row.map(String) : [],
      )
    }
    return DEFAULT_ROWS
  }, [qwerty])

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

  /** 预览显示文本：单字母键大写（与官方一致）。 */
  const showLabel = (raw: string) =>
    raw.length === 1 && /[a-z]/.test(raw) ? raw.toUpperCase() : raw

  const keyCap = (id: string, label: string, bg: string, flex: number, icon = false, textColor?: string) => {
    const binding = keys[id]
    const swipeUp = gestureDisplay(binding?.swipe_up) !== 'bubble' ? gestureLabel(binding?.swipe_up) : null
    const swipeDown = gestureDisplay(binding?.swipe_down) !== 'bubble' ? gestureLabel(binding?.swipe_down) : null
    const style = { ...keyStyle(bg, flex), ...(textColor ? { color: textColor } : {}) }
    const isCompact = compact
    if (isCompact) {
      // compact：主字符左上，右上列放提示，下滑占右侧中部（对齐官方 SwipeableKeyButton）
      const hasChinese = !!swipeDown && [...swipeDown].some(c => c >= '\u4e00' && c <= '\u9fff')
      return (
        <div key={id} style={{ ...style, alignItems: 'flex-start', justifyContent: 'flex-start' }}>
          <span style={{ position: 'absolute', top: 2, left: 4, fontSize: 16, fontWeight: label.length > 2 ? 500 : 400, lineHeight: '16px' }}>
            {showLabel(label)}
          </span>
          <div style={{ position: 'absolute', top: 4, right: 4, bottom: 2, display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
            {swipeUp && (
              <span style={{ fontSize: 9, fontWeight: 500, opacity: 0.6 }}>{swipeUp.slice(0, 2)}</span>
            )}
            {swipeDown && (
              <div style={{ flexGrow: 1, display: 'flex', alignItems: 'flex-end' }}>
                <span style={{ fontSize: hasChinese ? 7.6 : 9, fontWeight: 500, opacity: 0.7, textAlign: 'right' }}>
                  {swipeDown.slice(0, 12)}
                </span>
              </div>
            )}
          </div>
        </div>
      )
    }
    // standard：主字符居中，提示以中心为锚 ±14px 偏移（对齐官方）
    return (
      <div key={id} style={style}>
        <span style={{ fontSize: label.length > 2 ? 14 : 18, fontWeight: label.length > 2 ? 500 : 400 }}>
          {showLabel(label)}
        </span>
        {swipeUp && (
          <span style={{ position: 'absolute', fontSize: 9, fontWeight: 500, opacity: 0.6, transform: 'translateY(-14px)' }}>
            {swipeUp.slice(0, 4)}
          </span>
        )}
        {swipeDown && (
          <span style={{ position: 'absolute', fontSize: 9, opacity: 0.5, transform: 'translateY(14px)' }}>
            {swipeDown.slice(0, 4)}
          </span>
        )}
      </div>
    )
  }

  const rowStyle: React.CSSProperties = {
    display: 'flex',
  }

  const commaLabel = gestureLabel(keys["'"]?.tap) ?? '，'

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

      {/* 第 1 行 */}
      <div style={rowStyle}>
        {rows[0].map(k => keyCap(k, gestureLabel(keys[k]?.tap) ?? k, palette.keyBg, 1))}
      </div>
      {/* 第 2 行（9 键，左右各缩进 16px） */}
      <div style={{ ...rowStyle, paddingLeft: 16, paddingRight: 16 }}>
        {rows[1].map(k => keyCap(k, gestureLabel(keys[k]?.tap) ?? k, palette.keyBg, 1))}
      </div>
      {/* 第 3 行：shift + 字母 + 删除 */}
      <div style={rowStyle}>
        {keyCap('shift_l', '⌃', palette.specialKeyBg, 1.4)}
        <div style={{ display: 'flex', flexGrow: 7.2, flexBasis: 0 }}>
          {rows[2].map(k => keyCap(k, gestureLabel(keys[k]?.tap) ?? k, palette.keyBg, 1))}
        </div>
        {keyCap('delete', '⌫', dark ? palette.primary : palette.specialKeyBg, 1.4, true, dark ? '#FFFFFF' : palette.primary)}
      </div>
      {/* 第 4 行（固定） */}
      <div style={rowStyle}>
        {keyCap('?123', '?123', palette.specialKeyBg, 1.2)}
        {keyCap("'", commaLabel, palette.keyBg, 0.8)}
        {keyCap('space', '五笔拼音', palette.keyBg, 3.2)}
        {keyCap('earth', '⊕', palette.keyBg, 0.8)}
        {keyCap('enter', '换行', palette.specialKeyBg, 1.2)}
      </div>
    </div>
  )
}
