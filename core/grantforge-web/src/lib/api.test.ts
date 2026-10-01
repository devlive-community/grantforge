// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, onUnauthorized, problemMessage, readCookie, request } from './api'
const fetchMock = vi.fn<typeof fetch>()
const response = (data: unknown, code = 2000, status = 200) => new Response(JSON.stringify({ code, message: 'test message', data }), { status })
beforeEach(() => { localStorage.clear(); vi.stubGlobal('fetch', fetchMock); fetchMock.mockReset() })
afterEach(() => vi.unstubAllGlobals())
describe('API contract', () => {
  it.each(['GET', 'POST', 'PUT', 'DELETE'] as const)('relies on the session cookie for %s, never a bearer token', async method => {
    localStorage.setItem('AuthXToken', 'stale-token')
    fetchMock.mockResolvedValue(response({ id: 1 }))
    await request('/api/v1/user', { method, ...(method === 'GET' || method === 'DELETE' ? { query: { id: 1 } } : { body: { id: 1 } }) })
    const options = fetchMock.mock.calls[0]?.[1]
    expect(options?.headers).not.toHaveProperty('Authorization')
    expect(options?.credentials).toBe('same-origin')
  })
  it('clears authenticated state on unauthorized responses', async () => {
    const expired = vi.fn(); onUnauthorized(expired)
    fetchMock.mockResolvedValue(response(null, 4001, 401))
    await expect(request('/api/v1/user')).rejects.toMatchObject({ status: 401 })
    expect(expired).toHaveBeenCalledOnce()
  })
  it('keeps expected 401 answers local to the caller', async () => {
    const expired = vi.fn(); onUnauthorized(expired)
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ status: 401, code: 'GF-IDENTITY-020', detail: 'wrong' }),
      { status: 401, headers: { 'Content-Type': 'application/problem+json' } }))
    await expect(request('/api/v1/auth/login', { method: 'POST', anonymous: true, body: {} })).rejects.toBeInstanceOf(ApiError)
    expect(expired).not.toHaveBeenCalled()
  })
  it('accepts empty 204 answers', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await expect(request('/api/v1/auth/logout', { method: 'POST', anonymous: true })).resolves.toBeNull()
  })
  it('treats business-level denial as an error even with HTTP 200', async () => {
    fetchMock.mockResolvedValue(response(null, 4000))
    await expect(request('/api/v1/role')).rejects.toMatchObject({ status: 403, code: 4000 })
  })
  it('does not disguise cancellation as a network failure', async () => {
    const controller = new AbortController(); controller.abort()
    const aborted = new DOMException('cancelled', 'AbortError'); fetchMock.mockRejectedValue(aborted)
    await expect(request('/api/v1/user', { signal: controller.signal })).rejects.toBe(aborted)
  })
  it('returns plain JSON bodies of the rebuilt API unchanged', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ id: 7, code: 'ADMIN' }), { status: 200 }))
    await expect(request('/api/v1/roles/7')).resolves.toEqual({ id: 7, code: 'ADMIN' })
    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe('same-origin')
  })
  it('turns problem details into errors with code, request id and field errors', async () => {
    const body = { status: 400, title: 'Bad Request', detail: 'name must not be blank', code: 'GF-COMMON-400',
      messageKey: 'error.common.bad-request', requestId: 'req-1', errors: [{ field: 'name', message: 'must not be blank' }, { bad: 1 }] }
    fetchMock.mockResolvedValue(new Response(JSON.stringify(body), { status: 400, headers: { 'Content-Type': 'application/problem+json' } }))
    const error = await request('/api/v1/roles', { method: 'POST', body: {} }).catch((reason: unknown) => reason)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ message: 'name must not be blank', status: 400,
      problem: { code: 'GF-COMMON-400', messageKey: 'error.common.bad-request', requestId: 'req-1' } })
    expect((error as ApiError).details).toEqual([{ field: 'name', message: 'must not be blank' }])
  })
  it('hides server error details and shows the request id instead', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ status: 500, detail: 'internal', code: 'GF-COMMON-500', requestId: 'r-9' }), { status: 500 }))
    await expect(request('/api/v1/x')).rejects.toMatchObject({ message: '服务暂时不可用，请稍后重试（请求编号 r-9）', status: 500 })
    expect(problemMessage({ status: 503 })).toBe('服务暂时不可用，请稍后重试')
    expect(problemMessage({ status: 404, title: 'Not Found' })).toBe('Not Found')
    expect(problemMessage({ status: 403 })).toBe('你没有执行此操作的权限')
  })
  it('signs out on problem and plain 401 responses', async () => {
    const expired = vi.fn(); onUnauthorized(expired)
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ status: 401, code: 'GF-COMMON-401' }), { status: 401 }))
    fetchMock.mockResolvedValueOnce(new Response('', { status: 401 }))
    await expect(request('/api/v1/me')).rejects.toMatchObject({ status: 401 })
    await expect(request('/api/v1/me')).rejects.toMatchObject({ status: 401, message: '登录已失效，请重新登录' })
    expect(expired).toHaveBeenCalledTimes(2)
  })
  it('reports failures without a readable body by status', async () => {
    fetchMock.mockResolvedValue(new Response('<html>', { status: 502 }))
    await expect(request('/api/v1/x')).rejects.toMatchObject({ status: 502, message: '请求失败（502）' })
  })
  it('sends the CSRF cookie as a header on unsafe methods only', async () => {
    document.cookie = 'XSRF-TOKEN=abc%3D1; path=/'
    fetchMock.mockResolvedValue(new Response('null', { status: 200 }))
    await request('/api/v1/roles', { method: 'POST', body: {} })
    await request('/api/v1/roles')
    expect(fetchMock.mock.calls[0]?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'abc=1' })
    expect(fetchMock.mock.calls[1]?.[1]?.headers).not.toHaveProperty('X-XSRF-TOKEN')
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })
  it('fetches a CSRF token before an unsafe call when the cookie is missing', async () => {
    fetchMock.mockImplementation(async input => {
      if (String(input).endsWith('/api/v1/bootstrap')) document.cookie = 'XSRF-TOKEN=fresh; path=/'
      return new Response('null', { status: 200 })
    })
    await request('/api/v1/auth/login', { method: 'POST', body: {}, anonymous: true })
    expect(fetchMock.mock.calls.map(([input]) => String(input))).toEqual(['/api/v1/bootstrap', '/api/v1/auth/login'])
    expect(fetchMock.mock.calls[1]?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'fresh' })
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })
  it('retries once with a fresh token when the server rejects a stale one', async () => {
    document.cookie = 'XSRF-TOKEN=stale; path=/'
    const stale = () => new Response(JSON.stringify({ status: 403, code: 'GF-SECURITY-001', detail: '页面已过期，请刷新后重试。' }),
      { status: 403, headers: { 'Content-Type': 'application/problem+json' } })
    fetchMock.mockImplementation(async input => {
      if (String(input).endsWith('/api/v1/bootstrap')) { document.cookie = 'XSRF-TOKEN=fresh; path=/'; return new Response('{}') }
      return stale()
    })
    await expect(request('/api/v1/me/password', { method: 'POST', body: {} })).rejects.toMatchObject({ status: 403, message: '页面已过期，请刷新后重试。' })
    expect(fetchMock.mock.calls.map(([input]) => String(input))).toEqual(['/api/v1/me/password', '/api/v1/bootstrap', '/api/v1/me/password'])
    expect(fetchMock.mock.calls[2]?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'fresh' })

    fetchMock.mockClear()
    fetchMock.mockImplementation(async () => { throw new TypeError('offline') })
    await expect(request('/api/v1/me/password', { method: 'POST', body: {} })).rejects.toMatchObject({ message: '无法连接服务，请检查网络后重试' })
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })
  it('reads cookies safely', () => {
    document.cookie = 'broken=%E0%A4%A; path=/'
    expect(readCookie('broken')).toBe('')
    expect(readCookie('absent')).toBe('')
    document.cookie = 'broken=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })
})
