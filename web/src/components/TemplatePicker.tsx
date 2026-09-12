import { CATALOG, type TemplateEntry } from '../data/templates'

interface Props {
  open: boolean
  loadingId: string | null
  onClose: () => void
  onPick: (id: string) => void
}

/** 模板选择弹窗：与 Android 首页模板目录一致（jsDelivr 拉取，无兜底）。 */
export function TemplatePicker({ open, loadingId, onClose, onPick }: Props) {
  if (!open) return null
  return (
    <div
      onClick={onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)', zIndex: 100,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}
    >
      <div
        onClick={e => e.stopPropagation()}
        style={{
          width: 480, maxHeight: '80vh', overflow: 'auto',
          background: '#181825', border: '1px solid #313244', borderRadius: 12, padding: 16,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12 }}>
          <h3 style={{ margin: 0, fontSize: 15 }}>常用配置模板</h3>
          <button onClick={onClose} style={{ ...btnStyle, marginLeft: 'auto' }}>关闭</button>
        </div>
        {CATALOG.map((t: TemplateEntry) => (
          <button
            key={t.id}
            disabled={loadingId !== null}
            onClick={() => onPick(t.id)}
            style={{
              display: 'flex', alignItems: 'center', gap: 10, width: '100%', textAlign: 'left',
              padding: '10px 12px', marginBottom: 8, borderRadius: 8, cursor: 'pointer',
              border: '1px solid #313244', background: '#1e1e2e', color: '#cdd6f4',
              opacity: loadingId !== null && loadingId !== t.id ? 0.5 : 1,
            }}
          >
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>{t.title}</div>
              <div style={{ fontSize: 12, color: '#6c7086' }}>{t.description}</div>
            </div>
            {loadingId === t.id && <span style={{ color: '#89b4fa', fontSize: 12 }}>拉取中…</span>}
          </button>
        ))}
        <div style={{ fontSize: 11, color: '#6c7086' }}>
          模板来自 xime 官方仓库（jsDelivr CDN），拉取失败请检查网络。
        </div>
      </div>
    </div>
  )
}

const btnStyle: React.CSSProperties = {
  padding: '4px 12px', border: '1px solid #45475a', borderRadius: 6,
  background: 'transparent', color: '#cdd6f4', cursor: 'pointer', fontSize: 12,
}
