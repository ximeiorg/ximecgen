import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { PreviewPanel } from './PreviewPanel'

afterEach(cleanup)

/** 数字行 5 行模板（含合并键 [q,w] 与双拼式多行下滑提示）。 */
const NUMBER_ROWS_CONFIG = {
  keyboard: {
    key: { corner_radius: 8, spacing_x: 2, spacing_y: 4.25 },
    shadow: { enabled: true, elevation: 0.5 },
    qwerty: {
      layout: {
        rows: [
          ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
          [['q', 'w'], 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
          ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
          ['shift', 'z', 'x', 'c', 'v', 'b', 'n', 'm', 'delete'],
          ['mode_change', 'comma', 'space', 'earth', 'enter'],
        ],
      },
      keys: {
        q: { tap: 'q', swipe_down: { label: 'ue\nve', action: 'none', display: 'key' } },
        comma: { tap: { label: '，', value: ',' } },
        earth: { tap: { label: '@language', action: 'toggle_ascii' } },
      },
    },
  },
}

describe('PreviewPanel', () => {
  it('renders five rows with function keys from layout.rows', () => {
    const { container } = render(<PreviewPanel config={NUMBER_ROWS_CONFIG} dark={false} />)
    // 功能键：文本键与图标键
    expect(screen.getByText('?123')).toBeInTheDocument()
    expect(screen.getByText('换行')).toBeInTheDocument()
    expect(screen.getByText('，')).toBeInTheDocument()
    expect(screen.getByText('空格')).toBeInTheDocument()
    // shift/delete/earth 为图标（svg）；@language 视为图标约定
    expect(container.querySelectorAll('svg')).toHaveLength(3)
    // 每行抽样
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('A')).toBeInTheDocument()
  })

  it('merged key subarray renders joined uppercase id', () => {
    render(<PreviewPanel config={NUMBER_ROWS_CONFIG} dark={false} />)
    expect(screen.getByText('QW')).toBeInTheDocument()
  })

  it('multi-line swipe label renders both lines in compact mode', () => {
    const compact = {
      keyboard: {
        qwerty: {
          button_layout: 'compact',
          layout: { rows: [['t']] },
          keys: { t: { tap: 't', swipe_down: { label: 'ue\nve', action: 'none', display: 'key' } } },
        },
      },
    }
    const { container } = render(<PreviewPanel config={compact} dark={false} />)
    expect(container.textContent).toContain('ue')
    expect(container.textContent).toContain('ve')
  })

  it('config without layout.rows falls back to built-in rows with shift/delete', () => {
    // 无 rows 的双拼类模板：兜底行带功能键（对齐 Xime 内置 xime.yaml 回退）
    const { container } = render(<PreviewPanel config={{ keyboard: { qwerty: { button_layout: 'compact' } } }} dark={false} />)
    expect(screen.getByText('?123')).toBeInTheDocument()
    expect(container.querySelectorAll('svg')).toHaveLength(3)
  })

  it('dark mode renders without crash and keeps control row', () => {
    render(<PreviewPanel config={NUMBER_ROWS_CONFIG} dark />)
    expect(screen.getByText('?123')).toBeInTheDocument()
    expect(screen.getByText('换行')).toBeInTheDocument()
  })

  it('delete key carries default clear hint', () => {
    render(<PreviewPanel config={NUMBER_ROWS_CONFIG} dark={false} />)
    expect(screen.getByText('清空')).toBeInTheDocument()
  })
})
