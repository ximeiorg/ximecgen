import { useState } from 'react'
import { ops } from '../wasm'

/** Xime 3.0 支持的手势动作（与 Android 端 GESTURE_ACTIONS / validator 一致）。 */
const GESTURE_ACTIONS = [
  'commit', 'send_rime', 'command', 'select_all', 'copy', 'cut', 'paste',
  'line_start', 'line_end', 'undo', 'none', 'repeat',
  'switch_route', 'toggle_ascii', 'delete', 'toggle_symbols',
  'enter', 'newline', 'space', 'repeat_space',
  'clear_all', 'undo_clear', 'toggle_shift', 'voice',
]
/** 不需要 value 参数的动作。 */
const NO_VALUE_ACTIONS = new Set([
  'select_all', 'copy', 'cut', 'paste', 'line_start', 'line_end', 'undo', 'none',
  'repeat', 'toggle_ascii', 'delete', 'toggle_symbols', 'enter', 'newline',
  'space', 'clear_all', 'undo_clear', 'toggle_shift', 'voice',
])
const COMMAND_VALUES = [
  'clear_composition', 'show_ime_picker',
  'shift_single', 'shift_caps', 'toggle_shift',
  'mode_change', 'mode_change_number', 'mode_change_common_symbol',
]
const SWITCH_ROUTE_VALUES = ['emoji', 'symbol', 'clipboard']
const DISPLAY_MODES = ['key', 'bubble', 'both']

/** 可被 layout.rows 引用的功能键 id（对齐 Xime KeysConfigHelper.FUNCTION_KEY_IDS）。 */
const FUNCTION_KEY_IDS = new Set([
  'shift', 'delete', 'enter', 'space', 'mode_change', 'symbol', 'emoji', 'earth', 'voice', 'comma',
])
/** 无 layout.rows 时的兜底行 = 内置 xime.yaml 的行布局（Xime 回退链：custom rows →
    内置 rows（含 shift/delete）→ 裸字母行）。模板类 custom yaml 不写 rows，实际渲染带功能键。 */
const DEFAULT_ROW_IDS = [
  ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
  ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
  ['shift', 'z', 'x', 'c', 'v', 'b', 'n', 'm', 'delete'],
  ['mode_change', 'comma', 'space', 'earth', 'enter'],
]

interface Props {
  config: any
  onDispatch: (ops: unknown[]) => void
}

/** 键位 id → 键面显示（功能键用内置图标/标签，可被 keys.<id>.tap 覆盖；单字母大写）。 */
function capLabel(keys: any, id: string): string {
  const binding = keys?.[id]
  const tap = binding?.tap
  const field = (f: string): string | null => {
    if (!tap || typeof tap !== 'object') return null
    const v = tap[f]
    if (typeof v === 'string' && v.trim()) return v.trim()
    if (Array.isArray(v)) {
      const s = v.filter(Boolean).join(' ').trim()
      return s || null
    }
    return null
  }
  if (FUNCTION_KEY_IDS.has(id)) {
    const label = field('label')
    const value = field('value')
    switch (id) {
      case 'shift': return '⇧'
      case 'delete': return '⌫'
      case 'mode_change': return label ?? '?123'
      case 'enter': return label ?? '换行'
      case 'comma': return value ?? label ?? '，'
      case 'earth': return label && !label.startsWith('@') ? label : '中'
      case 'space': return label ?? '空格'
      case 'symbol': return label ?? '⌨'
      case 'emoji': return label ?? '表情'
      case 'voice': return label ?? '语音'
    }
  }
  let main: string
  if (typeof tap === 'string') {
    main = tap
  } else if (tap && typeof tap === 'object') {
    // label 支持数组（多行显示）；{use: 预设名} 引用无 label/value，键面显示键名
    main = Array.isArray(tap.label) ? tap.label.filter(Boolean).join('·') : String(tap.label ?? tap.value ?? '')
  } else {
    main = ''
  }
  if (!main) main = id
  if (main.startsWith('@')) main = main.slice(1)
  if (/[a-z]/i.test(main)) main = main.toUpperCase()
  return main
}

/** 单个手势编辑区：简单文本 ↔ 结构化 {label, action, value, display, bubble}。 */
function GestureEditor({ name, initial, onChange }: { name: string; initial: any; onChange: (v: any | null) => void }) {
  const obj = initial && typeof initial === 'object' ? initial : {}
  const label = typeof initial === 'string' ? initial : String(obj.label ?? '')
  const action = String(obj.action ?? '')
  const value = String(obj.value ?? '')
  const display = String(obj.display ?? '')
  const bubble: boolean | null = obj.bubble === true ? true : obj.bubble === false ? false : null

  const emit = (nl: string, na: string, nv: string, nd: string, nb: boolean | null = bubble) => {
    const clean: Record<string, unknown> = {}
    if (nl) clean.label = nl
    if (na) clean.action = na
    if (nv) clean.value = nv
    if (nd) clean.display = nd
    if (nb !== null) clean.bubble = nb
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
          <select value={action} onChange={e => emit(label, e.target.value, e.target.value === 'command' ? COMMAND_VALUES[0] : e.target.value === 'switch_route' ? SWITCH_ROUTE_VALUES[0] : '', display)} style={inp}>
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
        ) : action === 'switch_route' ? (
          <label style={lbl}>面板
            <select value={value || SWITCH_ROUTE_VALUES[0]} onChange={e => emit(label, action, e.target.value, display)} style={inp}>
              {SWITCH_ROUTE_VALUES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </label>
        ) : action && !NO_VALUE_ACTIONS.has(action) ? (
          <label style={lbl}>上屏值
            <input value={value} placeholder={action === 'repeat_space' ? '次数（默认 5）' : '留空用显示文本'} onChange={e => emit(label, action, e.target.value, display)} style={inp} />
          </label>
        ) : null}
        <label style={lbl}>显示方式
          <select value={display} onChange={e => emit(label, action, value, e.target.value)} style={inp}>
            <option value="">（默认）</option>
            {DISPLAY_MODES.map(d => <option key={d} value={d}>{d === 'key' ? 'key（印在键面）' : d === 'bubble' ? 'bubble（气泡）' : 'both'}</option>)}
          </select>
        </label>
        <label style={lbl}>气泡
          <select value={bubble === null ? '' : String(bubble)} onChange={e => emit(label, action, value, display, e.target.value === '' ? null : e.target.value === 'true')} style={inp}>
            <option value="">（默认弹）</option>
            <option value="true">弹气泡</option>
            <option value="false">不弹</option>
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
  // 展示与预览同源：缺失行补内置默认（含控制行）；编辑操作写回配置
  const rows: (string | string[])[][] = Array.isArray(configured)
    ? configured.slice(0, 5).map((r: unknown) => (Array.isArray(r) ? r : []))
    : DEFAULT_ROW_IDS

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
    // 新行用内置默认模板补齐（空行会被输入法截断）
    const template = DEFAULT_ROW_IDS[rows.length] ?? DEFAULT_ROW_IDS[DEFAULT_ROW_IDS.length - 1]
    onDispatch([ops.set(rowsPath, [...rows, [...template]])])
  }

  const selId = selected
  const selBinding = selId ? keys[selId] : undefined

  return (
    <div>
      <div style={{ fontSize: 12, color: '#6c7086', marginBottom: 8 }}>
        点击键位编辑手势（点按/双击/四向滑动/长按）——行内可移动/移除键位，最多 5 行；合并键（[[q,w]]）显示为 q·w。
      </div>

      {/* 键位布局 */}
      {rows.map((row, ri) => (
        <div key={ri} style={{ display: 'flex', flexWrap: 'wrap', gap: 4, alignItems: 'center', marginBottom: 6 }}>
          {row.map((id, ki) => {
            const merged = Array.isArray(id) ? id.map(String) : null
            const flatId = merged ? merged.join('') : String(id)
            return (
            <span key={flatId + ki} style={{ position: 'relative', display: 'inline-flex' }}>
              <button
                onClick={() => setSelected(selected === flatId ? null : flatId)}
                style={{
                  minWidth: 34, height: 34, padding: '0 6px', borderRadius: 6, cursor: 'pointer', fontSize: 13,
                  border: selected === flatId ? '2px solid #89b4fa' : '1px solid #45475a',
                  background: selected === flatId ? '#313244' : '#1e1e2e', color: '#cdd6f4',
                }}
              >
                {merged ? merged.map(m => capLabel(keys, m)).join('·') : capLabel(keys, flatId)}
              </button>
              {selected === flatId && (
                <span style={{ position: 'absolute', top: -8, right: -8, display: 'flex', gap: 2 }}>
                  <button title="左移" onClick={() => moveKey(ri, ki, -1)} style={miniBtn('⇐')} disabled={ki === 0}>←</button>
                  <button title="右移" onClick={() => moveKey(ri, ki, 1)} style={miniBtn('⇐')} disabled={ki === row.length - 1}>→</button>
                  <button title="移除" onClick={() => removeKey(ri, ki)} style={{ ...miniBtn('x'), color: '#f38ba8' }}>✕</button>
                </span>
              )}
            </span>
            )
          })}
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
          <GestureEditor name="双击 double_tap" initial={selBinding?.double_tap} onChange={v => onDispatch([v === null ? ops.remove(`${keysPath}/${selId}/double_tap`) : ops.set(`${keysPath}/${selId}/double_tap`, v)])} />
          <GestureEditor name="上滑 swipe_up" initial={gesture(selId, 'swipe_up')} onChange={v => onDispatch([v === null ? ops.remove(`${keysPath}/${selId}/swipe_up`) : ops.set(`${keysPath}/${selId}/swipe_up`, v)])} />
          <GestureEditor name="下滑 swipe_down" initial={gesture(selId, 'swipe_down')} onChange={v => onDispatch([v === null ? ops.remove(`${keysPath}/${selId}/swipe_down`) : ops.set(`${keysPath}/${selId}/swipe_down`, v)])} />
          <GestureEditor name="左滑 swipe_left（接管光标移动）" initial={gesture(selId, 'swipe_left')} onChange={v => onDispatch([v === null ? ops.remove(`${keysPath}/${selId}/swipe_left`) : ops.set(`${keysPath}/${selId}/swipe_left`, v)])} />
          <GestureEditor name="右滑 swipe_right（接管光标移动）" initial={gesture(selId, 'swipe_right')} onChange={v => onDispatch([v === null ? ops.remove(`${keysPath}/${selId}/swipe_right`) : ops.set(`${keysPath}/${selId}/swipe_right`, v)])} />
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
