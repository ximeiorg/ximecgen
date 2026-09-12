import { useMemo, useState } from 'react'
import { ops } from '../wasm'
import { SchemeRadioGroup } from './SchemeSwatches'
import { KeyboardGestureEditor } from './KeyboardGestureEditor'

interface FieldDescriptor {
  path: string
  label: string
  section: string
  fieldType: string
  description: string
  default: string
  options?: string[]
}

interface Props {
  config: any
  descriptors: FieldDescriptor[]
  onDispatch: (ops: unknown[]) => void
}

const SECTION_TITLES: Record<string, string> = {
  metadata: '元数据',
  style: '样式',
  keyboard_colors: '键盘颜色（全局后备）',
  keyboard_key: '按键形状',
  keyboard_shadow: '按键阴影',
  keyboard_fonts: '字体',
  keyboard_layout: '布局',
  color_scheme: '配色方案',
}

/** 分组 tab，对齐 Android 端四分区结构。 */
const TAB_GROUPS = [
  { id: 'general', title: '常规', sections: ['metadata', 'style'] },
  { id: 'colors', title: '主题配色', sections: ['color_scheme', 'keyboard_colors'] },
  { id: 'appearance', title: '键盘外观', sections: ['keyboard_key', 'keyboard_shadow', 'keyboard_fonts'] },
  { id: 'layout', title: '布局与手势', sections: ['keyboard_layout'] },
]

/** 按 JSON Pointer 取值。 */
function at(obj: any, pointer: string): any {
  if (!obj) return undefined
  const tokens = pointer.replace(/^\//, '').split('/')
  let cur: any = obj
  for (const t of tokens) {
    if (cur == null) return undefined
    cur = Array.isArray(cur) ? cur[Number(t)] : cur[t]
  }
  return cur
}

function parseColorNum(value: string): number | null {
  const t = value.trim().replace(/^#/, '').replace(/^0x/i, '')
  if (!/^[0-9a-fA-F]+$/.test(t) || t.length === 0 || t.length > 8) return null
  const n = parseInt(t, 16)
  return n > 0 ? n : null
}

/** 按字段类型把字符串输入类型化（与 Rust 校验规则对应）。 */
function coerce(fieldType: string, value: string): { value: unknown; remove: boolean } {
  switch (fieldType) {
    case 'number':
      if (value === '') return { value: null, remove: true }
      return { value: value.includes('.') ? Number(value) : Number.parseInt(value, 10), remove: false }
    case 'boolean':
      return { value: value === 'true', remove: false }
    case 'color': {
      const n = parseColorNum(value)
      return n === null ? { value, remove: false } : { value: n, remove: false }
    }
    default:
      return { value, remove: false }
  }
}

export function EditorPanel({ config, descriptors, onDispatch }: Props) {
  const [activeTab, setActiveTab] = useState('general')
  const [schemeId, setSchemeId] = useState('')

  const schemeIds: string[] = useMemo(
    () => (config?.color_schemes ? Object.keys(config.color_schemes).sort() : []),
    [config],
  )
  const effectiveScheme = schemeId || schemeIds[0] || ''

  const sections = useMemo(() => {
    const groups: Record<string, FieldDescriptor[]> = {}
    for (const f of descriptors) {
      ;(groups[f.section] ??= []).push(f)
    }
    return groups
  }, [descriptors])

  const renderField = (field: FieldDescriptor) => {
    const isSchemeField = field.path.includes('{id}')
    const path = field.path.replace('{id}', effectiveScheme)
    const raw = at(config, path)
    const displayValue = raw !== undefined && raw !== null ? String(raw) : field.default

    const commit = (text: string) => {
      if (isSchemeField && !effectiveScheme) return
      const { value, remove } = coerce(field.fieldType, text)
      onDispatch([remove ? ops.remove(path) : ops.set(path, value)])
    }

    switch (field.fieldType) {
      case 'select':
        return (
          <select value={displayValue} onChange={e => commit(e.target.value)} style={selectStyle}>
            {field.options?.map(o => <option key={o} value={o}>{o}</option>)}
          </select>
        )
      case 'boolean':
        return (
          <input
            type="checkbox"
            checked={raw === true || (raw === undefined && field.default === 'true')}
            onChange={e => commit(String(e.target.checked))}
          />
        )
      case 'color': {
        const num = typeof raw === 'number' ? raw : parseColorNum(displayValue)
        const hex = num !== null ? '#' + num.toString(16).padStart(8, '0').slice(-6) : '#8F73E2'
        const shown = typeof raw === 'number'
          ? '0x' + raw.toString(16).toUpperCase().padStart(6, '0')
          : displayValue
        return (
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <input
              type="color"
              value={hex}
              onChange={e => commit('0x' + e.target.value.replace('#', '').toUpperCase())}
              style={{ width: 36, height: 36, padding: 0, border: 'none', borderRadius: 6, cursor: 'pointer' }}
            />
            <input
              type="text"
              value={shown}
              onChange={e => commit(e.target.value)}
              style={{ ...inputStyle, width: 120 }}
            />
          </div>
        )
      }
      case 'string_list':
        return (
          <input
            type="text"
            placeholder="逗号分隔"
            value={displayValue}
            style={inputStyle}
            readOnly
            title="列表字段请在 Raw YAML 中编辑"
          />
        )
      default:
        return (
          <input
            type={field.fieldType === 'number' ? 'number' : 'text'}
            value={displayValue}
            onChange={e => commit(e.target.value)}
            style={inputStyle}
          />
        )
    }
  }

  if (!config) {
    return <div style={{ color: '#6c7086' }}>加载中…</div>
  }

  const group = TAB_GROUPS.find(g => g.id === activeTab) ?? TAB_GROUPS[0]

  return (
    <div>
      <h2 style={{ margin: '0 0 12px', fontSize: 16, fontWeight: 600 }}>Visual Editor</h2>

      {/* 分组 tab（对齐 Android 端四分区） */}
      <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid #313244', marginBottom: 16 }}>
        {TAB_GROUPS.map(g => (
          <button
            key={g.id}
            onClick={() => setActiveTab(g.id)}
            style={{
              padding: '8px 14px', border: 'none', cursor: 'pointer', fontSize: 13,
              background: 'transparent',
              color: g.id === activeTab ? '#cdd6f4' : '#6c7086',
              fontWeight: g.id === activeTab ? 600 : 400,
              borderBottom: g.id === activeTab ? '2px solid #89b4fa' : '2px solid transparent',
            }}
          >
            {g.title}
          </button>
        ))}
      </div>

      {group.sections.map(section => {
        const fields = sections[section]
        if (!fields || fields.length === 0) return null
        const isSchemeSection = section === 'color_scheme'
        if (isSchemeSection && !effectiveScheme) return null
        return (
          <div key={section} style={{ marginBottom: 16 }}>
            {isSchemeSection && (
              <div style={{ padding: '8px 12px' }}>
                <div style={{ fontSize: 12, color: '#6c7086', marginBottom: 8 }}>选择要编辑的方案（色块为该方案浅色/深色的实际颜色）</div>
                <SchemeRadioGroup config={config} value={effectiveScheme} onChange={setSchemeId} />
                <div style={{ fontSize: 11, color: '#6c7086', marginTop: 8 }}>背景配置（渐变/图片）请在 Raw YAML 编辑</div>
              </div>
            )}
            {section === 'keyboard_layout' && (
              <div style={{ padding: '8px 12px 0' }}>
                <KeyboardGestureEditor config={config} onDispatch={onDispatch} />
              </div>
            )}
            <div style={{
              padding: '8px 12px', fontSize: 13, fontWeight: 600,
              background: '#313244', color: '#cdd6f4',
              borderRadius: '6px 6px 0 0',
            }}>
              {SECTION_TITLES[section] ?? section}
            </div>
            <div style={{ padding: '8px 0', border: '1px solid #313244', borderTop: 'none', borderRadius: '0 0 6px 6px' }}>
              {fields
                .filter(f => !isSchemeSection || !f.path.endsWith('/name') || effectiveScheme)
                .map(field => {
                  // 浅色/深色主题：radio 单选（色块 + 中文名），不用文本框。
                  // color_scheme 支持标量（单方案双模式）与 {light, dark} 对象双写，
                  // 标量时两个组都反映当前值；点击任一组即转为对象写法。
                  const isSchemeLight = field.path === '/style/color_scheme/light'
                  const isSchemeDark = field.path === '/style/color_scheme/dark'
                  if (isSchemeLight || isSchemeDark) {
                    const cs = config?.style?.color_scheme
                    const scalar = typeof cs === 'string' ? cs : ''
                    const lightVal = scalar || String(cs?.light ?? '')
                    const darkVal = scalar || String(cs?.dark ?? '')
                    const setScheme = (id: string) => {
                      if (scalar) {
                        onDispatch([ops.set('/style/color_scheme', isSchemeLight ? { light: id, dark: scalar } : { light: scalar, dark: id })])
                      } else {
                        onDispatch([ops.set(field.path, id)])
                      }
                    }
                    return (
                      <div key={field.path} style={{ padding: '8px 12px' }}>
                        <div style={{ fontSize: 13, marginBottom: 6 }}>{field.label}</div>
                        <SchemeRadioGroup
                          config={config}
                          mode={isSchemeLight ? 'light' : 'dark'}
                          value={isSchemeLight ? lightVal : darkVal}
                          onChange={setScheme}
                        />
                      </div>
                    )
                  }
                  return (
                    <div key={field.path} style={{ padding: '6px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ fontSize: 13 }}>{field.label}</div>
                        <div style={{ fontSize: 11, color: '#6c7086' }}>{field.description}</div>
                      </div>
                      {renderField(field)}
                    </div>
                  )
                })}
            </div>
          </div>
        )
      })}
    </div>
  )
}

const inputStyle: React.CSSProperties = {
  padding: '6px 10px', border: '1px solid #45475a', borderRadius: 6,
  background: '#1e1e2e', color: '#cdd6f4', fontSize: 13,
  outline: 'none', width: 160,
}

const selectStyle: React.CSSProperties = {
  ...inputStyle,
  appearance: 'auto',
}
