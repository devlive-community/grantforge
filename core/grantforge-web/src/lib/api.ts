// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { currentLocale, translate } from '@/i18n'

/** A field that failed server-side validation. */
export interface FieldProblem { field: string; message: string }

/**
 * RFC 9457 problem details returned by the rebuilt server. Clients branch on `code` and localise with
 * `messageKey`; `requestId` correlates the failure with server logs.
 */
export interface Problem {
  status: number
  title?: string
  detail?: string
  code?: string
  messageKey?: string
  requestId?: string
  errors?: FieldProblem[]
}

export class ApiError extends Error {
  constructor(message: string, readonly status = 0, readonly code = 0, readonly details: unknown = null,
    readonly problem: Problem | null = null) {
    super(message)
    this.name = 'ApiError'
  }
}
let unauthorizedHandler: (() => void) | undefined
export function onUnauthorized(handler: () => void): void { unauthorizedHandler = handler }
/** Answers to calls that need a permission carry the version of the caller's permissions in this header. */
export const AUTHORIZATION_VERSION_HEADER = 'X-Authorization-Version'
let versionHandler: ((version: string) => void) | undefined
/** Calls `handler` with the version of the user's permissions each time an answer reports it. */
export function onAuthorizationVersion(handler: (version: string) => void): void { versionHandler = handler }

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | boolean | undefined>
  signal?: AbortSignal
  /** A 401 answer is expected (sign-in, session probe) and must not end the current session. */
  anonymous?: boolean
}
const base = import.meta.env.VITE_API_BASE_URL || ''
const SAFE_METHODS = new Set(['GET', 'HEAD'])

/** Reads a cookie value, or '' when absent (Spring Security stores the CSRF token in XSRF-TOKEN). */
export function readCookie(name: string): string {
  const prefix = `${name}=`
  const entry = document.cookie.split('; ').find(item => item.startsWith(prefix))
  if (!entry) return ''
  try { return decodeURIComponent(entry.slice(prefix.length)) } catch { return '' }
}

function statusMessage(status: number): string {
  if (status === 401) return translate('errors.unauthorized')
  if (status === 403) return translate('errors.forbidden')
  return translate('errors.status', { status })
}

/** Returns the problem details of an error response, or null for other bodies. */
export function toProblem(payload: unknown, response: Response): Problem | null {
  if (!payload || typeof payload !== 'object') return null
  const body = payload as Record<string, unknown>
  const isProblem = (response.headers.get('Content-Type') || '').includes('application/problem+json')
    || (typeof body.status === 'number' && typeof body.code === 'string')
  if (!isProblem) return null
  const text = (key: string) => typeof body[key] === 'string' ? body[key] as string : undefined
  const errors = Array.isArray(body.errors)
    ? body.errors.filter((item): item is FieldProblem => !!item && typeof item === 'object'
        && typeof (item as FieldProblem).field === 'string' && typeof (item as FieldProblem).message === 'string')
    : undefined
  return { status: typeof body.status === 'number' ? body.status : response.status, title: text('title'), detail: text('detail'),
    code: text('code'), messageKey: text('messageKey'), requestId: text('requestId'), errors }
}

/** User-facing text for a problem: the server detail for client errors, a generic text with the request ID otherwise. */
export function problemMessage(problem: Problem): string {
  if (problem.status >= 500) {
    return problem.requestId ? translate('errors.unavailableWithId', { id: problem.requestId }) : translate('errors.unavailable')
  }
  return problem.detail || problem.title || statusMessage(problem.status)
}
/**
 * Asks the server for a CSRF token: every response carries the XSRF-TOKEN cookie, but signing out removes it
 * and signing in replaces it, so a console that has made no request since needs a fresh one.
 */
async function fetchCsrfToken(signal?: AbortSignal) {
  try { await fetch(`${base}/api/v1/bootstrap`, { headers: { Accept: 'application/json' }, credentials: 'same-origin', signal }) }
  catch { /* the request itself reports the failure */ }
}
export async function request<T>(path: string, options: RequestOptions = {}, retryCsrf = true): Promise<T> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(options.query || {})) if (value !== undefined) query.set(key, String(value))
  const suffix = query.size ? `?${query}` : ''
  const method = options.method || 'GET'
  // Accept-Language lets the server localise problem details to the interface language.
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json', 'Accept-Language': currentLocale() }
  const unsafe = !SAFE_METHODS.has(method)
  if (unsafe && !readCookie('XSRF-TOKEN')) await fetchCsrfToken(options.signal)
  const csrf = unsafe ? readCookie('XSRF-TOKEN') : ''
  if (csrf) headers['X-XSRF-TOKEN'] = csrf
  // A FormData body (file uploads) sets its own multipart content type with the boundary.
  const form = options.body instanceof FormData
  if (options.body !== undefined && !form) headers['Content-Type'] = 'application/json'
  let response: Response
  try {
    response = await fetch(`${base}${path}${suffix}`, {
      method, headers, signal: options.signal, credentials: 'same-origin',
      body: options.body === undefined ? undefined : form ? options.body as FormData : JSON.stringify(options.body),
    })
  } catch (error) {
    if (options.signal?.aborted) throw error
    throw new ApiError(translate('errors.network'))
  }
  const version = response.headers.get(AUTHORIZATION_VERSION_HEADER)
  if (version) versionHandler?.(version)
  const payload: unknown = await response.json().catch(() => null)
  const problem = toProblem(payload, response)
  if (problem) {
    // A token from before a sign-in or sign-out is stale: fetch the current one and try once more.
    if (problem.code === 'GF-SECURITY-001' && unsafe && retryCsrf) {
      await fetchCsrfToken(options.signal)
      return request<T>(path, options, false)
    }
    if (problem.status === 401 && !options.anonymous) unauthorizedHandler?.()
    throw new ApiError(problemMessage(problem), problem.status, 0, problem.errors ?? null, problem)
  }
  const record = payload && typeof payload === 'object' ? payload as Partial<LegacyEnvelope<T>> : null
  // Legacy envelope ({ code: 2000, message, data }) of the pre-rebuild API; plain JSON otherwise.
  const legacy = typeof record?.code === 'number' && 'message' in (record ?? {})
  if (!legacy) {
    if (response.status === 401 && !options.anonymous) unauthorizedHandler?.()
    if (!response.ok) throw new ApiError(statusMessage(response.status), response.status)
    return payload as T
  }
  const code = record?.code || 0
  const message = record?.message || statusMessage(response.status)
  if (response.status === 401 || code === 4001) {
    if (!options.anonymous) unauthorizedHandler?.()
    throw new ApiError(message, 401, code, record?.data)
  }
  if (!response.ok || code !== 2000) throw new ApiError(message, response.status === 200 && code === 4000 ? 403 : response.status, code, record?.data)
  return record?.data as T
}
/** The pre-rebuild API's response envelope, still understood for compatibility. */
interface LegacyEnvelope<T> { code: number; message: string; data: T }
/** Downloads a file, such as a CSV export, and names it as the server does (Content-Disposition). */
export async function download(path: string, query: Record<string, string | number | undefined> = {}): Promise<{ blob: Blob; filename: string }> {
  const parameters = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) if (value !== undefined && value !== '') parameters.set(key, String(value))
  const suffix = parameters.size ? `?${parameters}` : ''
  let response: Response
  try {
    response = await fetch(`${base}${path}${suffix}`, { credentials: 'same-origin',
      headers: { Accept: 'text/csv, application/problem+json', 'Accept-Language': currentLocale() } })
  } catch { throw new ApiError(translate('errors.network')) }
  if (!response.ok) {
    const problem = toProblem(await response.json().catch(() => null), response)
    throw problem ? new ApiError(problemMessage(problem), problem.status, 0, problem.errors ?? null, problem)
      : new ApiError(statusMessage(response.status), response.status)
  }
  const disposition = response.headers.get('Content-Disposition') || ''
  const filename = /filename="([^"]+)"/.exec(disposition)?.[1] ?? 'download'
  return { blob: await response.blob(), filename }
}
export function errorMessage(error: unknown): string { return error instanceof Error ? error.message : translate('errors.generic') }
