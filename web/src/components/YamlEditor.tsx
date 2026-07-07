import { useRef, useEffect } from 'react'

interface Props {
  value: string
  onChange: (value: string) => void
}

export function YamlEditor({ value, onChange }: Props) {
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ padding: '8px 12px', fontSize: 12, color: '#6c7086', borderBottom: '1px solid #313244' }}>
        YAML Source
      </div>
      <textarea
        ref={textareaRef}
        value={value}
        onChange={e => onChange(e.target.value)}
        spellCheck={false}
        style={{
          flex: 1,
          padding: 12,
          border: 'none',
          outline: 'none',
          resize: 'none',
          fontFamily: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace",
          fontSize: 13,
          lineHeight: 1.6,
          background: '#181825',
          color: '#cdd6f4',
          tabSize: 2,
        }}
      />
    </div>
  )
}
