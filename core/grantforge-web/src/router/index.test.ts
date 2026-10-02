// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: router } = await import('./index')
const { useAuth } = await import('@/stores/auth')

const me = { username: 'admin', tenantCode: 'default', tenantName: 'Default', systemAccount: true, passwordChangeRequired: false }

/** Answers like a server without a session, in the given bootstrap state. */
function signedOut(bootstrap: { setupRequired: boolean; registrationEnabled: boolean }) {
  api.request.mockImplementation((path: string) => path === '/api/v1/me'
    ? Promise.reject(new ApiError('signed out', 401)) : Promise.resolve(bootstrap))
}

/** Answers like a server with a valid session that grants the given console resources. */
function signIn(allowed: string[]) {
  api.request.mockImplementation((path: string) => {
    if (path === '/api/v1/me/authorization') return Promise.resolve({ version: 1, unrestricted: false, roles: [], resources: allowed, permissions: [] })
    if (path === '/api/v1/me') return Promise.resolve(me)
    return Promise.resolve({ setupRequired: false, registrationEnabled: false })
  })
}

describe('router guards', () => {
  beforeEach(async () => {
    localStorage.clear()
    api.request.mockReset()
    setActivePinia(createPinia())
    await router.replace('/common/404')
    // The guard creates the auth store on first use; a fresh store per test reads the session set by the test.
    setActivePinia(createPinia())
  })

  it('sends anonymous visitors of protected pages to login with a redirect back', async () => {
    signedOut({ setupRequired: false, registrationEnabled: false })
    await router.push('/admin/users')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/admin/users')
  })

  it('lets signed-in users open pages they were granted and sets the title', async () => {
    signIn(['system.user'])
    await router.push('/admin/users')
    expect(router.currentRoute.value.path).toBe('/admin/users')
    expect(document.title).toBe('用户管理 · GrantForge')
  })

  it('redirects to 403 for pages they were not granted', async () => {
    signIn(['system.user'])
    await router.push('/admin/groups')
    expect(router.currentRoute.value.path).toBe('/common/403')
  })

  it('confines users who must change their password to the account page', async () => {
    api.request.mockImplementation((path: string) => {
      if (path === '/api/v1/me') return Promise.resolve({ ...me, passwordChangeRequired: true })
      if (path === '/api/v1/me/authorization') return Promise.resolve({ version: 0, unrestricted: true, roles: [], resources: [], permissions: [] })
      return Promise.resolve({ setupRequired: false, registrationEnabled: false })
    })
    await router.push('/admin/users')
    expect(router.currentRoute.value.name).toBe('account')
    expect(document.title).toBe('个人中心 · GrantForge')
  })

  it('logs out and returns to login when the stored session cannot be restored', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/me'
      ? Promise.reject(new Error('expired')) : Promise.resolve({ setupRequired: false }))
    await router.push('/dashboard')
    expect(router.currentRoute.value.name).toBe('login')
    expect(useAuth().authenticated).toBe(false)
  })

  it('keeps signed-in users away from the login and register pages', async () => {
    signIn(['/admin/users'])
    await router.push('/auth/register')
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('sends every visitor to setup while it is pending', async () => {
    api.request.mockResolvedValue({ setupRequired: true, registrationEnabled: false })
    await router.push('/admin/users')
    expect(router.currentRoute.value.name).toBe('setup')
    await router.push('/auth/login')
    expect(router.currentRoute.value.name).toBe('setup')
  })

  it('closes the setup page once setup is done', async () => {
    signedOut({ setupRequired: false, registrationEnabled: false })
    await router.push('/setup')
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('opens registration only when the server enables it', async () => {
    signedOut({ setupRequired: false, registrationEnabled: false })
    await router.push('/auth/register')
    expect(router.currentRoute.value.name).toBe('login')

    setActivePinia(createPinia())
    signedOut({ setupRequired: false, registrationEnabled: true })
    await router.push('/auth/register')
    expect(router.currentRoute.value.name).toBe('register')
  })

  it('redirects the root to the dashboard and unknown paths to 404', async () => {
    signIn([])
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/dashboard')
    await router.push('/missing/page')
    expect(router.currentRoute.value.path).toBe('/common/404')
    expect(document.title).toBe('权限工作台 · GrantForge')
  })
})
