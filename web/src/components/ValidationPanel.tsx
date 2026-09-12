import { useState } from 'react'

interface Props {
  validation: { valid: boolean; errors: any[]; warnings: string[] }
}

/** 校验结果面板：错误（路径+信息）与警告列表，可折叠。 */
export function ValidationPanel({ validation }: Props) {
  const [open, setOpen] = useState(false)
  const errors: any[] = validation?.errors ?? []
  const warnings: string[] = validation?.warnings ?? []
  if (errors.length === 0 && warnings.length === 0) return null

  return (
    <div style={{ margin: '0 0 12px', border: '1px solid #313244', borderRadius: 8, overflow: 'hidden' }}>
      <button
        onClick={() => setOpen(o => !o)}
        style={{
          width: '100%', display: 'flex', alignItems: 'center', gap: 8,
          padding: '8px 12px', border: 'none', cursor: 'pointer',
          background: '#1e1e2e', color: '#cdd6f4', fontSize: 13,
        }}
      >
        <span style={{ color: errors.length ? '#f38ba8' : '#f9e2af' }}>
          {errors.length ? `✗ ${errors.length} 个错误` : '⚠'}{errors.length && warnings.length ? '，' : ''}{warnings.length ? `${warnings.length} 条提示` : ''}
        </span>
        <span style={{ marginLeft: 'auto', color: '#6c7086' }}>{open ? '▾' : '▸'}</span>
      </button>
      {open && (
        <div style={{ padding: '4px 0', maxHeight: 260, overflow: 'auto' }}>
          {errors.map((e, i) => (
            <div key={`e${i}`} style={{ padding: '4px 12px', fontSize: 12 }}>
              <span style={{ color: '#f38ba8' }}>✗</span>{' '}
              <code style={{ color: '#89b4fa' }}>{e.path}</code> {e.message}
            </div>
          ))}
          {warnings.map((w, i) => (
            <div key={`w${i}`} style={{ padding: '4px 12px', fontSize: 12, color: '#f9e2af' }}>⚠ {w}</div>
          ))}
        </div>
      )}
    </div>
  )
}
