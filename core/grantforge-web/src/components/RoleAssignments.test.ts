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
import { setDate } from '../../tests/unit/datePicker'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: RoleAssignments } = await import('./RoleAssignments.vue')

const assignments = [
  { id: 'a1', roleId: '7', subjectType: 'USER', subjectId: '1', subjectName: 'Alice', subjectDetail: 'alice', includeSubUnits: false, valid: true },
  { id: 'a2', roleId: '7', subjectType: 'ORG_UNIT', subjectId: '2', subjectName: 'Sales', subjectDetail: 'sales', validTo: '2026-12-31T16:00:00Z', includeSubUnits: true, valid: false },
]
function answer(path: string, options?: { method?: string }) {
  if (options?.method) return Promise.resolve(null)
  if (path === '/api/v1/roles/7/assignments') return Promise.resolve(assignments)
  if (path === '/api/v1/users') return Promise.resolve({ items: [{ id: '1', username: 'alice', displayName: 'Alice' }, { id: '3', username: 'bob' }] })
  if (path === '/api/v1/groups') return Promise.resolve({ items: [{ id: '4', code: 'dev', name: 'Developers' }] })
  if (path === '/api/v1/org-units') return Promise.resolve([{ id: '2', code: 'sales', name: 'Sales', sortOrder: 0, depth: 0 }])
  return Promise.resolve([{ id: '5', name: 'CFO' }])
}
function mountDialog() {
  return mount(RoleAssignments, { props: { modelValue: true, roleId: '7', roleName: '审计员' }, attachTo: document.body, global: { plugins: [i18n] } })
}
function dialogButton(label: string) {
  const found = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!found) throw new Error('missing dialog button ' + label)
  return found as HTMLButtonElement
}
const comboboxes = () => [...document.querySelectorAll<HTMLButtonElement>('dialog[open] [role="combobox"]')]
async function pick(index: number, option: string) {
  comboboxes()[index]?.click()
  await flushPromises()
  ;[...document.querySelectorAll<HTMLElement>('[role="option"]')].find(item => item.textContent?.trim() === option)?.click()
  await flushPromises()
}
const options = () => [...document.querySelectorAll('[role="option"]')].map(item => item.textContent?.trim())
const toasts = () => useToast().items.map(item => item.message)

describe('role assignments', () => {
  beforeEach(() => { setActivePinia(createPinia()); api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists who has the role with validity and reach', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    expect(document.querySelectorAll('[data-assignment]')).toHaveLength(2)
    expect(document.querySelector('[data-assignment="a1"]')?.textContent).toContain('长期有效')
    expect(document.querySelector('[data-assignment="a1"]')?.textContent).toContain('生效中')
    expect(document.querySelector('[data-assignment="a2"]')?.textContent).toContain('含下级部门')
    expect(document.querySelector('[data-assignment="a2"]')?.textContent).toContain('未生效')
    wrapper.unmount()
  })

  it('assigns to users, groups, departments and positions', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    dialogButton('添加分配').click()
    await flushPromises()
    dialogButton('分配').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请选择分配对象')
    comboboxes()[1]?.click()
    await flushPromises()
    expect(options()).toEqual(['Alice (alice)', 'bob'])
    ;[...document.querySelectorAll<HTMLElement>('[role="option"]')].find(item => item.textContent?.trim() === 'bob')?.click()
    await flushPromises()
    await setDate(wrapper, '截止日期', '2026-12-31')
    dialogButton('分配').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/7/assignments', { method: 'POST', body: { subjectType: 'USER', subjectId: '3',
      validFrom: null, validTo: new Date(new Date('2026-12-31T00:00:00').getTime() + 24 * 3600 * 1000).toISOString(), includeSubUnits: false } })
    expect(toasts()).toContain('已分配')

    dialogButton('添加分配').click()
    await flushPromises()
    await pick(0, '部门')
    await pick(1, 'Sales')
    const sub = [...document.querySelectorAll<HTMLElement>('dialog[open] [role="switch"]')][0]
    sub?.click()
    await flushPromises()
    dialogButton('分配').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/7/assignments', { method: 'POST', body: { subjectType: 'ORG_UNIT', subjectId: '2',
      validFrom: null, validTo: null, includeSubUnits: true } })

    dialogButton('添加分配').click()
    await flushPromises()
    await pick(0, '用户组')
    comboboxes()[1]?.click()
    await flushPromises()
    expect(options()).toEqual(['Developers (dev)'])
    ;[...document.querySelectorAll<HTMLElement>('[role="option"]')][0]?.click()
    await flushPromises()
    await pick(0, '岗位')
    comboboxes()[1]?.click()
    await flushPromises()
    expect(options()).toEqual(['CFO'])
    wrapper.unmount()
  })

  it('searches accounts after a pause and removes assignments', async () => {
    vi.useFakeTimers()
    const wrapper = mountDialog()
    await flushPromises()
    dialogButton('添加分配').click()
    await flushPromises()
    const search = [...document.querySelectorAll<HTMLInputElement>('dialog[open] input')].find(item => item.placeholder === '按名称或编码搜索')
    if (!search) throw new Error('missing search')
    search.value = ' ali '
    search.dispatchEvent(new Event('input'))
    await flushPromises()
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/users', { query: { q: 'ali', page: 1, size: 50 } })
    vi.useRealTimers()
    dialogButton('取消').click()
    await flushPromises()
    document.querySelector<HTMLButtonElement>('[aria-label="移除对 Alice 的分配"]')?.click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/role-assignments/a1', { method: 'DELETE' })
    expect(toasts()).toContain('已移除分配')
    wrapper.unmount()
  })

  it('reports refusals and load failures', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) => options?.method
      ? Promise.reject(new ApiError('已经拥有该角色。', 409)) : answer(path, options))
    const wrapper = mountDialog()
    await flushPromises()
    dialogButton('添加分配').click()
    await flushPromises()
    await pick(1, 'bob')
    dialogButton('分配').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('已经拥有该角色。')
    dialogButton('取消').click()
    await flushPromises()
    document.querySelector<HTMLButtonElement>('[aria-label="移除对 Alice 的分配"]')?.click()
    await flushPromises()
    expect(toasts()).toContain('已经拥有该角色。')
    wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = mountDialog()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('无权访问')
    failed.unmount()
  })
})
