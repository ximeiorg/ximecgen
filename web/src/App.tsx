import { useState, useCallback, useEffect } from 'react'
import { YamlEditor } from './components/YamlEditor'
import { EditorPanel } from './components/EditorPanel'
import { PreviewPanel } from './components/PreviewPanel'

type EditorTab = 'raw' | 'form' | 'preview'

const DEFAULT_YAML = `metadata:
  app_name: Xime
  app_version: ">=2.5.0"
  platform: android
  config_version: 1
  modified_time: "2026-07-05"
`

export default function App() {
  const [yamlText, setYamlText] = useState(DEFAULT_YAML)
  const [tab, setTab] = useState<EditorTab>('form')
  const [configJson, setConfigJson] = useState<string>('')
  const [validation, setValidation] = useState<string>('')
  const [wasmReady, setWasmReady] = useState(false)

  useEffect(() => {
    async function loadWasm() {
      try {
        const wasm = await import('./pkg/xime_config_core')
        setWasmReady(true)
        ;(window as any).__xime_wasm = wasm
        console.log('WASM loaded')
      } catch (e) {
        console.warn('WASM not available, using mock mode', e)
        setWasmReady(true)
      }
    }
    loadWasm()
  }, [])

  const parseYaml = useCallback((yaml: string) => {
    const wasm = (window as any).__xime_wasm
    if (wasm?.parse) {
      const json = wasm.parse(yaml)
      setConfigJson(json)
      const result = wasm.validate(yaml)
      setValidation(result)
    } else {
      setConfigJson(JSON.stringify({ metadata: { app_name: 'Xime' } }))
      setValidation(JSON.stringify({ valid: true, errors: [], warnings: [] }))
    }
  }, [])

  useEffect(() => {
    if (wasmReady) {
      parseYaml(yamlText)
    }
  }, [yamlText, wasmReady, parseYaml])

  const handleYamlChange = useCallback((yaml: string) => {
    setYamlText(yaml)
  }, [])

  const handleFieldUpdate = useCallback((fieldPath: string, value: string) => {
    const wasm = (window as any).__xime_wasm
    if (wasm?.updateField) {
      const updated = wasm.updateField(configJson, fieldPath, value)
      const newConfig = JSON.parse(updated)
      const yaml = wasm.toYaml(JSON.stringify(newConfig))
      if (yaml) {
        setYamlText(yaml)
      }
    }
  }, [configJson])

  let validationResult: any = null
  try {
    validationResult = JSON.parse(validation)
  } catch {}

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column', background: '#1e1e2e', color: '#cdd6f4' }}>
      <header style={{ padding: '12px 20px', borderBottom: '1px solid #313244', display: 'flex', alignItems: 'center', gap: 16 }}>
        <h1 style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>Xime Config Generator</h1>
        <nav style={{ display: 'flex', gap: 4 }}>
          {(['form', 'raw', 'preview'] as EditorTab[]).map(t => (
            <button key={t} onClick={() => setTab(t)}
              style={{
                padding: '6px 14px', border: 'none', borderRadius: 6, cursor: 'pointer',
                background: tab === t ? '#45475a' : 'transparent',
                color: tab === t ? '#cdd6f4' : '#6c7086', fontSize: 13,
              }}>
              {t === 'form' ? 'Form' : t === 'raw' ? 'Raw YAML' : 'Preview'}
            </button>
          ))}
        </nav>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 12, alignItems: 'center', fontSize: 12 }}>
          {validationResult && (
            <span style={{ color: validationResult.valid ? '#a6e3a1' : '#f38ba8' }}>
              {validationResult.valid ? `✓ Valid` : `✗ ${validationResult.errors.length} error(s)`}
            </span>
          )}
        </div>
      </header>

      <main style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {tab === 'raw' && (
          <YamlEditor value={yamlText} onChange={handleYamlChange} />
        )}
        {tab === 'form' && (
          <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
            <div style={{ flex: 1, overflow: 'auto', padding: 16, borderRight: '1px solid #313244' }}>
              <EditorPanel configJson={configJson} onFieldUpdate={handleFieldUpdate} />
            </div>
            <div style={{ width: 400, overflow: 'auto', padding: 16 }}>
              <YamlEditor value={yamlText} onChange={handleYamlChange} />
            </div>
          </div>
        )}
        {tab === 'preview' && (
          <PreviewPanel yamlText={yamlText} />
        )}
      </main>
    </div>
  )
}
