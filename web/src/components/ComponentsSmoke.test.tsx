import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SchemeRadioGroup } from './SchemeSwatches'
import { TemplatePicker } from './TemplatePicker'
import { ValidationPanel } from './ValidationPanel'

afterEach(cleanup)

describe('ValidationPanel', () => {
  it('renders nothing when valid and quiet', () => {
    const { container } = render(<ValidationPanel validation={{ valid: true, errors: [], warnings: [] }} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('lists errors and warnings after expanding', async () => {
    render(
      <ValidationPanel
        validation={{
          valid: false,
          errors: [{ path: '/keyboard/qwerty/button_layout', message: '未知布局模式' }],
          warnings: ['配置中没有 color_schemes'],
        }}
      />,
    )
    // 默认折叠：点标题展开后可见明细
    await userEvent.click(screen.getByRole('button'))
    expect(screen.getByText(/未知布局模式/)).toBeInTheDocument()
    expect(screen.getByText(/color_schemes/)).toBeInTheDocument()
  })
})

describe('TemplatePicker', () => {
  const templates = [
    { id: 'flypy', title: '小鹤双拼带韵母', description: '专属于小鹤双拼版本。', downloadUrl: 'https://a/flypy.yaml' },
    { id: 'number_rows', title: '数字行', description: '新增数字行', downloadUrl: 'https://a/num.yaml' },
  ]

  it('renders nothing when closed', () => {
    const { container } = render(
      <TemplatePicker open={false} templates={templates} loadingId={null} error={null} onClose={() => {}} onPick={() => {}} />,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('lists entries and reports load failure state', async () => {
    const onPick = vi.fn()
    render(<TemplatePicker open templates={templates} loadingId={null} error={null} onClose={() => {}} onPick={onPick} />)
    expect(screen.getByText('小鹤双拼带韵母')).toBeInTheDocument()
    await userEvent.click(screen.getByText('数字行'))
    expect(onPick).toHaveBeenCalledWith(templates[1])

    cleanup()
    render(<TemplatePicker open templates={[]} loadingId={null} error={null} onClose={() => {}} onPick={() => {}} />)
    expect(screen.getByText(/拉取失败/)).toBeInTheDocument()
  })
})

describe('SchemeRadioGroup', () => {
  const config = {
    color_schemes: {
      lavender_purple: { name: '薰衣草紫', primary_color: 0x8f73e2 },
      ocean_blue: { name: '海洋蔚蓝', primary_color: 0x1a73e8 },
    },
  }

  it('renders radio list with localized names and fires change', async () => {
    const onChange = vi.fn()
    render(<SchemeRadioGroup config={config} value="lavender_purple" onChange={onChange} />)
    expect(screen.getByText('薰衣草紫')).toBeInTheDocument()
    expect(screen.getByText('海洋蔚蓝')).toBeInTheDocument()
    await userEvent.click(screen.getByText('海洋蔚蓝'))
    expect(onChange).toHaveBeenCalledWith('ocean_blue')
  })

  it('dynamic scheme shows localized label', () => {
    const withDynamic = { color_schemes: { dynamic: { dynamic_color: true }, ...config.color_schemes } }
    render(<SchemeRadioGroup config={withDynamic} value="" onChange={() => {}} />)
    expect(screen.getByText('动态配色')).toBeInTheDocument()
  })
})
