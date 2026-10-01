// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setLocale } from '@/i18n'
import { useBootstrap } from '@/stores/bootstrap'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: AuthView } = await import('./AuthView.vue')

async function submit(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], values: Record<string, string>) {
  const inputs = wrapper.findAll('input')
  if (values.name !== undefined) await inputs[0]?.setValue(values.name)
  if (values.password !== undefined) await inputs[1]?.setValue(values.password)
  if (values.confirmation !== undefined) await inputs[2]?.setValue(values.confirmation)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('auth view', () => {
  beforeEach(() => {
    localStorage.clear()
    api.request.mockReset()
  })

  it('focuses the username and validates the login form', async () => {
    const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
    expect(document.activeElement).toBe(wrapper.findAll('input')[0]?.element)
    await submit(wrapper, {})
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入用户名')
    await submit(wrapper, { name: 'admin' })
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入密码')
    wrapper.unmount()
  })

  it('logs in and returns to a same-site redirect only', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(path === '/api/v1/auth/login'
      ? { username: 'admin', tenantCode: 'default', tenantName: 'Default', systemAccount: true, passwordChangeRequired: false } : []))
    const { wrapper, router } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?redirect=/admin/users')
    await submit(wrapper, { name: ' admin ', password: 'secret' })
    expect(api.request).toHaveBeenCalledWith('/api/v1/auth/login',
      { method: 'POST', anonymous: true, body: { username: 'admin', password: 'secret' } })
    expect(router.currentRoute.value.path).toBe('/admin/users')
    wrapper.unmount()

    const other = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?redirect=//evil.example')
    await submit(other.wrapper, { name: 'admin', password: 'secret' })
    expect(other.router.currentRoute.value.path).toBe('/dashboard')
    other.wrapper.unmount()
  })

  it('shows server errors', async () => {
    api.request.mockRejectedValue(new Error('用户名或密码错误'))
    const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
    await submit(wrapper, { name: 'admin', password: 'wrong' })
    expect(wrapper.get('[role="alert"]').text()).toBe('用户名或密码错误')
    wrapper.unmount()
  })

  it('validates registration and confirms a created account', async () => {
    api.request.mockResolvedValue(7)
    const { wrapper } = await mountView(AuthView, { props: { mode: 'register' } }, '/auth/register')
    await submit(wrapper, { name: 'alex', password: 'short' })
    expect(wrapper.get('[role="alert"]').text()).toBe('密码至少需要 8 个字符')
    await submit(wrapper, { password: 'long-enough' })
    expect(wrapper.get('[role="alert"]').text()).toBe('请再次输入密码')
    await submit(wrapper, { confirmation: 'different' })
    expect(wrapper.get('[role="alert"]').text()).toBe('两次输入的密码不一致')
    await submit(wrapper, { confirmation: 'long-enough' })
    expect(api.request).toHaveBeenCalledWith('/api/v1/user/register', expect.objectContaining({ method: 'POST', anonymous: true }))
    expect(wrapper.get('h2').text()).toBe('账号已创建')
    wrapper.unmount()
  })

  it('links to registration only when the server enables it', async () => {
    const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
    expect(wrapper.find('a[href="/auth/register"]').exists()).toBe(false)
    useBootstrap().registrationEnabled = true
    await flushPromises()
    expect(wrapper.find('a[href="/auth/register"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('runs first-run setup with the token from the server log', async () => {
    api.request.mockResolvedValue({ tenantCode: 'default', username: 'admin' })
    const { wrapper } = await mountView(AuthView, { props: { mode: 'setup' } }, '/setup')
    const bootstrap = useBootstrap()
    bootstrap.setupRequired = true
    const inputs = wrapper.findAll('input')
    expect(inputs).toHaveLength(5)
    expect(document.activeElement).toBe(inputs[0]?.element)
    expect(wrapper.find('a[href="/auth/register"]').exists()).toBe(false)

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('请输入初始化令牌')

    await inputs[0]?.setValue(' the-token ')
    await inputs[2]?.setValue(' admin ')
    await inputs[3]?.setValue('a long password')
    await inputs[4]?.setValue('another password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('两次输入的密码不一致')

    await inputs[4]?.setValue('a long password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/setup', { method: 'POST', anonymous: true,
      body: { token: 'the-token', tenantName: undefined, username: 'admin', password: 'a long password' } })
    expect(wrapper.get('h2').text()).toBe('初始化完成')
    expect(bootstrap.setupRequired).toBe(false)
    wrapper.unmount()
  })

  it('shows why the server rejected setup', async () => {
    api.request.mockRejectedValue(new Error('初始化令牌无效'))
    const { wrapper } = await mountView(AuthView, { props: { mode: 'setup' } }, '/setup')
    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('wrong')
    await inputs[1]?.setValue('Acme')
    await inputs[2]?.setValue('admin')
    await inputs[3]?.setValue('a long password')
    await inputs[4]?.setValue('a long password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/setup', expect.objectContaining({
      body: expect.objectContaining({ tenantName: 'Acme' }) }))
    expect(wrapper.get('[role="alert"]').text()).toBe('初始化令牌无效')
    wrapper.unmount()
  })

  it('renders in English when the interface language is English', async () => {
    setLocale('en-US')
    try {
      const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
      expect(wrapper.get('h2').text()).toBe('Welcome back')
      expect(wrapper.get('button[type="submit"]').text()).toContain('Sign in')
      await submit(wrapper, {})
      expect(wrapper.get('[role="alert"]').text()).toBe('Enter a username')
      wrapper.unmount()
    } finally {
      setLocale('zh-CN')
    }
  })

  it('toggles password visibility and resets the form when switching modes', async () => {
    const { wrapper } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login')
    await wrapper.get('[aria-label="显示密码"]').trigger('click')
    expect(wrapper.findAll('input')[1]?.attributes('type')).toBe('text')
    await wrapper.findAll('input')[1]?.setValue('x')
    await wrapper.setProps({ mode: 'register' })
    expect((wrapper.findAll('input')[1]?.element as HTMLInputElement).value).toBe('')
    expect(wrapper.findAll('input')).toHaveLength(3)
    wrapper.unmount()
  })
})
