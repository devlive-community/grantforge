// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, authenticate, onUnauthorized, request } from './api'
import { TOKEN_KEY } from './session'
const fetchMock = vi.fn<typeof fetch>()
const response = (data: unknown, code = 2000, status = 200) => new Response(JSON.stringify({ code, message: 'test message', data }), { status })
beforeEach(() => { localStorage.clear(); vi.stubGlobal('fetch', fetchMock); fetchMock.mockReset() })
afterEach(() => vi.unstubAllGlobals())
describe('API contract', () => {
  it.each(['GET', 'POST', 'PUT', 'DELETE'] as const)('attaches fresh bearer credentials to %s', async method => {
    localStorage.setItem(TOKEN_KEY, 'current-token')
    fetchMock.mockResolvedValue(response({ id: 1 }))
    await request('/api/v1/user', { method, ...(method === 'GET' || method === 'DELETE' ? { query: { id: 1 } } : { body: { id: 1 } }) })
    expect(fetchMock.mock.calls[0]?.[1]?.headers).toMatchObject({ Authorization: 'Bearer current-token' })
  })
  it('sends login credentials only in the form body', async () => {
    fetchMock.mockResolvedValue(response('token'))
    expect(await authenticate('a&b', 'p?word')).toBe('token')
    const [url, options] = fetchMock.mock.calls[0]!
    expect(url).toBe('/oauth/token')
    expect((options?.body as URLSearchParams).get('password')).toBe('p?word')
    expect((options?.body as URLSearchParams).get('username')).toBe('a&b')
    expect(options?.headers).toMatchObject({ 'Content-Type': 'application/x-www-form-urlencoded' })
  })
  it('clears authenticated state on unauthorized responses', async () => {
    const expired = vi.fn(); onUnauthorized(expired)
    fetchMock.mockResolvedValue(response(null, 4001, 401))
    await expect(request('/api/v1/user')).rejects.toMatchObject({ status: 401 })
    expect(expired).toHaveBeenCalledOnce()
  })
  it('keeps credential rejection local to the login form', async () => {
    const expired = vi.fn(); onUnauthorized(expired)
    fetchMock.mockResolvedValue(response(null, 4002, 401))
    await expect(authenticate('admin', 'wrong')).rejects.toBeInstanceOf(ApiError)
    expect(expired).not.toHaveBeenCalled()
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
})
