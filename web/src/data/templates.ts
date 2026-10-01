/**
 * 模板目录：来自 Xime 官方布局子索引 index.ximei.me/layouts/index.yaml
 * （xime-index 仓库 scripts/ci-update.py 自动生成），无兜底，失败提示网络问题。
 */

export interface TemplateEntry {
  id: string
  title: string
  description: string
  downloadUrl: string
}

export const LAYOUTS_INDEX_URL = 'https://index.ximei.me/layouts/index.yaml'

/** 拉取并解析布局子索引（极简状态机解析，避免引入 YAML 依赖）。失败返回 null。 */
export async function fetchLayoutIndex(): Promise<TemplateEntry[] | null> {
  try {
    const resp = await fetch(LAYOUTS_INDEX_URL)
    if (!resp.ok) return null
    return parseLayoutsIndex(await resp.text())
  } catch {
    return null
  }
}

export function parseLayoutsIndex(raw: string): TemplateEntry[] {
  const entries: TemplateEntry[] = []
  let curId: string | null = null
  let curName = ''
  let curDesc = ''
  let curUrl: string | null = null
  const flush = () => {
    if (curId && curUrl) {
      entries.push({
        id: curId,
        title: curName || curId,
        description: curDesc || '键盘布局模板',
        downloadUrl: curUrl,
      })
    }
    curId = null; curName = ''; curDesc = ''; curUrl = null
  }
  for (const line of raw.split('\n')) {
    const t = line.trim()
    if (t.startsWith('- id:')) {
      flush()
      curId = t.slice(5).trim().replace(/^["']|["']$/g, '')
    } else if (t.startsWith('name:') && curId && !curName) {
      curName = t.slice(5).trim().replace(/^["']|["']$/g, '')
    } else if (t.startsWith('description:') && curId) {
      curDesc = t.slice(12).trim().replace(/^["']|["']$/g, '')
    } else if (t.startsWith('- url:') && curId && !curUrl) {
      curUrl = t.slice(6).trim().replace(/^["']|["']$/g, '')
    }
  }
  flush()
  return entries
}

/** 按索引给出的 URL 直接拉取模板内容；无效内容返回 null。 */
export async function fetchTemplate(entry: TemplateEntry): Promise<string | null> {
  try {
    const resp = await fetch(entry.downloadUrl)
    if (!resp.ok) return null
    const text = await resp.text()
    return text.includes('color_schemes') || text.includes('keyboard') ? text : null
  } catch {
    return null
  }
}
