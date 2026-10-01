// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ authenticate: vi.fn(), request: vi.fn() }))
vi.mock('@/lib/api', () => api)

const { default: router } = await import('./index')
const { useAuth } = await import('@/stores/auth')

function signIn(allowed: string[]) {
  localStorage.setItem('AuthXToken', 'token')
  localStorage.setItem('GrantForgeUserName', 'admin')
  api.request.mockImplementation((path: string) => path === '/api/v1/role/menu'
    ? Promise.resolve(allowed.map((url, index) => ({ id: index + 1, title: url, url })))
    : Promise.resolve({ id: 2, name: 'admin' }))
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
    await router.push('/admin/users')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/admin/users')
  })

  it('lets signed-in users open pages their navigation grants and sets the title', async () => {
    signIn(['/admin/users'])
    await router.push('/admin/users')
    expect(router.currentRoute.value.path).toBe('/admin/users')
    expect(document.title).toBe('用户管理 · GrantForge')
  })

  it('redirects to 403 for pages outside the navigation', async () => {
    signIn(['/admin/users'])
    await router.push('/admin/roles')
    expect(router.currentRoute.value.path).toBe('/common/403')
  })

  it('logs out and returns to login when the stored session cannot be restored', async () => {
    localStorage.setItem('AuthXToken', 'token')
    localStorage.setItem('GrantForgeUserName', 'admin')
    api.request.mockRejectedValue(new Error('expired'))
    await router.push('/dashboard')
    expect(router.currentRoute.value.name).toBe('login')
    expect(useAuth().authenticated).toBe(false)
  })

  it('keeps signed-in users away from the login and register pages', async () => {
    signIn(['/admin/users'])
    await router.push('/auth/register')
    expect(router.currentRoute.value.path).toBe('/dashboard')
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
