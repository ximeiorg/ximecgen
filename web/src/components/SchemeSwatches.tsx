import { schemeIds, schemePalette } from '../palette'

interface Props {
  config: any
  /** 当前选中的方案 id（'' 表示未选择） */
  value: string
  onChange: (id: string) => void
  /** compact：用于预览面板的小尺寸色卡 */
  compact?: boolean
}

/**
 * radio 单选组：每项 = 圆点 + 该方案的实际色块 + 中文名。
 * mode 决定色块取浅色还是深色调色板（both 两行都画）；'dynamic' 始终在列表末尾。
 */
export function SchemeRadioGroup({ config, value, onChange, mode = 'both' }: {
  config: any
  value: string
  onChange: (id: string) => void
  mode?: 'light' | 'dark' | 'both'
}) {
  const schemes = config?.color_schemes ?? {}
  const keyboard = config?.keyboard ?? {}
  const ids = schemeIds(config)
  const dynamicName = String(schemes?.dynamic?.name ?? '动态配色')

  const item = (id: string, selected: boolean, onClick: () => void, chips: React.ReactNode, name: string, sub?: string) => (
    <button
      key={id}
      onClick={onClick}
      role="radio"
      aria-checked={selected}
      style={{
        display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', textAlign: 'left',
        padding: '6px 10px', borderRadius: 8,
        border: selected ? '1px solid #89b4fa' : '1px solid #313244',
        background: selected ? '#313244' : '#181825', color: '#cdd6f4',
      }}
    >
      <span style={{
        width: 16, height: 16, borderRadius: 9, flexShrink: 0,
        border: selected ? '2px solid #89b4fa' : '2px solid #585b70',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      }}>
        {selected && <span style={{ width: 8, height: 8, borderRadius: 4, background: '#89b4fa' }} />}
      </span>
      {chips}
      <span style={{ minWidth: 0 }}>
        <span style={{ display: 'block', fontSize: 13, fontWeight: selected ? 600 : 400, whiteSpace: 'nowrap' }}>{name}</span>
        {sub && <span style={{ display: 'block', fontSize: 11, color: '#6c7086' }}>{sub}</span>}
      </span>
    </button>
  )

  const chipRow = (p: ReturnType<typeof schemePalette>) => (
    <span style={{ display: 'inline-flex', gap: 3, flexShrink: 0 }}>
      {[p.keyboardBg, p.keyBg, p.primary, p.keyText].map((c, i) => (
        <span key={i} style={{
          width: 16, height: 16, borderRadius: 4, background: c,
          border: '1px solid rgba(255,255,255,0.14)', boxSizing: 'border-box',
        }} />
      ))}
    </span>
  )

  const dynamicChips = (
    <span style={{
      display: 'inline-flex', gap: 3, flexShrink: 0,
    }}>
      <span style={{
        width: 40, height: 16, borderRadius: 4,
        background: 'linear-gradient(90deg, #f38ba8, #f9e2af, #a6e3a1, #89dceb, #89b4fa, #cba6f7)',
        border: '1px solid rgba(255,255,255,0.14)', boxSizing: 'border-box',
      }} />
    </span>
  )

  return (
    <div role="radiogroup" style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
      {ids.map(id => {
        const scheme = schemes[id]
        const selected = id === value
        const chips = mode === 'both' ? (
          <span style={{ display: 'inline-flex', flexDirection: 'column', gap: 3, flexShrink: 0 }}>
            {chipRow(schemePalette(scheme, keyboard, false))}
            {chipRow(schemePalette(scheme, keyboard, true))}
          </span>
        ) : chipRow(schemePalette(scheme, keyboard, mode === 'dark'))
        return item(id, selected, () => onChange(id), chips, String(scheme?.name ?? id), id)
      })}
      {item(
        'dynamic',
        value === 'dynamic',
        () => onChange('dynamic'),
        dynamicChips,
        dynamicName,
        'dynamic',
      )}
    </div>
  )
}
