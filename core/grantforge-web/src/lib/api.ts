import type { ApiResponse, Page } from '@/types/api'
import { readToken } from './session'

export class ApiError extends Error {
  constructor(message: string, readonly status = 0, readonly code = 0, readonly details: unknown = null) {
    super(message)
    this.name = 'ApiError'
  }
}
let unauthorizedHandler: (() => void) | undefined
export function onUnauthorized(handler: () => void): void { unauthorizedHandler = handler }

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  form?: URLSearchParams
  query?: Record<string, string | number | boolean | undefined>
  signal?: AbortSignal
  anonymous?: boolean
}
const base = import.meta.env.VITE_API_BASE_URL || ''
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(options.query || {})) if (value !== undefined) query.set(key, String(value))
  const suffix = query.size ? `?${query}` : ''
  const token = options.anonymous ? '' : readToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.form) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded'
    headers.Authorization = `Basic ${btoa('AuthX-Client:AuthX-Web')}`
  } else if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  let response: Response
  try {
    response = await fetch(`${base}${path}${suffix}`, {
      method: options.method || 'GET', headers, signal: options.signal,
      body: options.form || (options.body === undefined ? undefined : JSON.stringify(options.body)),
    })
  } catch (error) {
    if (options.signal?.aborted) throw error
    throw new ApiError('无法连接服务，请检查网络后重试')
  }
  const payload: unknown = await response.json().catch(() => null)
  const record = payload && typeof payload === 'object' ? payload as Partial<ApiResponse<T>> : null
  const code = record?.code || 0
  const message = record?.message || (response.status === 401 ? '登录已失效，请重新登录' : response.status === 403 ? '你没有执行此操作的权限' : `请求失败（${response.status}）`)
  if (response.status === 401 || code === 4001) {
    if (!options.anonymous) unauthorizedHandler?.()
    throw new ApiError(message, 401, code, record?.data)
  }
  if (!response.ok || code !== 2000) throw new ApiError(message, response.status === 200 && code === 4000 ? 403 : response.status, code, record?.data)
  return record?.data as T
}
export function authenticate(username: string, password: string): Promise<string> {
  return request<string>('/oauth/token', { method: 'POST', anonymous: true,
    form: new URLSearchParams({ username, password, grant_type: 'password', client_id: 'AuthX-Client' }),
  })
}
export async function allOptions<T>(path: string, signal?: AbortSignal): Promise<T[]> {
  const rows: T[] = []
  for (let page = 1; page <= 50; page++) {
    const result = await request<Page<T>>(path, { query: { page, size: 100 }, signal })
    rows.push(...result.content)
    if (page >= result.totalPages || result.content.length === 0) return rows
  }
  throw new ApiError('可选项数量过多，请联系管理员缩小数据范围')
}
export function errorMessage(error: unknown): string { return error instanceof Error ? error.message : '操作失败，请稍后重试' }
