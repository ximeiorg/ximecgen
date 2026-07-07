import { useMemo } from 'react'

interface Props {
  yamlText: string
}

export function PreviewPanel({ yamlText }: Props) {
  let config: any = {}
  let parseError = ''

  useMemo(() => {
    const wasm = (window as any).__xime_wasm
    if (wasm?.parse) {
      try {
        const json = wasm.parse(yamlText)
        config = JSON.parse(json)
        if (config.error) {
          parseError = config.error
          config = {}
        }
      } catch (e) {
        parseError = String(e)
      }
    }
  }, [yamlText])

  if (parseError) {
    return (
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#f38ba8' }}>
        Parse Error: {parseError}
      </div>
    )
  }

  return (
    <div style={{ flex: 1, overflow: 'auto', padding: 24 }}>
      <h2 style={{ margin: '0 0 24px', fontSize: 16, fontWeight: 600 }}>Preview</h2>

      <div style={{
        width: 360, margin: '0 auto', borderRadius: 16, overflow: 'hidden',
        boxShadow: '0 8px 32px rgba(0,0,0,0.3)',
      }}>
        {/* Keyboard Preview */}
        <div style={{ background: '#E3E4E8', padding: 16 }}>
          <div style={{
            display: 'grid', gridTemplateColumns: 'repeat(10, 1fr)', gap: 4, marginBottom: 4,
          }}>
            {'qwertyuiop'.split('').map(k => (
              <div key={k} style={{
                aspectRatio: '1', borderRadius: 8, background: '#FFFFFF',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 16, fontWeight: 500, color: '#202124',
                boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
              }}>{k}</div>
            ))}
          </div>
          <div style={{
            display: 'grid', gridTemplateColumns: 'repeat(10, 1fr)', gap: 4, marginBottom: 4,
          }}>
            {'asdfghjkl'.split('').map(k => (
              <div key={k} style={{
                aspectRatio: '1', borderRadius: 8, background: '#FFFFFF',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 16, fontWeight: 500, color: '#202124',
                boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
              }}>{k}</div>
            ))}
          </div>
          <div style={{
            display: 'grid', gridTemplateColumns: 'repeat(10, 1fr)', gap: 4,
          }}>
            {'zxcvbnm'.split('').map(k => (
              <div key={k} style={{
                aspectRatio: '1', borderRadius: 8, background: '#FFFFFF',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 16, fontWeight: 500, color: '#202124',
                boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
              }}>{k}</div>
            ))}
          </div>
        </div>

        {/* Info */}
        <div style={{ background: '#fff', padding: 16, fontSize: 13, color: '#202124' }}>
          <div>Keyboard preview (simplified)</div>
        </div>
      </div>
    </div>
  )
}
