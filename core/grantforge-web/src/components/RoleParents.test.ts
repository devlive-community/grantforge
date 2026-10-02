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

const { default: RoleParents } = await import('./RoleParents.vue')

const role = (id: string, code: string, name: string, extra = {}) => ({ id, code, name, type: 'CUSTOM', enabled: true, ...extra })
const admin = role('1', 'tenant-admin', 'Tenant administrator', { type: 'SYSTEM' })
const auditors = role('2', 'auditors', '审计员')
const editors = role('3', 'editors', '编辑', { enabled: false })
const base = role('4', 'base', '基础')
const inheritance = { role: auditors, parents: [editors], ancestors: [{ role: editors, distance: 1 }, { role: admin, distance: 2 }],
  descendants: [{ role: base, distance: 1 }] }

function answer(path: string, options?: { method?: string; body?: { parentIds: string[] } }) {
  if (path === '/api/v1/roles') return Promise.resolve([admin, auditors, editors, base])
  if (options?.method === 'PUT') return Promise.resolve({ ...inheritance, parents: [editors, admin] })
  return Promise.resolve(inheritance)
}
function mountDialog() {
  return mount(RoleParents, { props: { modelValue: true, roleId: '2', roleName: '审计员' }, attachTo: document.body, global: { plugins: [i18n] } })
}
const item = (code: string) => document.querySelector<HTMLElement>(`[data-role="${code}"]`)
const box = (code: string) => item(code)?.querySelector<HTMLInputElement>('input[type="checkbox"]') ?? null
function dialogButton(label: string) {
  const found = [...document.querySelectorAll('dialog[open] button')].find(button => button.textContent?.trim() === label)
  if (!found) throw new Error('missing dialog button ' + label)
  return found as HTMLButtonElement
}

describe('role parents', () => {
  beforeEach(() => { setActivePinia(createPinia()); api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists other roles, keeps cycles out and shows the chain both ways', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    expect([...document.querySelectorAll('[data-role]')].map(element => element.getAttribute('data-role')))
      .toEqual(['tenant-admin', 'editors', 'base'])
    expect(box('editors')?.checked).toBe(true)
    expect(item('editors')?.textContent).toContain('已停用')
    expect(box('base')?.disabled).toBe(true)
    expect(item('base')?.textContent).toContain('已继承本角色')
    expect(document.querySelector('[data-list="ancestors"]')?.textContent).toContain('租户管理员')
    expect(document.querySelector('[data-list="descendants"]')?.textContent).toContain('基础')
    expect(dialogButton('保存继承').disabled).toBe(true)

    const search = document.querySelector<HTMLInputElement>('dialog[open] input[aria-label="搜索角色"]')
    if (!search) throw new Error('missing search')
    search.value = 'edit'
    search.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(document.querySelectorAll('[data-role]')).toHaveLength(1)
    wrapper.unmount()
  })

  it('saves the chosen parents and reports refusals in the dialog', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    box('tenant-admin')?.click()
    await flushPromises()
    expect(document.querySelector('dialog[open]')?.textContent).toContain('直接继承 2 个角色')
    dialogButton('保存继承').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/2/parents', { method: 'PUT', body: { parentIds: ['3', '1'] } })
    expect(useToast().items.map(toast => toast.message)).toContain('继承关系已保存')
    expect(wrapper.emitted('saved')).toHaveLength(1)

    api.request.mockImplementation((path: string, options?: { method?: string }) => options?.method === 'PUT'
      ? Promise.reject(new ApiError('继承“基础”会形成循环继承。', 409)) : answer(path, options))
    box('editors')?.click()
    await flushPromises()
    dialogButton('保存继承').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('继承“基础”会形成循环继承。')
    wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = mountDialog()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('无权访问')
    failed.unmount()
  })
})
