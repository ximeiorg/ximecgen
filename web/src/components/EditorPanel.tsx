import { useState, useMemo } from 'react'

interface FieldDescriptor {
  path: string
  label: string
  fieldType: string
  description: string
  default: string
  options?: string[]
}

interface Props {
  configJson: string
  onFieldUpdate: (path: string, value: string) => void
}

function getNestedValue(obj: any, path: string): any {
  return path.split('.').reduce((acc, part) => acc?.[part], obj)
}

export function EditorPanel({ configJson, onFieldUpdate }: Props) {
  const [expanded, setExpanded] = useState<Record<string, boolean>>({
    metadata: true,
    keyboard: true,
  })

  let config: any = {}
  try { config = JSON.parse(configJson) } catch {}

  const fields = useMemo(() => {
    const wasm = (window as any).__xime_wasm
    if (wasm?.getFieldDescriptors) {
      try { return JSON.parse(wasm.getFieldDescriptors()) as FieldDescriptor[] } catch {}
    }
    return []
  }, [])

  const groupedFields = useMemo(() => {
    const groups: Record<string, FieldDescriptor[]> = {}
    for (const f of fields) {
      const parts = f.path.split('.')
      const group = parts.length > 1 ? parts[0] : 'general'
      if (!groups[group]) groups[group] = []
      groups[group].push(f)
    }
    return groups
  }, [fields])

  const renderField = (field: FieldDescriptor) => {
    const parts = field.path.split('.')
    const lastKey = parts[parts.length - 1]
    const value = getNestedValue(config, field.path)
    const displayValue = value !== undefined && value !== null ? String(value) : field.default

    switch (field.fieldType) {
      case 'select':
        return (
          <select
            value={displayValue}
            onChange={e => onFieldUpdate(field.path, e.target.value)}
            style={selectStyle}
          >
            {field.options?.map(opt => (
              <option key={opt} value={opt}>{opt}</option>
            ))}
          </select>
        )
      case 'boolean':
        return (
          <input
            type="checkbox"
            checked={displayValue === 'true'}
            onChange={e => onFieldUpdate(field.path, String(e.target.checked))}
          />
        )
      case 'color':
        return (
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <input
              type="color"
              value={colorToHex(displayValue)}
              onChange={e => onFieldUpdate(field.path, hexToColor(e.target.value))}
              style={{ width: 36, height: 36, padding: 0, border: 'none', borderRadius: 6, cursor: 'pointer' }}
            />
            <input
              type="text"
              value={displayValue}
              onChange={e => onFieldUpdate(field.path, e.target.value)}
              style={{ ...inputStyle, width: 120 }}
            />
          </div>
        )
      default:
        return (
          <input
            type={field.fieldType === 'number' ? 'number' : 'text'}
            value={displayValue}
            onChange={e => onFieldUpdate(field.path, e.target.value)}
            style={inputStyle}
          />
        )
    }
  }

  return (
    <div>
      <h2 style={{ margin: '0 0 16px', fontSize: 16, fontWeight: 600 }}>Visual Editor</h2>

      {Object.entries(groupedFields).map(([group, groupFields]) => (
        <div key={group} style={{ marginBottom: 16 }}>
          <button
            onClick={() => setExpanded(prev => ({ ...prev, [group]: !prev[group] }))}
            style={{
              width: '100%', textAlign: 'left', padding: '8px 12px',
              border: 'none', borderRadius: 6, cursor: 'pointer',
              background: '#313244', color: '#cdd6f4', fontSize: 13, fontWeight: 600,
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            }}
          >
            {group}
            <span>{expanded[group] ? '▾' : '▸'}</span>
          </button>
          {expanded[group] && (
            <div style={{ padding: '8px 0' }}>
              {groupFields.map(field => (
                <div key={field.path} style={{ padding: '6px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontSize: 13 }}>{field.label}</div>
                    <div style={{ fontSize: 11, color: '#6c7086' }}>{field.description}</div>
                  </div>
                  {renderField(field)}
                </div>
              ))}
            </div>
          )}
        </div>
      ))}
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

function colorToHex(val: string): string {
  if (val.startsWith('0x')) {
    const num = parseInt(val, 16)
    return '#' + num.toString(16).padStart(6, '0')
  }
  if (val.startsWith('#')) return val
  return '#8F73E2'
}

function hexToColor(hex: string): string {
  const num = parseInt(hex.replace('#', ''), 16)
  return '0x' + num.toString(16).toUpperCase()
}
