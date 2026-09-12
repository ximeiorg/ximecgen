/**
 * WASM 绑定封装：统一解 ApiResult 信封 {"ok","data","error"}。
 * configJson 是唯一状态源；YAML 只在导入/导出边界出现。
 */

let wasm: any = null
let lastError: string | null = null

export function wasmReady(): boolean {
  return wasm !== null
}

export function error(): string | null {
  return lastError
}

export async function loadWasm(): Promise<boolean> {
  try {
    const mod = await import('./pkg/xime_config_core.js')
    await mod.default()
    wasm = mod
    return true
  } catch (e) {
    console.warn('WASM 加载失败', e)
    return false
  }
}

function unwrap<T>(raw: string): T | null {
  try {
    const r = JSON.parse(raw)
    if (r.ok) {
      lastError = null
      return r.data as T
    }
    lastError = r.error ?? '未知错误'
    return null
  } catch (e) {
    lastError = String(e)
    return null
  }
}

export function parseYaml<T = any>(yaml: string): T | null {
  return wasm ? unwrap<T>(wasm.parse(yaml)) : null
}

export function toYaml(config: unknown): string | null {
  return wasm ? unwrap<string>(wasm.to_yaml(JSON.stringify(config))) : null
}

export function validateConfig<T = any>(config: unknown): T | null {
  return wasm ? unwrap<T>(wasm.validate(JSON.stringify(config))) : null
}

export function getDescriptors<T = any[]>(): T | null {
  return wasm ? unwrap<T>(wasm.get_field_descriptors()) : null
}

export function applyOps<T = any>(config: unknown, ops: unknown[]): T | null {
  return wasm ? unwrap<T>(wasm.apply_ops(JSON.stringify(config), JSON.stringify(ops))) : null
}

/** ops 构造工具（JSON Pointer 寻址，与 Android 端 Ops 对应）。 */
export const ops = {
  set: (path: string, value: unknown) => ({ op: 'set', path, value }),
  add: (path: string, value: unknown) => ({ op: 'add', path, value }),
  remove: (path: string) => ({ op: 'remove', path }),
}
