// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { i18n } from '@/i18n'
import { ApiError } from '@/lib/api'
import { useToast } from '@/stores/toast'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: RoleGrants } = await import('./RoleGrants.vue')

const base = { applicationId: '1', visible: true, enabled: true, denyMode: 'HIDE', builtin: true }
const resources = [
  { ...base, id: '10', type: 'MODULE', code: 'system', name: 'Access control', nameKey: 'layout.groupAccess', sortOrder: 0, depth: 0 },
  { ...base, id: '11', parentId: '10', type: 'PAGE', code: 'system.user', name: 'Users', nameKey: 'titles.users', sortOrder: 0, depth: 1 },
  { ...base, id: '12', parentId: '11', type: 'ACTION', code: 'system.user.btn.edit', name: 'Edit users', nameKey: 'permissionNames.userEdit', sortOrder: 0, depth: 2 },
  { ...base, id: '20', type: 'MODULE', code: 'api', name: 'API', sortOrder: 1, depth: 0 },
  { ...base, id: '21', parentId: '20', type: 'API', code: 'api:system.user.update', name: 'system.user.update', sortOrder: 0, depth: 1 },
]
const empty = { roleId: '7', applicationId: '1', readOnly: false, grants: [], states: [] }
const previewed = { ...empty, states: [
  { resourceId: '12', state: 'ALLOWED', explicit: true, reasons: [] },
  { resourceId: '21', state: 'IMPLIED', explicit: false, reasons: [{ resourceId: '12', via: 'DEPENDENCY' }] },
  { resourceId: '11', state: 'IMPLIED', explicit: false, reasons: [{ resourceId: '12', via: 'ANCESTOR' }, { resourceId: '21', via: 'ANCESTOR' }, { resourceId: '10', via: 'SYSTEM_ROLE' }] },
] }

function answer(path: string, options?: { method?: string }) {
  if (path === '/api/v1/applications') return Promise.resolve([{ id: '1', code: 'grantforge-console', name: 'Console', builtin: true, resources: 5 },
    { id: '2', code: 'crm', name: 'CRM', builtin: false, resources: 0 }])
  if (path.endsWith('/resources')) return Promise.resolve(path.includes('/2/') ? [] : resources)
  if (path.endsWith('/grants/impact')) return Promise.resolve({ roles: [{ roleId: '7', code: 'auditors', name: '审计员',
    gained: 4, lost: 0 }], accounts: 2, gained: ['system.user', 'system.user.btn.edit'], lost: [] })
  if (options?.method === 'POST') return Promise.resolve(previewed)
  if (options?.method === 'PUT') return Promise.resolve({ ...previewed, grants: [{ resourceId: '12', effect: 'ALLOW', applies: true }] })
  return Promise.resolve(empty)
}
function mountDialog() {
  return mount(RoleGrants, { props: { modelValue: true, roleId: '7', roleName: '审计员' }, attachTo: document.body, global: { plugins: [i18n] } })
}
const row = (code: string) => document.querySelector<HTMLElement>(`[data-resource="${code}"]`)
function choice(code: string, label: string) {
  const found = [...row(code)?.querySelectorAll<HTMLButtonElement>('button') ?? []].find(button => button.textContent?.trim() === label)
  if (!found) throw new Error(`missing ${label} on ${code}`)
  return found
}
function dialogButton(label: string) {
  const found = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!found) throw new Error('missing dialog button ' + label)
  return found as HTMLButtonElement
}
const toasts = () => useToast().items.map(item => item.message)

describe('role grants', () => {
  beforeEach(() => { setActivePinia(createPinia()); api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = ''; vi.useRealTimers() })

  it('lists the tree with grant choices only where grants are possible', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    expect([...document.querySelectorAll('[data-resource]')].map(item => item.getAttribute('data-resource')))
      .toEqual(['system', 'system.user', 'system.user.btn.edit', 'api', 'api:system.user.update'])
    expect(row('system')?.textContent).toContain('访问控制')
    expect(row('system')?.querySelector('[role="group"]')).toBeNull()
    expect(choice('system.user.btn.edit', '未授权').getAttribute('aria-pressed')).toBe('true')
    expect(dialogButton('保存授权').disabled).toBe(true)
    wrapper.unmount()
  })

  it('previews each choice and saves them together', async () => {
    vi.useFakeTimers()
    const wrapper = mountDialog()
    await flushPromises()
    choice('system.user.btn.edit', '允许').click()
    await flushPromises()
    expect(row('system.user.btn.edit')?.className).toContain('bg-amber-50')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('1 项未保存的修改')
    vi.advanceTimersByTime(200)
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/7/grants/preview', { method: 'POST',
      body: { applicationId: '1', changes: [{ resourceId: '12', effect: 'ALLOW' }] } })
    expect(row('api:system.user.update')?.textContent).toContain('被 编辑用户 需要')
    expect(row('system.user')?.textContent).toContain('等 1 项')
    // Choosing the stored state again drops the change.
    choice('system.user.btn.edit', '未授权').click()
    await flushPromises()
    expect(document.querySelector('dialog[open]')?.textContent).toContain('0 项未保存的修改')
    choice('system.user.btn.edit', '允许').click()
    await flushPromises()
    // Saving first shows what the changes would do; they are saved once that is confirmed.
    dialogButton('保存授权').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/7/grants/impact', { method: 'POST',
      body: { applicationId: '1', changes: [{ resourceId: '12', effect: 'ALLOW' }] } })
    expect(document.querySelector('dialog[open] [data-impact]')?.textContent).toContain('将改变 1 个角色的权限，持有这些角色的用户 2 个')
    dialogButton('返回修改').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [data-impact]')).toBeNull()
    dialogButton('保存授权').click()
    await flushPromises()
    dialogButton('确认保存').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/7/grants', { method: 'PUT',
      body: { applicationId: '1', changes: [{ resourceId: '12', effect: 'ALLOW' }] } })
    expect(toasts()).toContain('授权已保存')
    expect(choice('system.user.btn.edit', '允许').getAttribute('aria-pressed')).toBe('true')
    wrapper.unmount()
  })

  it('filters rows and switches applications', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    const search = document.querySelector<HTMLInputElement>('dialog[open] input[aria-label="搜索资源"]')
    if (!search) throw new Error('missing search')
    search.value = 'edit'
    search.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(document.querySelectorAll('[data-resource]')).toHaveLength(1)
    search.value = ''
    search.dispatchEvent(new Event('input'))
    document.querySelector<HTMLElement>('dialog[open] [role="switch"]')?.click()
    await flushPromises()
    expect(document.querySelectorAll('[data-resource]')).toHaveLength(0)
    document.querySelector<HTMLButtonElement>('dialog[open] [role="combobox"]')?.click()
    await flushPromises()
    ;[...document.querySelectorAll<HTMLElement>('[role="option"]')].find(item => item.textContent?.trim() === 'CRM')?.click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/applications/2/resources')
    wrapper.unmount()
  })

  it('is read-only for system roles and reports failures', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) => path === '/api/v1/roles/7/grants' && !options?.method
      ? Promise.resolve({ ...previewed, readOnly: true, grants: [{ resourceId: '12', effect: 'DENY', expiresAt: '2026-01-01T00:00:00Z', applies: false }] })
      : answer(path, options))
    const readOnly = mountDialog()
    await flushPromises()
    expect(document.querySelector('[role="note"]')?.textContent).toContain('系统角色')
    expect(choice('system.user.btn.edit', '允许').disabled).toBe(true)
    expect(row('system.user.btn.edit')?.textContent).toContain('已过期')
    readOnly.unmount()

    vi.useFakeTimers()
    api.request.mockImplementation((path: string, options?: { method?: string }) => options?.method
      ? Promise.reject(new ApiError('平台资源只能在平台租户中授权。', 403)) : answer(path, options))
    const failing = mountDialog()
    await flushPromises()
    choice('system.user.btn.edit', '拒绝').click()
    vi.advanceTimersByTime(200)
    await flushPromises()
    dialogButton('保存授权').click()
    await flushPromises()
    expect(toasts().filter(message => message === '平台资源只能在平台租户中授权。')).toHaveLength(2)
    failing.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = mountDialog()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('无权访问')
    failed.unmount()
  })
})
