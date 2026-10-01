// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { MenuTree, User } from '@/types/api'

const api = vi.hoisted(() => ({ authenticate: vi.fn(), request: vi.fn() }))
vi.mock('@/lib/api', () => api)

const { useAuth } = await import('./auth')

const admin: User = { id: 2, name: 'admin' }
const navigation: MenuTree[] = [{ id: 1, title: '用户', url: '/admin/users' }]

function answer(path: string) {
  if (path.startsWith('/api/v1/user/info/')) return Promise.resolve(admin)
  if (path === '/api/v1/role/menu') return Promise.resolve(navigation)
  return Promise.reject(new Error('unexpected ' + path))
}

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    api.authenticate.mockReset()
    api.request.mockReset()
    api.request.mockImplementation(answer)
  })

  it('logs in, stores the session and loads the profile and navigation', async () => {
    api.authenticate.mockResolvedValue('token-1')
    const auth = useAuth()

    await auth.login('admin', 'secret')

    expect(api.authenticate).toHaveBeenCalledWith('admin', 'secret')
    expect(auth.authenticated).toBe(true)
    expect(auth.user).toEqual(admin)
    expect(auth.navigationReady).toBe(true)
    expect(localStorage.getItem('AuthXToken')).toBe('token-1')
    expect(auth.canVisit('/admin/users')).toBe(true)
    expect(auth.canVisit('/admin/roles')).toBe(false)
    expect(auth.canVisit('/dashboard')).toBe(true)
  })

  it('rejects a login response without a token', async () => {
    api.authenticate.mockResolvedValue('')
    await expect(useAuth().login('admin', 'x')).rejects.toThrow('登录响应缺少有效令牌')
  })

  it('clears the session when loading the profile fails after login', async () => {
    api.authenticate.mockResolvedValue('token-1')
    api.request.mockRejectedValue(new Error('down'))
    const auth = useAuth()
    await expect(auth.login('admin', 'x')).rejects.toThrow('down')
    expect(auth.authenticated).toBe(false)
    expect(localStorage.getItem('AuthXToken')).toBeNull()
  })

  it('keeps working without navigation and allows every page until it loads', async () => {
    api.authenticate.mockResolvedValue('token-1')
    api.request.mockImplementation((path: string) => path === '/api/v1/role/menu' ? Promise.reject(new Error('x')) : answer(path))
    const auth = useAuth()
    await auth.login('admin', 'x')
    expect(auth.navigationReady).toBe(false)
    expect(auth.navigationError).toBe('导航权限暂未加载，可重新获取')
    expect(auth.canVisit('/admin/roles')).toBe(true)
  })

  it('hydrates a stored session only once, even for concurrent callers', async () => {
    localStorage.setItem('AuthXToken', 'stored')
    localStorage.setItem('GrantForgeUserName', 'admin')
    const auth = useAuth()
    await Promise.all([auth.hydrate(), auth.hydrate()])
    await auth.hydrate()
    expect(api.request.mock.calls.filter(([path]) => String(path).startsWith('/api/v1/user/info/'))).toHaveLength(1)
    expect(auth.user).toEqual(admin)
  })

  it('logs out when a stored token has no username or the profile is empty', async () => {
    localStorage.setItem('AuthXToken', 'opaque')
    const auth = useAuth()
    await auth.hydrate()
    expect(auth.authenticated).toBe(false)

    localStorage.setItem('AuthXToken', 'stored')
    localStorage.setItem('GrantForgeUserName', 'ghost')
    setActivePinia(createPinia())
    api.request.mockResolvedValue(null)
    const other = useAuth()
    await other.hydrate()
    expect(other.authenticated).toBe(false)
  })

  it('does nothing when there is no session', async () => {
    await useAuth().hydrate()
    expect(api.request).not.toHaveBeenCalled()
  })

  it('logout clears every piece of session state', async () => {
    api.authenticate.mockResolvedValue('token-1')
    const auth = useAuth()
    await auth.login('admin', 'x')
    auth.logout()
    expect(auth.user).toBeNull()
    expect(auth.navigation).toEqual([])
    expect(auth.username).toBe('')
    expect(localStorage.getItem('GrantForgeUserName')).toBeNull()
  })
})
