import { useState, useCallback, useEffect, useMemo, useRef } from 'react'
import { YamlEditor } from './components/YamlEditor'
import { EditorPanel } from './components/EditorPanel'
import { PreviewPanel } from './components/PreviewPanel'
import { TemplatePicker } from './components/TemplatePicker'
import { SchemeRadioGroup } from './components/SchemeSwatches'
import { ValidationPanel } from './components/ValidationPanel'
import { fetchLayoutIndex, fetchTemplate, type TemplateEntry } from './data/templates'
import {
  loadWasm, wasmReady, error as wasmError,
  parseYaml, toYaml, validateConfig, getDescriptors, applyOps,
} from './wasm'

type EditorTab = 'form' | 'raw' | 'preview'

const FALLBACK_YAML = `metadata:
  app_name: Xime
  app_version: ">=2.5.0"
  platform: android
  config_version: 1
  modified_time: "2026-07-05"
`

export default function App() {
  const [tab, setTab] = useState<EditorTab>('form')
  const [ready, setReady] = useState(false)
  const [config, setConfig] = useState<any>(null)
  const [yamlText, setYamlText] = useState(FALLBACK_YAML)
  const [parseError, setParseError] = useState<string | null>(null)
  const [validation, setValidation] = useState<any>({ valid: true, errors: [], warnings: [] })
  const [descriptors, setDescriptors] = useState<any[]>([])
  const [darkPreview, setDarkPreview] = useState(false)
  const [previewScheme, setPreviewScheme] = useState('')
  const [showTemplates, setShowTemplates] = useState(false)
  const [loadingTemplate, setLoadingTemplate] = useState<string | null>(null)
  const [templateError, setTemplateError] = useState<string | null>(null)
  const [templates, setTemplates] = useState<TemplateEntry[] | null>(null)
  const [showValidation, setShowValidation] = useState(false)
  const validateTimer = useRef<number | undefined>(undefined)

  // 初始化 WASM + 模板（网络优先，失败回退内置）
  useEffect(() => {
    ;(async () => {
      const ok = await loadWasm()
      setReady(true)
      if (!ok) return
      setDescriptors(getDescriptors<any[]>() ?? [])
      // 默认加载布局子索引的第一个模板；失败时用最小兜底
      const idx = await fetchLayoutIndex()
      setTemplates(idx ?? [])
      const yaml = (idx && idx.length ? await fetchTemplate(idx[0]) : null) ?? FALLBACK_YAML
      const parsed = parseYaml(yaml)
      if (parsed) {
        setConfig(parsed)
        setYamlText(yaml)
      }
    })()
  }, [])

  // 校验防抖（300ms，与 Android 端一致）
  useEffect(() => {
    if (!config || !wasmReady()) return
    window.clearTimeout(validateTimer.current)
    validateTimer.current = window.setTimeout(() => {
      setValidation(validateConfig(config) ?? { valid: false, errors: [], warnings: [] })
    }, 300)
    return () => window.clearTimeout(validateTimer.current)
  }, [config, ready])

  /** 批量应用 ops：唯一编辑入口。 */
  const dispatch = useCallback((ops: unknown[]) => {
    if (!ops.length) return
    const updated = applyOps(config, ops)
    if (updated) setConfig(updated)
    else if (wasmError()) console.warn('applyOps 失败:', wasmError())
  }, [config])

  /** 切换 tab：进入 Raw 时用当前配置重新生成源码（config 是唯一状态源）。 */
  const switchTab = useCallback((t: EditorTab) => {
    setTab(t)
    if (t === 'raw' && parseError === null && config && wasmReady()) {
      setYamlText(toYaml(config) ?? yamlText)
    }
  }, [parseError, config, yamlText])

  /** Raw YAML 编辑 → 解析回 config（解析失败时保留上一个有效版本并提示）。 */
  const handleYamlChange = useCallback((yaml: string) => {
    setYamlText(yaml)
    const parsed = wasmReady() ? parseYaml(yaml) : null
    if (parsed) {
      setConfig(parsed)
      setParseError(null)
    } else {
      setParseError(wasmError() ?? '无法解析')
    }
  }, [])

  /** 用 Rust 的 toYaml 做规范化输出，即格式化。 */
  const handleFormat = useCallback(() => {
    if (!config || !wasmReady()) return
    const yaml = toYaml(config)
    if (yaml) {
      setYamlText(yaml)
      setParseError(null)
    }
  }, [config])

  /** 应用模板：拉取 jsDelivr 上的官方示例（无兜底，失败提示）。 */
  const handleTemplate = useCallback(async (entry: TemplateEntry) => {
    if (loadingTemplate) return
    setLoadingTemplate(entry.id)
    setTemplateError(null)
    const yaml = await fetchTemplate(entry)
    setLoadingTemplate(null)
    if (!yaml) {
      setTemplateError('拉取失败或内容不完整，请检查网络后重试')
      return
    }
    const parsed = wasmReady() ? parseYaml(yaml) : null
    if (parsed) {
      setConfig(parsed)
      setYamlText(yaml)
      setParseError(null)
      setPreviewScheme('')
      setShowTemplates(false)
    } else {
      setParseError(wasmError() ?? '模板解析失败')
      setTemplateError('模板解析失败：' + (wasmError() ?? '未知错误'))
    }
  }, [loadingTemplate])

  /** 导出当前配置 YAML（下载）。 */
  const handleExport = useCallback(() => {
    const yaml = config ? toYaml(config) : null
    if (!yaml) return
    const blob = new Blob([yaml], { type: 'text/yaml' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = 'xime.custom.yaml'
    a.click()
    URL.revokeObjectURL(a.href)
  }, [config])

  const errCount = validation?.errors?.length ?? 0
  const warnCount = validation?.warnings?.length ?? 0

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column', background: '#1e1e2e', color: '#cdd6f4' }}>
      <header style={{ padding: '12px 20px', borderBottom: '1px solid #313244', display: 'flex', alignItems: 'center', gap: 16 }}>
        <h1 style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>Xime Config Generator</h1>
        <nav style={{ display: 'flex', gap: 4 }}>
          {(['form', 'raw', 'preview'] as EditorTab[]).map(t => (
            <button key={t} onClick={() => switchTab(t)}
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
          <button onClick={() => setShowTemplates(true)} style={{
            padding: '5px 12px', border: '1px solid #45475a', borderRadius: 6,
            background: 'transparent', color: '#cdd6f4', cursor: 'pointer', fontSize: 12,
          }}>模板</button>
          {!ready && <span style={{ color: '#6c7086' }}>加载引擎…</span>}
          {ready && !wasmReady() && <span style={{ color: '#f38ba8' }}>WASM 不可用（运行 npm run wasm）</span>}
          {errCount > 0 && <span style={{ color: '#f38ba8' }}>✗ {errCount} 错误</span>}
          {errCount === 0 && warnCount > 0 && <span style={{ color: '#f9e2af' }}>⚠ {warnCount} 提示</span>}
          {errCount === 0 && warnCount === 0 && config && <span style={{ color: '#a6e3a1' }}>✓ 校验通过</span>}
          {(errCount > 0 || warnCount > 0) && (
            <button onClick={() => setShowValidation(v => !v)} style={{
              padding: '4px 10px', border: '1px solid #45475a', borderRadius: 6,
              background: 'transparent', color: '#cdd6f4', cursor: 'pointer', fontSize: 12,
            }}>{showValidation ? '收起详情' : '详情'}</button>
          )}
          {config && <button onClick={handleExport} style={{
            padding: '5px 12px', border: '1px solid #45475a', borderRadius: 6,
            background: 'transparent', color: '#cdd6f4', cursor: 'pointer', fontSize: 12,
          }}>导出 YAML</button>}
        </div>
      </header>

      {showValidation && (errCount > 0 || warnCount > 0) && (
        <div style={{ padding: '0 20px', borderBottom: '1px solid #313244', background: '#181825' }}>
          <ValidationPanel validation={validation} />
        </div>
      )}

      <main style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {tab === 'raw' && (
          <YamlEditor
            value={yamlText}
            onChange={handleYamlChange}
            parseError={parseError}
            onFormat={handleFormat}
            dirty={parseError !== null}
          />
        )}
        {tab === 'form' && (
          <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
            <div style={{ flex: 1, overflow: 'auto', padding: 16, borderRight: '1px solid #313244' }}>
              <EditorPanel config={config} descriptors={descriptors} onDispatch={dispatch} />
            </div>
            <div style={{ width: 420, overflow: 'auto', padding: 16 }}>
              <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <span style={{ fontSize: 13, color: '#6c7086' }}>实时预览</span>
                <button onClick={() => setDarkPreview(d => !d)} style={{
                  padding: '4px 10px', border: '1px solid #45475a', borderRadius: 6,
                  background: 'transparent', color: '#cdd6f4', cursor: 'pointer', fontSize: 12,
                }}>{darkPreview ? '深色' : '浅色'}</button>
              </div>
              <div style={{ marginBottom: 8 }}>
                <SchemeRadioGroup config={config} value={previewScheme} onChange={setPreviewScheme} />
              </div>
              <PreviewPanel config={config} dark={darkPreview} schemeId={previewScheme || undefined} />
            </div>
          </div>
        )}
        {tab === 'preview' && (
          <div style={{ flex: 1, overflow: 'auto', padding: 16 }}>
            <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <button onClick={() => setDarkPreview(d => !d)} style={{
                padding: '4px 10px', border: '1px solid #45475a', borderRadius: 6,
                background: 'transparent', color: '#cdd6f4', cursor: 'pointer', fontSize: 12,
              }}>{darkPreview ? '深色' : '浅色'}</button>
            </div>
            <div style={{ marginBottom: 8, maxWidth: 460 }}>
              <SchemeRadioGroup config={config} value={previewScheme} onChange={setPreviewScheme} />
            </div>
            <PreviewPanel config={config} dark={darkPreview} schemeId={previewScheme || undefined} />
          </div>
        )}
      </main>

      <TemplatePicker
        open={showTemplates}
        templates={templates}
        loadingId={loadingTemplate}
        error={templateError}
        onClose={() => setShowTemplates(false)}
        onPick={handleTemplate}
      />
    </div>
  )
}
