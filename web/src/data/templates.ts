/** 模板目录：与 Android 端 ConfigRepository.CATALOG 一致，jsDelivr CDN，无兜底。 */

export interface TemplateEntry {
  id: string
  title: string
  description: string
}

export const CATALOG: TemplateEntry[] = [
  { id: 'full', title: '全键盘完整示例', description: '标准布局，手势/颜色/阴影全量配置' },
  { id: 'wubi_compact', title: '五笔·紧凑布局', description: '五笔字根气泡提示，紧凑键面' },
  { id: 'flypy', title: '小鹤双拼', description: '紧凑布局双拼键位' },
  { id: 'msdouble', title: '微软双拼', description: '标准布局双拼键位' },
  { id: 'cangjie', title: '仓颉', description: '仓颉字根键位' },
  { id: 'theme', title: '主题配色示例', description: '多套配色与纯色/渐变/图片背景演示' },
  { id: 'shortcut', title: '快捷符号手势', description: '键面直显快捷符号的手势写法' },
]

export const CDN_BASE = 'https://cdn.jsdelivr.net/gh/ximeiorg/xime@master/docs/config_examples/'

/** 拉取模板 YAML（无兜底，失败返回 null，由调用方提示）。 */
export async function fetchExample(id: string): Promise<string | null> {
  try {
    const resp = await fetch(`${CDN_BASE}${id}/xime.custom.yaml`)
    if (!resp.ok) return null
    const text = await resp.text()
    return text.includes('color_schemes') ? text : null
  } catch {
    return null
  }
}
