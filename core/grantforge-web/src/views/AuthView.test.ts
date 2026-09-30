// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ authenticate: vi.fn(), request: vi.fn() }))
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
    api.authenticate.mockReset()
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
    api.authenticate.mockResolvedValue('token')
    api.request.mockImplementation((path: string) => Promise.resolve(path.includes('/user/info/') ? { id: 1, name: 'admin' } : []))
    const { wrapper, router } = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?redirect=/admin/users')
    await submit(wrapper, { name: ' admin ', password: 'secret' })
    expect(api.authenticate).toHaveBeenCalledWith('admin', 'secret')
    expect(router.currentRoute.value.path).toBe('/admin/users')
    wrapper.unmount()

    const other = await mountView(AuthView, { props: { mode: 'login' } }, '/auth/login?redirect=//evil.example')
    await submit(other.wrapper, { name: 'admin', password: 'secret' })
    expect(other.router.currentRoute.value.path).toBe('/dashboard')
    other.wrapper.unmount()
  })

  it('shows server errors', async () => {
    api.authenticate.mockRejectedValue(new Error('用户名或密码错误'))
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
