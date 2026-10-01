import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ops } from '../wasm'
import { KeyboardGestureEditor } from './KeyboardGestureEditor'

afterEach(cleanup)

const baseConfig = {
  keyboard: {
    qwerty: {
      layout: { rows: [['q', 'w', 'e'], ['a', 's', 'd']] },
      keys: { q: { tap: 'q' } },
    },
  },
}

describe('KeyboardGestureEditor', () => {
  it('renders rows with function key display names', () => {
    const config = {
      keyboard: {
        qwerty: {
          layout: { rows: [['shift', 'q', 'delete'], ['mode_change', 'comma', 'space', 'earth', 'enter']] },
        },
      },
    }
    render(<KeyboardGestureEditor config={config} onDispatch={() => {}} />)
    expect(screen.getByRole('button', { name: '⇧' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '⌫' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '?123' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '，' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '空格' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '中' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '换行' })).toBeInTheDocument()
  })

  it('appending a key dispatches row set plus key definition', async () => {
    const onDispatch = vi.fn()
    render(<KeyboardGestureEditor config={baseConfig} onDispatch={onDispatch} />)
    await userEvent.click(screen.getAllByRole('button', { name: '＋键' })[0])
    await userEvent.type(screen.getByPlaceholderText('键位 id'), 'x')
    await userEvent.click(screen.getByRole('button', { name: '添加' }))
    expect(onDispatch).toHaveBeenCalledTimes(1)
    const batch = onDispatch.mock.calls[0][0]
    expect(batch).toHaveLength(2)
    expect(batch[0]).toEqual(ops.set('/keyboard/qwerty/layout/rows', [['q', 'w', 'e', 'x'], ['a', 's', 'd']]))
    expect(batch[1]).toEqual(ops.set('/keyboard/qwerty/keys/x', { tap: 'x' }))
  })

  it('moving a key right swaps within the row', async () => {
    const onDispatch = vi.fn()
    render(<KeyboardGestureEditor config={baseConfig} onDispatch={onDispatch} />)
    await userEvent.click(screen.getByRole('button', { name: 'Q' }))
    await userEvent.click(screen.getByTitle('右移'))
    expect(onDispatch).toHaveBeenCalledWith([
      ops.set('/keyboard/qwerty/layout/rows', [['w', 'q', 'e'], ['a', 's', 'd']]),
    ])
  })

  it('removing a key drops it from the row', async () => {
    const onDispatch = vi.fn()
    render(<KeyboardGestureEditor config={baseConfig} onDispatch={onDispatch} />)
    await userEvent.click(screen.getByRole('button', { name: 'Q' }))
    await userEvent.click(screen.getByTitle('移除'))
    expect(onDispatch).toHaveBeenCalledWith([
      ops.set('/keyboard/qwerty/layout/rows', [['w', 'e'], ['a', 's', 'd']]),
    ])
  })

  it('adding a row uses built-in default template not empty row', async () => {
    const onDispatch = vi.fn()
    render(<KeyboardGestureEditor config={baseConfig} onDispatch={onDispatch} />)
    await userEvent.click(screen.getByRole('button', { name: '＋ 添加行' }))
    expect(onDispatch).toHaveBeenCalledWith([
      ops.set('/keyboard/qwerty/layout/rows', [
        ['q', 'w', 'e'],
        ['a', 's', 'd'],
        ['shift', 'z', 'x', 'c', 'v', 'b', 'n', 'm', 'delete'],
      ]),
    ])
  })

  it('merged keys display joined with dot separator', () => {
    const config = { keyboard: { qwerty: { layout: { rows: [[['q', 'w'], 'e']] } } } }
    render(<KeyboardGestureEditor config={config} onDispatch={() => {}} />)
    expect(screen.getByRole('button', { name: 'Q·W' })).toBeInTheDocument()
  })

  it('selecting a key opens gesture editors dispatching to keys path', async () => {
    const onDispatch = vi.fn()
    render(<KeyboardGestureEditor config={baseConfig} onDispatch={onDispatch} />)
    await userEvent.click(screen.getByRole('button', { name: 'Q' }))
    // 点按 tap 的显示输入框
    const input = screen.getAllByPlaceholderText('键面/气泡文字')[0]
    await userEvent.type(input, 'Q')
    expect(onDispatch).toHaveBeenCalled()
    const last = onDispatch.mock.calls[onDispatch.mock.calls.length - 1][0]
    expect(last[0]).toMatchObject({ op: 'set', path: '/keyboard/qwerty/keys/q/tap' })
  })
})
