import { useState } from 'react'
import { ops } from '../wasm'

/** Xime 支持的手势动作（与 Android 端 GESTURE_ACTIONS / validator 一致）。 */
const GESTURE_ACTIONS = [
  'commit', 'command', 'select_all', 'copy', 'cut', 'paste',
  'line_start', 'line_end', 'undo', 'none', 'repeat',
  'switch_route', 'toggle_ascii', 'delete', 'toggle_symbols',
]
const COMMAND_VALUES = ['clear_composition', 'show_ime_picker']
const DISPLAY_MODES = ['key', 'bubble', 'both']

const DEFAULT_ROWS = [
  ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
  ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
  ['z', 'x', 'c', 'v', 'b', 'n', 'm'],
]

interface Props {
  config: any
  onDispatch: (ops: unknown[]) => void
}

/** 键位 id → 键面显示（单字母大写；对象 tap 取 label/value；@ 前缀为图标）。 */
function capLabel(keys: any, id: string): string {
  const binding = keys?.[id]
  const tap = binding?.tap
  let main = typeof tap === 'string' ? tap : tap && typeof tap === 'object' ? String(tap.label ?? tap.value ?? '') : ''
  if (!main) main = id
  if (main.startsWith('@')) main = main.slice(1)
  if (main.length === 1 && /[a-z]/.test(main)) main = main.toUpperCase()
  return main
}

/** 单个手势编辑区：简单文本 ↔ 结构化 {label, action, value, display}。 */
function GestureEditor({ name, initial, onChange }: { name: string; initial: any; onChange: (v: any | null) => void }) {
  const obj = initial && typeof initial === 'object' ? initial : {}
  const label = typeof initial === 'string' ? initial : String(obj.label ?? '')
  const action = String(obj.action ?? '')
  const value = String(obj.value ?? '')
  const display = String(obj.display ?? '')

  const emit = (nl: string, na: string, nv: string, nd: string) => {
    const clean: Record<string, string> = {}
    if (nl) (clean as any).label = nl
    if (na) (clean as any).action = na
    if (nv) (clean as any).value = nv
    if (nd) (clean as any).display = nd
    const keys = Object.keys(clean)
    if (keys.length === 0) onChange(null)
    else if (keys.length === 1 && keys[0] === 'label') onChange(nl) // 纯文本手势存字符串
    else onChange(clean)
  }

  return (
    <div style={{ border: '1px solid #313244', borderRadius: 8, padding: '8px 10px', marginBottom: 8 }}>
      <div style={{ fontSize: 12, fontWeight: 600, color: '#89b4fa', marginBottom: 6 }}>{name}</div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
        <label style={lbl}>显示
          <input value={label} placeholder="键面/气泡文字" onChange={e => emit(e.target.value, action, value, display)} style={inp} />
        </label>
        <label style={lbl}>动作
          <select value={action} onChange={e => emit(label, e.target.value, e.target.value === 'command' ? COMMAND_VALUES[0] : '', display)} style={inp}>
            <option value="">（无）</option>
            {GESTURE_ACTIONS.map(a => <option key={a} value={a}>{a}</option>)}
          </select>
        </label>
        {action === 'command' ? (
          <label style={lbl}>命令
            <select value={value || COMMAND_VALUES[0]} onChange={e => emit(label, action, e.target.value, display)} style={inp}>
              {COMMAND_VALUES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </label>
        ) : action ? (
          <label style={lbl}>上屏值
            <input value={value} onChange={e => emit(label, action, e.target.value, display)} style={inp} />
          </label>
        ) : null}
        <label style={lbl}>显示方式
          <select value={display} onChange={e => emit(label, action, value, e.target.value)} style={inp}>
            <option value="">（默认）</option>
            {DISPLAY_MODES.map(d => <option key={d} value={d}>{d === 'key' ? 'key（印在键面）' : d === 'bubble' ? 'bubble（气泡）' : 'both'}</option>)}
          </select>
        </label>
      </div>
    </div>
  )
}

export function KeyboardGestureEditor({ config, onDispatch }: Props) {
  const [selected, setSelected] = useState<string | null>(null)
  const [addingTo, setAddingTo] = useState<number | null>(null)
  const [newKeyLabel, setNewKeyLabel] = useState('')

  const qwerty = config?.keyboard?.qwerty ?? {}
  const keys = qwerty.keys ?? {}
  const configured = qwerty.layout?.rows
  const rows: string[][] = Array.isArray(configured)
    ? configured.slice(0, 5).map((r: unknown) => (Array.isArray(r) ? r.map(String) : []))
    : DEFAULT_ROWS

  const keysPath = '/keyboard/qwerty/keys'
  const rowsPath = '/keyboard/qwerty/layout/rows'

  const gesture = (id: string, g: string) => keys?.[id]?.[g]

  const moveKey = (ri: number, ki: number, dir: -1 | 1) => {
    const next = rows.map(r => [...r])
    const row = next[ri]
    const tj = ki + dir
    if (tj < 0 || tj >= row.length) return
    ;[row[ki], row[tj]] = [row[tj], row[ki]]
    onDispatch([ops.set(rowsPath, next)])
  }

  const removeKey = (ri: number, ki: number) => {
    const next = rows.map(r => [...r])
    next[ri].splice(ki, 1)
    onDispatch([ops.set(rowsPath, next)])
    setSelected(null)
  }

  const appendKey = (ri: number) => {
    const id = newKeyLabel.trim() || `key_${Date.now() % 1000}`
    const next = rows.map(r => [...r])
    next[ri].push(id)
    const batch = [ops.set(rowsPath, next)]
    if (!keys[id]) batch.push(ops.set(`${keysPath}/${id}`, { tap: id }))
    onDispatch(batch)
    setAddingTo(null)
    setNewKeyLabel('')
  }

  const addRow = () => {
    onDispatch([ops.set(rowsPath, [...rows, []])])
  }

  const selId = selected
  const selBinding = selId ? keys[selId] : undefined

  return (
    <div>
      <div style={{ fontSize: 12, color: '#6c7086', marginBottom: 8 }}>
        点击键位编辑手势（点按/上滑/下滑/长按）——上滑下滑选 bubble 时不在键面直显；行内可移动/移除键位。
      </div>

      {/* 键位布局 */}
      {rows.map((row, ri) => (
        <div key={ri} style={{ display: 'flex', flexWrap: 'wrap', gap: 4, alignItems: 'center', marginBottom: 6 }}>
          {row.map((id, ki) => (
            <span key={id + ki} style={{ position: 'relative', display: 'inline-flex' }}>
              <button
                onClick={() => setSelected(selected === id ? null : id)}
                style={{
                  minWidth: 34, height: 34, padding: '0 6px', borderRadius: 6, cursor: 'pointer', fontSize: 13,
                  border: selected === id ? '2px solid #89b4fa' : '1px solid #45475a',
                  background: selected === id ? '#313244' : '#1e1e2e', color: '#cdd6f4',
                }}
              >
                {capLabel(keys, id)}
              </button>
              {selected === id && (
                <span style={{ position: 'absolute', top: -8, right: -8, display: 'flex', gap: 2 }}>
                  <button title="左移" onClick={() => moveKey(ri, ki, -1)} style={miniBtn('⇐')} disabled={ki === 0}>←</button>
                  <button title="右移" onClick={() => moveKey(ri, ki, 1)} style={miniBtn('⇐')} disabled={ki === row.length - 1}>→</button>
                  <button title="移除" onClick={() => removeKey(ri, ki)} style={{ ...miniBtn('x'), color: '#f38ba8' }}>✕</button>
                </span>
              )}
            </span>
          ))}
          {addingTo === ri ? (
            <span style={{ display: 'inline-flex', gap: 4 }}>
              <input
                autoFocus value={newKeyLabel} placeholder="键位 id"
                onChange={e => setNewKeyLabel(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') appendKey(ri) }}
                style={{ ...inp, width: 80 }}
              />
              <button onClick={() => appendKey(ri)} style={miniBtn('ok')}>添加</button>
              <button onClick={() => setAddingTo(null)} style={miniBtn('x')}>取消</button>
            </span>
          ) : (
            <button onClick={() => { setAddingTo(ri); setNewKeyLabel('') }} style={miniBtn('plus')}>＋键</button>
          )}
        </div>
      ))}
      {rows.length < 5 && <button onClick={addRow} style={{ ...miniBtn('plus'), marginBottom: 12 }}>＋ 添加行</button>}

      {/* 选中键位的手势编辑 */}
      {selId && (
        <div style={{ border: '1px solid #45475a', borderRadius: 10, padding: 12, marginTop: 8 }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
            键位 <span style={{ color: '#89b4fa' }}>{capLabel(keys, selId)}</span>
            <span style={{ fontSize: 11, color: '#6c7086', marginLeft: 8 }}>({selId})</span>
            <button onClick={() => setSelected(null)} style={{ ...miniBtn('x'), float: 'right' }}>收起</button>
          </div>
          <GestureEditor name="点按 tap" initial={selBinding?.tap} onChange={v => onDispatch([v === null ? ops.remove(`${keysPath}/${selId}/tap`) : ops.set(`${keysPath}/${selId}/tap`, v)])} />
          <GestureEditor name="上滑 swipe_up" initial={gesture(selId, 'swipe_up')} onChange={v => onDispatch([v === null ? ops.remove(`${keysPath}/${selId}/swipe_up`) : ops.set(`${keysPath}/${selId}/swipe_up`, v)])} />
          <GestureEditor name="下滑 swipe_down" initial={gesture(selId, 'swipe_down')} onChange={v => onDispatch([v === null ? ops.remove(`${keysPath}/${selId}/swipe_down`) : ops.set(`${keysPath}/${selId}/swipe_down`, v)])} />
          <div style={{ marginTop: 4 }}>
            <label style={{ ...lbl, alignItems: 'center' }}>长按 long_press（逗号分隔，最多 10 项）
              <input
                value={longPressText(selBinding?.long_press)}
                onChange={e => {
                  const items = e.target.value.split(/[,，]/).map(s => s.trim()).filter(Boolean)
                  onDispatch([items.length ? ops.set(`${keysPath}/${selId}/long_press`, items) : ops.remove(`${keysPath}/${selId}/long_press`)])
                }}
                style={{ ...inp, width: 260 }}
              />
            </label>
          </div>
        </div>
      )}
    </div>
  )
}

function longPressText(v: unknown): string {
  if (Array.isArray(v)) return v.map(i => (typeof i === 'string' ? i : String((i as any)?.label ?? ''))).join('，')
  if (typeof v === 'string') return v
  return ''
}

function miniBtn(_k: string): React.CSSProperties {
  return {
    padding: '2px 8px', border: '1px solid #45475a', borderRadius: 5,
    background: '#1e1e2e', color: '#cdd6f4', cursor: 'pointer', fontSize: 12,
  }
}

const lbl: React.CSSProperties = {
  display: 'flex', flexDirection: 'column', gap: 2, fontSize: 11, color: '#6c7086',
}

const inp: React.CSSProperties = {
  padding: '4px 8px', border: '1px solid #45475a', borderRadius: 5,
  background: '#181825', color: '#cdd6f4', fontSize: 12, outline: 'none',
}
