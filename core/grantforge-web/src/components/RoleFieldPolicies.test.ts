// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'
import { groups, users, variables } from '../../tests/unit/dataEntities'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: RoleFieldPolicies } = await import('./RoleFieldPolicies.vue')

const stored = [{ entityCode: 'user', fieldCode: 'lastLoginAt', readMode: 'HIDDEN', writeMode: 'EDITABLE' }]
type Call = [string, { method?: string, body?: { policies: unknown[] } }?]
const puts = () => (api.request.mock.calls as Call[]).filter(([, options]) => options?.method === 'PUT')

function answer(path: string, options?: { method?: string }) {
  if (options?.method === 'PUT') return Promise.resolve([])
  if (path === '/api/v1/data-entities') return Promise.resolve({ entities: [users, groups], variables })
  return Promise.resolve(stored)
}
async function pick(select: Element | null | undefined, option: string) {
  (select as HTMLElement).click()
  await flushPromises()
  Array.from(document.querySelectorAll<HTMLElement>('[role="option"]')).find(element => element.textContent?.trim() === option)?.click()
  await flushPromises()
}
const row = (field: string) => document.querySelector(`[data-field="${field}"]`)
const selectsOf = (field: string) => row(field)?.querySelectorAll('button[role="combobox"]') ?? []
async function mountDialog() {
  const result = await mountView(RoleFieldPolicies, { props: { modelValue: true, roleId: '5', roleName: '审计员' } })
  await flushPromises()
  return result
}
async function submit() {
  document.querySelector('form#field-policy-form')?.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

describe('role field policies', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists every secured field with the role\'s policy and saves them all', async () => {
    const { wrapper } = await mountDialog()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/5/field-policies')
    expect(row('user.email')?.textContent).toContain('用户 · 邮箱')
    expect(row('user.lastLoginAt')?.textContent).toContain('隐藏')
    // Without a read mode the field carries no policy, so it cannot be locked either.
    expect((selectsOf('user.email')[1] as HTMLButtonElement).disabled).toBe(true)

    await pick(selectsOf('user.email')[0], '脱敏')
    await pick(selectsOf('user.email')[1], '邮箱（保留首字母和域名）')
    await pick(selectsOf('user.email')[2], '只读')
    await pick(selectsOf('user.lastLoginAt')[0], '不设置')
    await submit()
    expect(puts()[0]?.[1]?.body).toEqual({ policies: [
      { entityCode: 'user', fieldCode: 'email', readMode: 'MASKED', maskStrategy: 'EMAIL', writeMode: 'READONLY' }] })
    expect(useToast().items.map(item => item.message)).toContain('字段权限已保存')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })

  it('shows the server\'s problems next to their fields', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) => options?.method === 'PUT'
      ? Promise.reject(new ApiError('字段策略的部分内容不正确。', 400, 0, null,
        { status: 400, errors: [{ field: 'policies[0].fieldCode', message: '没有名为“user.lastLoginAt”的受控字段。' }] }))
      : answer(path, options))
    await mountDialog()
    await submit()
    expect(document.querySelector('[role="alert"]')?.textContent).toContain('字段策略的部分内容不正确')
    expect(row('user.lastLoginAt')?.textContent).toContain('没有名为')
  })

  it('says when there is nothing to set and why loading failed', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/data-entities'
      ? Promise.resolve({ entities: [groups], variables }) : Promise.resolve([]))
    await mountDialog()
    expect(document.body.textContent).toContain('还没有受控字段')
    document.body.innerHTML = ''
    api.request.mockRejectedValue(new ApiError('无权访问。', 403))
    await mountDialog()
    expect(document.querySelector('[role="alert"]')?.textContent).toContain('无权访问')
  })
})
