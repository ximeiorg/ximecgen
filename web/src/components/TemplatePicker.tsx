import type { TemplateEntry } from '../data/templates'

interface Props {
  open: boolean
  templates: TemplateEntry[] | null
  loadingId: string | null
  /** 拉取/解析失败信息（显示在弹窗内，因为 Raw 页错误条在 Form 页不可见） */
  error: string | null
  onClose: () => void
  onPick: (entry: TemplateEntry) => void
}

/** 模板选择弹窗：与 Android 首页模板目录一致（jsDelivr 拉取，无兜底）。 */
export function TemplatePicker({ open, templates, loadingId, error, onClose, onPick }: Props) {
  if (!open) return null
  const list = templates ?? []

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
        {templates === null ? (
          <div style={{ color: '#6c7086', fontSize: 13, padding: '12px 0' }}>模板目录加载中…</div>
        ) : list.length === 0 ? (
          <div style={{ color: '#f38ba8', fontSize: 13, padding: '12px 0' }}>
            模板目录拉取失败，请检查网络后重试
          </div>
        ) : (
          list.map(t => (
            <button
              key={t.id}
              disabled={loadingId !== null}
              onClick={() => onPick(t)}
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
          ))
        )}
        {error && (
          <div style={{ fontSize: 12, color: '#f38ba8', marginBottom: 8 }}>✗ {error}</div>
        )}
        <div style={{ fontSize: 11, color: '#6c7086' }}>
          目录来自 index.ximei.me 布局子索引，模板文件按索引给出的 CDN 地址拉取。
        </div>
      </div>
    </div>
  )
}

const btnStyle: React.CSSProperties = {
  padding: '4px 12px', border: '1px solid #45475a', borderRadius: 6,
  background: 'transparent', color: '#cdd6f4', cursor: 'pointer', fontSize: 12,
}
