// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: TenantsView } = await import('./TenantsView.vue')

const platform = { id: '1', code: 'default', name: '平台', status: 'ACTIVE', platform: true, accounts: 3, createdAt: '2026-10-01T08:00:00Z' }
const acme = { id: '9007199254740993', code: 'acme', name: 'Acme', status: 'ACTIVE', platform: false, accounts: 1, createdAt: '2026-10-01T09:00:00Z' }
const globex = { id: '7', code: 'globex', name: 'Globex', status: 'SUSPENDED', platform: false, accounts: 4, createdAt: '2026-10-01T10:00:00Z' }

function answer(path: string) {
  if (path === '/api/v1/tenants') return Promise.resolve({ items: [platform, acme, globex], page: 1, size: 20, total: 3 })
  return Promise.resolve(acme)
}

function dialogButton(label: string) {
  const button = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!button) throw new Error('missing dialog button ' + label)
  return button as HTMLButtonElement
}

/** Types into a field of the open dialog (dialogs render outside the component, in the document body). */
async function fill(label: string, value: string) {
  const owner = [...document.querySelectorAll<HTMLLabelElement>('dialog[open] label')]
    .find(item => item.textContent?.replace('*', '').trim() === label)
  const input = owner ? document.getElementById(owner.htmlFor) as HTMLInputElement | null : null
  if (!input) throw new Error('missing field ' + label)
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function submit(form: string) {
  document.querySelector(`form#${form}`)?.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

const alertOf = (form: string) => document.querySelector(`form#${form} [role="alert"]`)?.textContent

async function mountTenants() {
  const mounted = await mountView(TenantsView, {}, '/platform/tenants')
  await flushPromises()
  return mounted
}

const toasts = () => useToast().items.map(item => item.message)
const listCalls = () => api.request.mock.calls.filter(([path, options]) => path === '/api/v1/tenants' && !options?.method)

describe('tenants view', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = ''; vi.useRealTimers() })

  it('lists tenants and protects the platform tenant', async () => {
    const { wrapper } = await mountTenants()
    expect(wrapper.text()).toContain('3 个租户')
    expect(wrapper.text()).toContain('平台')
    expect(wrapper.find('[aria-label="停用租户 平台"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="停用租户 Acme"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="启用租户 Globex"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('searches after a pause in typing', async () => {
    vi.useFakeTimers()
    const { wrapper } = await mountTenants()
    await wrapper.get('[aria-label="搜索租户"]').setValue(' ac')
    await wrapper.get('[aria-label="搜索租户"]').setValue(' acme ')
    expect(listCalls()).toHaveLength(1)
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(listCalls()).toHaveLength(2)
    expect(listCalls()[1]?.[1]).toMatchObject({ query: { q: 'acme', page: 1, size: 20 } })
    wrapper.unmount()
  })

  it('checks and creates a tenant with its administrator', async () => {
    const { wrapper } = await mountTenants()
    await wrapper.findAll('button').find(button => button.text().includes('创建租户'))?.trigger('click')
    await submit('create-tenant')
    expect(alertOf('create-tenant')).toBe('请输入租户编码')
    await fill('租户编码', 'acme')
    await submit('create-tenant')
    expect(alertOf('create-tenant')).toBe('请输入租户名称')
    await fill('租户名称', 'Acme')
    await submit('create-tenant')
    expect(alertOf('create-tenant')).toBe('请输入管理员用户名')
    await fill('管理员用户名', 'boss')
    await submit('create-tenant')
    expect(alertOf('create-tenant')).toBe('请输入初始密码')
    await fill('初始密码', 'a long enough password')
    await submit('create-tenant')
    expect(alertOf('create-tenant')).toBe('两次输入的密码不一致')

    api.request.mockRejectedValueOnce(new ApiError('租户编码“acme”已被使用。', 409))
    await fill('确认初始密码', 'a long enough password')
    await submit('create-tenant')
    expect(alertOf('create-tenant')).toBe('租户编码“acme”已被使用。')

    await submit('create-tenant')
    expect(api.request).toHaveBeenCalledWith('/api/v1/tenants', { method: 'POST', body: { code: 'acme', name: 'Acme',
      adminUsername: 'boss', adminDisplayName: '', adminPassword: 'a long enough password' } })
    expect(toasts()).toContain('租户已创建')
    expect(listCalls()).toHaveLength(2)
    wrapper.unmount()
  })

  it('renames a tenant', async () => {
    const { wrapper } = await mountTenants()
    await wrapper.get('[aria-label="编辑租户 Acme"]').trigger('click')
    await fill('租户名称', ' ')
    await submit('edit-tenant')
    expect(alertOf('edit-tenant')).toBe('请输入租户名称')
    await fill('租户名称', 'Acme Group')
    dialogButton('保存').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/tenants/9007199254740993', { method: 'PUT', body: { name: 'Acme Group' } })
    expect(toasts()).toContain('租户已更新')
    wrapper.unmount()
  })

  it('suspends after confirmation and reactivates', async () => {
    const { wrapper } = await mountTenants()
    await wrapper.get('[aria-label="停用租户 Acme"]').trigger('click')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('所有账号将立即退出')
    api.request.mockRejectedValueOnce(new ApiError('平台租户不能停用。', 409))
    dialogButton('停用').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('平台租户不能停用。')
    dialogButton('停用').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/tenants/9007199254740993/suspend', { method: 'POST' })
    expect(toasts()).toContain('租户已停用')

    await wrapper.get('[aria-label="启用租户 Globex"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/tenants/7/activate', { method: 'POST' })
    expect(toasts()).toContain('租户已启用')
    api.request.mockRejectedValueOnce(new ApiError('无权执行此操作。', 403))
    await wrapper.get('[aria-label="启用租户 Globex"]').trigger('click')
    await flushPromises()
    expect(useToast().items.at(-1)).toMatchObject({ message: '无权执行此操作。', kind: 'error' })
    wrapper.unmount()
  })

  it('shows why the list failed', async () => {
    api.request.mockRejectedValueOnce(new ApiError('无权执行此操作。', 403))
    const { wrapper } = await mountTenants()
    expect(wrapper.text()).toContain('无权执行此操作。')
    wrapper.unmount()
  })
})
