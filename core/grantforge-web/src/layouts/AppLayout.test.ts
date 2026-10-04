// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useAuth } from '@/stores/auth'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: AppLayout } = await import('./AppLayout.vue')

async function mountLayout() {
  const mounted = await mountView(AppLayout, { global: { stubs: { RouterView: true } } }, '/dashboard')
  const auth = useAuth()
  auth.authorization = { version: 1, unrestricted: false, roles: [], resources: ['system.user'], permissions: [], fields: {} }
  await nextTick()
  return mounted
}

describe('app layout', () => {
  beforeEach(() => { localStorage.clear(); api.request.mockReset() })
  afterEach(() => { document.body.innerHTML = ''; document.documentElement.classList.remove('dark') })

  it('shows only the pages the user may reach', async () => {
    const { wrapper } = await mountLayout()
    const links = wrapper.get('nav[aria-label="主导航"]').findAll('a').map(a => a.text())
    expect(links).toEqual(['概览', '我的申请', '用户管理', 'JSON 工作台'])
    wrapper.unmount()
  })

  it('switches and remembers the colour theme', async () => {
    const { wrapper } = await mountLayout()
    await wrapper.get('[aria-label="切换深色主题"]').trigger('click')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem('GrantForgeTheme')).toBe('dark')
    await wrapper.get('[aria-label="切换浅色主题"]').trigger('click')
    expect(localStorage.getItem('GrantForgeTheme')).toBe('light')
    wrapper.unmount()
  })

  it('switches the interface language', async () => {
    const { wrapper } = await mountLayout()
    await wrapper.get('[aria-label="Switch to English"]').trigger('click')
    expect(wrapper.get('nav').findAll('a').map(a => a.text())).toContain('Users')
    expect(document.documentElement.lang).toBe('en-US')
    await wrapper.get('[aria-label="切换到中文"]').trigger('click')
    expect(wrapper.get('nav').findAll('a').map(a => a.text())).toContain('用户管理')
    wrapper.unmount()
  })

  it('opens quick navigation with Ctrl+K, filters pages and navigates', async () => {
    const { wrapper, router } = await mountLayout()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }))
    await nextTick()
    const search = document.querySelector<HTMLInputElement>('[aria-label="搜索页面"]')
    expect(document.querySelector('dialog')?.hasAttribute('open')).toBe(true)
    if (!search) throw new Error('missing search')
    search.value = '用户'
    search.dispatchEvent(new Event('input'))
    await nextTick()
    const results = [...document.querySelectorAll('dialog button')].filter(button => button.textContent?.includes('用户管理'))
    expect(results).toHaveLength(1)
    ;(results[0] as HTMLButtonElement).click()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/users')
    search.value = 'zzz'
    search.dispatchEvent(new Event('input'))
    await nextTick()
    expect(document.querySelector('dialog')?.textContent).toContain('没有匹配的页面')
    wrapper.unmount()
  })

  it('opens and closes the mobile navigation', async () => {
    const { wrapper } = await mountLayout()
    await wrapper.get('[aria-label="打开导航"]').trigger('click')
    expect(wrapper.find('[aria-label="关闭导航"]').exists()).toBe(true)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(wrapper.find('[aria-label="关闭导航"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('offers a retry when the authorization failed to load', async () => {
    const { wrapper } = await mountLayout()
    const auth = useAuth()
    auth.authorizationError = '导航权限暂未加载，可重新获取'
    await nextTick()
    api.request.mockResolvedValue([])
    await wrapper.findAll('button').find(button => button.text() === '重试')?.trigger('click')
    expect(api.request).toHaveBeenCalledWith('/api/v1/me/authorization')
    wrapper.unmount()
  })

  it('signs out on the server and returns to the login page', async () => {
    const { wrapper, router } = await mountLayout()
    api.request.mockResolvedValue(null)
    const logout = [...wrapper.findAll('button')].find(button => button.text().includes('退出登录'))
    expect(logout).toBeDefined()
    await logout?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/auth/login')
    expect(api.request).toHaveBeenCalledWith('/api/v1/auth/logout', { method: 'POST', anonymous: true })
    expect(useAuth().authenticated).toBe(false)
    wrapper.unmount()
  })

  it('links the signed-in user to the account page', async () => {
    const { wrapper } = await mountLayout()
    const link = wrapper.findAll('header a').find(anchor => anchor.text().includes('个人中心'))
    expect(link?.attributes('href')).toBe('/account')
    wrapper.unmount()
  })
})
