import { useEffect, useState } from 'react'
import Editor, { loader, type OnMount } from '@monaco-editor/react'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/editor/editor.worker.js?worker'

// 使用本地打包的 monaco（默认会从 CDN 加载，离线不可用）
loader.config({ monaco })
;(self as any).MonacoEnvironment = { getWorker: () => new editorWorker() }

// 与应用整体的 Catppuccin Mocha 风格一致的 YAML 主题
const THEME = 'xime-dark'
function defineTheme() {
  monaco.editor.defineTheme(THEME, {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: '', foreground: 'cdd6f4' },
      { token: 'comment', foreground: '6c7086', fontStyle: 'italic' },
      { token: 'key', foreground: '89b4fa' },
      { token: 'string.key.yaml', foreground: '89b4fa' },
      { token: 'string.value.yaml', foreground: 'a6e3a1' },
      { token: 'string', foreground: 'a6e3a1' },
      { token: 'number', foreground: 'fab387' },
      { token: 'keyword', foreground: 'cba6f7' },
      { token: 'keyword.flow', foreground: 'cba6f7' },
      { token: 'delimiter', foreground: '94e2d5' },
    ],
    colors: {
      'editor.background': '#181825',
      'editor.foreground': '#cdd6f4',
      'editorLineNumber.foreground': '#585b70',
      'editorLineNumber.activeForeground': 'cdd6f4',
      'editor.selectionBackground': '#45475a',
      'editor.inactiveSelectionBackground': '#313244',
      'editorCursor.foreground': '#f5e0dc',
      'editorIndentGuide.background1': '#45475a',
      'editorIndentGuide.activeBackground1': '#89b4fa',
      'editorGutter.background': '#181825',
      'scrollbarSlider.background': '#45475a80',
      'scrollbarSlider.hoverBackground': '#585b7080',
    },
  })
}
let themeDefined = false

interface Props {
  value: string
  onChange: (value: string) => void
  /** 解析失败时的错误信息（输入中途的语法错误不阻塞编辑） */
  parseError: string | null
  onFormat: () => void
  dirty: boolean
}

export function YamlEditor({ value, onChange, parseError, onFormat, dirty }: Props) {
  const [ready, setReady] = useState(false)
  useEffect(() => {
    if (!themeDefined) {
      defineTheme()
      themeDefined = true
    }
    setReady(true)
  }, [])

  const handleMount: OnMount = (editor) => {
    editor.focus()
  }

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', minWidth: 0 }}>
      <div style={{
        padding: '8px 12px', fontSize: 12, color: '#6c7086', borderBottom: '1px solid #313244',
        display: 'flex', alignItems: 'center', gap: 12,
      }}>
        <span>YAML 源码（VS Code 编辑器内核）</span>
        {dirty && <span style={{ color: '#f9e2af' }}>存在解析错误，表单与预览停留在上一个有效配置</span>}
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
          <button onClick={onFormat} style={btnStyle}>格式化</button>
        </div>
      </div>
      {parseError && (
        <div style={{
          padding: '6px 12px', fontSize: 12, background: '#3b2530',
          color: '#f38ba8', borderBottom: '1px solid #f38ba840', whiteSpace: 'pre-wrap',
        }}>
          YAML 解析失败：{parseError}
        </div>
      )}
      <div style={{ flex: 1, minHeight: 0 }}>
        {ready && (
          <Editor
            language="yaml"
            theme={THEME}
            value={value}
            onChange={v => onChange(v ?? '')}
            onMount={handleMount}
            loading={<span style={{ color: '#6c7086' }}>加载编辑器…</span>}
            options={{
              fontSize: 15,
              lineHeight: 24,
              fontFamily: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace",
              tabSize: 2,
              insertSpaces: true,
              wordWrap: 'on',
              minimap: { enabled: false },
              scrollBeyondLastLine: false,
              automaticLayout: true,
              renderLineHighlight: 'all',
              guides: { indentation: true, highlightActiveIndentation: true },
              smoothScrolling: true,
              overviewRulerLanes: 0,
              padding: { top: 10, bottom: 10 },
              scrollbar: { verticalScrollbarSize: 10, horizontalScrollbarSize: 10 },
            }}
          />
        )}
      </div>
    </div>
  )
}

const btnStyle: React.CSSProperties = {
  padding: '4px 12px', border: '1px solid #45475a', borderRadius: 6,
  background: 'transparent', color: '#cdd6f4', cursor: 'pointer', fontSize: 12,
}
