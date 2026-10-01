// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: AccountView } = await import('./AccountView.vue')

const me = { username: 'alice', displayName: 'Alice', email: 'alice@example.org', tenantCode: 'acme', tenantName: 'Acme',
  systemAccount: false, passwordChangeRequired: false }
const firefox = 'Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0'
const sessions = [
  { id: '1', username: 'alice', userAgent: firefox, clientIp: '10.0.0.1', signedInAt: '2026-10-01T08:00:00Z', lastSeenAt: '2026-10-01T09:00:00Z', current: true },
  { id: '2', username: 'alice', signedInAt: '2026-10-01T07:00:00Z', lastSeenAt: '2026-10-01T07:30:00Z', current: false },
]

const history = [
  { occurredAt: '2026-10-01T09:00:00Z', action: 'LOGIN_SUCCEEDED', outcome: 'SUCCESS', userAgent: firefox, clientIp: '10.0.0.1' },
  { occurredAt: '2026-10-01T08:59:00Z', action: 'LOGIN_FAILED', outcome: 'FAILURE', reason: 'GF-IDENTITY-020', clientIp: '10.0.0.9' },
  { occurredAt: '2026-10-01T08:58:00Z', action: 'LOGIN_FAILED', outcome: 'FAILURE', reason: 'GF-FUTURE-001' },
]

function answer(path: string, options?: { method?: string; body?: { displayName?: string; email?: string } }) {
  if (path === '/api/v1/me/sessions') return Promise.resolve(sessions)
  if (path === '/api/v1/me/login-history') return Promise.resolve({ items: history, page: 1, size: 10, total: 3 })
  if (path === '/api/v1/me' && options?.method === 'PUT') return Promise.resolve({ ...me, displayName: options.body?.displayName || undefined, email: options.body?.email || undefined })
  if (path === '/api/v1/me') return Promise.resolve(me)
  return Promise.resolve(null)
}

async function mountAccount(profile = me) {
  const mounted = await mountView(AccountView, {}, '/account')
  useAuth().updated(profile)
  await flushPromises()
  return mounted
}

function field(wrapper: VueWrapper, label: string) {
  const id = wrapper.findAll('label').find(item => item.text().replace('*', '').trim() === label)?.attributes('for')
  return wrapper.get(`[id="${id ?? 'missing'}"]`)
}

function button(wrapper: VueWrapper, label: string) {
  const found = wrapper.findAll('button').find(item => item.text().trim() === label)
  if (!found) throw new Error('missing button ' + label)
  return found
}

describe('account view', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows the profile and the signed-in devices', async () => {
    const { wrapper } = await mountAccount()
    expect(wrapper.text()).toContain('Acme')
    expect((field(wrapper, '显示名称').element as HTMLInputElement).value).toBe('Alice')
    expect(wrapper.text()).toContain('Firefox · Linux')
    expect(wrapper.text()).toContain('当前会话')
    wrapper.unmount()
  })

  it('saves the profile and reports invalid values', async () => {
    const { wrapper } = await mountAccount()
    await field(wrapper, '显示名称').setValue('爱丽丝')
    await wrapper.get('form#profile').trigger('submit')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/me', { method: 'PUT', body: { displayName: '爱丽丝', email: 'alice@example.org' } })
    expect(useAuth().me?.displayName).toBe('爱丽丝')
    expect(useToast().items.map(item => item.message)).toContain('资料已保存')

    api.request.mockRejectedValueOnce(new ApiError('请求参数无效。', 400))
    await wrapper.get('form#profile').trigger('submit')
    await flushPromises()
    expect(wrapper.get('form#profile [role="alert"]').text()).toBe('请求参数无效。')
    wrapper.unmount()
  })

  it('checks the password form before changing the password', async () => {
    const { wrapper } = await mountAccount()
    const form = wrapper.get('form#password'), alert = () => wrapper.get('form#password [role="alert"]').text()
    await form.trigger('submit')
    expect(alert()).toBe('请输入当前密码')
    await field(wrapper, '当前密码').setValue('old password')
    await form.trigger('submit')
    expect(alert()).toBe('请输入新密码')
    await field(wrapper, '新密码').setValue('new password one')
    await field(wrapper, '确认新密码').setValue('new password two')
    await form.trigger('submit')
    expect(alert()).toBe('两次输入的新密码不一致')

    api.request.mockRejectedValueOnce(new ApiError('当前密码不正确。', 400))
    await field(wrapper, '确认新密码').setValue('new password one')
    await form.trigger('submit')
    await flushPromises()
    expect(alert()).toBe('当前密码不正确。')

    await form.trigger('submit')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/me/password', { method: 'POST', body: { currentPassword: 'old password', newPassword: 'new password one' } })
    expect((field(wrapper, '当前密码').element as HTMLInputElement).value).toBe('')
    expect(useToast().items.map(item => item.message)).toContain('密码已修改，其他设备上的会话已结束')
    wrapper.unmount()
  })

  it('asks for a new password first and shows the rest once it is set', async () => {
    const { wrapper } = await mountAccount({ ...me, passwordChangeRequired: true })
    expect(wrapper.text()).toContain('请先设置新密码')
    expect(wrapper.find('form#profile').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('我的登录设备')

    await field(wrapper, '当前密码').setValue('old password')
    await field(wrapper, '新密码').setValue('new password one')
    await field(wrapper, '确认新密码').setValue('new password one')
    await wrapper.get('form#password').trigger('submit')
    await flushPromises()

    expect(useAuth().passwordChangeRequired).toBe(false)
    expect(wrapper.text()).not.toContain('请先设置新密码')
    expect(wrapper.text()).toContain('我的登录设备')
    wrapper.unmount()
  })

  it('ends other sessions and signs out when ending the current one', async () => {
    const { wrapper, router } = await mountAccount()
    await wrapper.get('[aria-label="结束 — 上的会话"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/me/sessions/2', { method: 'DELETE' })
    expect(useToast().items.map(item => item.message)).toContain('会话已结束')

    api.request.mockRejectedValueOnce(new ApiError('会话不存在。', 404))
    await wrapper.get('[aria-label="结束 — 上的会话"]').trigger('click')
    await flushPromises()
    expect(useToast().items.map(item => item.message)).toContain('会话已结束')
    expect(useToast().items.at(-1)).toMatchObject({ message: '会话不存在。', kind: 'error' })

    await wrapper.get('[aria-label="结束 Firefox · Linux 上的会话"]').trigger('click')
    await flushPromises()
    expect(useAuth().authenticated).toBe(false)
    expect(router.currentRoute.value.path).toBe('/auth/login')
    wrapper.unmount()
  })

  it('lists recent sign-ins with readable reasons', async () => {
    const { wrapper } = await mountAccount()
    expect(api.request).toHaveBeenCalledWith('/api/v1/me/login-history', { query: { page: 1, size: 10 } })
    const items = wrapper.findAll('section').at(-1)?.findAll('li').map(item => item.text()) ?? []
    expect(items).toHaveLength(3)
    expect(items[0]).toContain('登录成功')
    expect(items[0]).toContain('Firefox · Linux')
    expect(items[1]).toContain('登录失败 · 密码错误')
    expect(items[1]).toContain('10.0.0.9')
    expect(items[2]).toContain('登录失败 · GF-FUTURE-001')
    wrapper.unmount()
  })

  it('says when there is no history or it failed to load', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/me/login-history'
      ? Promise.resolve({ items: [], page: 1, size: 10, total: 0 }) : answer(path))
    const empty = await mountAccount()
    expect(empty.wrapper.text()).toContain('暂无登录记录')
    empty.wrapper.unmount()

    api.request.mockImplementation((path: string) => path === '/api/v1/me/login-history'
      ? Promise.reject(new ApiError('服务暂时不可用。', 503)) : answer(path))
    const failed = await mountAccount()
    expect(failed.wrapper.text()).toContain('服务暂时不可用。')
    failed.wrapper.unmount()
  })

  it('reports why the devices failed to load', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/me/sessions'
      ? Promise.reject(new ApiError('网络连接失败。')) : answer(path))
    const { wrapper } = await mountAccount()
    expect(wrapper.text()).toContain('网络连接失败。')
    button(wrapper, '保存资料')
    wrapper.unmount()
  })
})
