// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { useAuth } = await import('./auth')

const me = { username: 'admin', displayName: 'The Admin', tenantCode: 'default', tenantName: 'Default',
  systemAccount: true, passwordChangeRequired: false }
const authorization = { version: 3, unrestricted: false, resources: ['system.user'] }

function answer(path: string) {
  if (path === '/api/v1/me' || path === '/api/v1/auth/login') return Promise.resolve(me)
  if (path === '/api/v1/me/authorization') return Promise.resolve(authorization)
  if (path === '/api/v1/auth/logout') return Promise.resolve(null)
  return Promise.reject(new Error('unexpected ' + path))
}

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    api.request.mockReset()
    api.request.mockImplementation(answer)
  })

  it('signs in through the API, remembers the name and loads what the user may reach', async () => {
    const auth = useAuth()

    await auth.login('admin', 'secret')

    expect(api.request).toHaveBeenCalledWith('/api/v1/auth/login',
      { method: 'POST', anonymous: true, body: { username: 'admin', password: 'secret' } })
    expect(auth.authenticated).toBe(true)
    expect(auth.user).toEqual({ name: 'The Admin' })
    expect(localStorage.getItem('GrantForgeUserName')).toBe('admin')
    expect(auth.authorization).toEqual(authorization)
    expect(auth.canVisit('/admin/users')).toBe(true)
    expect(auth.canVisit('/admin/roles')).toBe(false)
    expect(auth.canVisit('/dashboard')).toBe(true)
  })

  it('names users without a display name by their login name', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/auth/login'
      ? Promise.resolve({ ...me, displayName: undefined }) : answer(path))
    const auth = useAuth()
    await auth.login('admin', 'secret')
    expect(auth.user).toEqual({ name: 'admin' })
  })

  it('keeps rejected sign-ins local and signed out', async () => {
    api.request.mockRejectedValue(new ApiError('用户名或密码错误。', 401))
    const auth = useAuth()
    await expect(auth.login('admin', 'x')).rejects.toThrow('用户名或密码错误。')
    expect(auth.authenticated).toBe(false)
  })

  it('lets unrestricted users reach every page', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/me/authorization'
      ? Promise.resolve({ version: 0, unrestricted: true, resources: [] }) : answer(path))
    const auth = useAuth()
    await auth.login('admin', 'x')
    expect(auth.canVisit('/admin/roles')).toBe(true)
  })

  it('keeps working without the authorization and allows every page until it loads', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/me/authorization' ? Promise.reject(new Error('x')) : answer(path))
    const auth = useAuth()
    await auth.login('admin', 'x')
    expect(auth.authorization).toBeNull()
    expect(auth.authorizationError).toBe('导航权限暂未加载，可重新获取')
    expect(auth.canVisit('/admin/roles')).toBe(true)

    api.request.mockImplementation(answer)
    await auth.loadAuthorization()
    expect(auth.authorizationError).toBe('')
    expect(auth.canVisit('/admin/roles')).toBe(false)
  })

  it('restores the session once, even for concurrent callers, and drops legacy tokens', async () => {
    localStorage.setItem('AuthXToken', 'legacy')
    const auth = useAuth()

    await Promise.all([auth.restore(), auth.restore()])
    await auth.restore()

    expect(api.request.mock.calls.filter(([path]) => path === '/api/v1/me')).toHaveLength(1)
    expect(api.request).toHaveBeenCalledWith('/api/v1/me', { anonymous: true })
    expect(auth.authenticated).toBe(true)
    expect(localStorage.getItem('AuthXToken')).toBeNull()
  })

  it('treats 401 as signed out but lets other failures surface and retry', async () => {
    api.request.mockRejectedValueOnce(new Error('offline'))
    const auth = useAuth()
    await expect(auth.restore()).rejects.toThrow('offline')

    api.request.mockRejectedValueOnce(new ApiError('signed out', 401))
    await auth.restore()
    expect(auth.authenticated).toBe(false)
    await auth.restore()
    expect(api.request).toHaveBeenCalledTimes(2)
  })

  it('signs out on the server and locally, even when the server is unreachable', async () => {
    const auth = useAuth()
    await auth.login('admin', 'x')

    await auth.logout()
    expect(api.request).toHaveBeenCalledWith('/api/v1/auth/logout', { method: 'POST', anonymous: true })
    expect(auth.authenticated).toBe(false)
    expect(auth.authorization).toBeNull()

    await auth.login('admin', 'x')
    api.request.mockRejectedValue(new Error('offline'))
    await auth.logout()
    expect(auth.authenticated).toBe(false)
  })

  it('takes over the user returned after a change and knows when a new password is due', async () => {
    const auth = useAuth()
    await auth.login('admin', 'x')
    expect(auth.passwordChangeRequired).toBe(false)
    auth.updated({ ...me, displayName: 'Renamed', passwordChangeRequired: true })
    expect(auth.user).toEqual({ name: 'Renamed' })
    expect(auth.passwordChangeRequired).toBe(true)
  })

  it('resets local state without calling the server', async () => {
    const auth = useAuth()
    await auth.login('admin', 'x')
    api.request.mockClear()
    auth.reset()
    expect(auth.authenticated).toBe(false)
    expect(api.request).not.toHaveBeenCalled()
  })
})
