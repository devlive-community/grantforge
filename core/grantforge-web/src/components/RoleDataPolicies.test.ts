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

const { default: RoleDataPolicies } = await import('./RoleDataPolicies.vue')

const units = [{ id: '1', code: 'hq', name: '总部', sortOrder: 0, depth: 0 }, { id: '2', parentId: '1', code: 'rd', name: '研发', sortOrder: 0, depth: 1 }]
const policies = [
  { id: '11', roleId: '5', entityCode: 'user', action: 'READ', scope: 'CUSTOM_ORGS', effect: 'ALLOW', orgUnitIds: ['2', '9'], updatedAt: '2026-10-02T00:00:00Z' },
  { id: '12', roleId: '5', entityCode: 'user', action: 'EXPORT', scope: 'CONDITION', effect: 'DENY', orgUnitIds: [],
    condition: { and: [{ field: 'status', op: 'eq', value: 'DISABLED' }] }, updatedAt: '2026-10-02T00:00:00Z' },
  { id: '13', roleId: '5', entityCode: 'invoice', action: 'READ', scope: 'TENANT', effect: 'ALLOW', orgUnitIds: [], updatedAt: '2026-10-02T00:00:00Z' },
]
type Call = [string, { method?: string, body?: Record<string, unknown>, query?: Record<string, unknown> }?]
const calls = (method: string) => (api.request.mock.calls as Call[]).filter(([, options]) => options?.method === method)
function button(text: string) {
  const found = Array.from(document.querySelectorAll('button')).find(element => element.textContent?.trim() === text)
  if (!found) throw new Error(`no button ${text}`)
  return found
}
async function pick(select: Element | null | undefined, option: string) {
  (select as HTMLElement).click()
  await flushPromises()
  Array.from(document.querySelectorAll<HTMLElement>('[role="option"]')).find(element => element.textContent?.trim() === option)?.click()
  await flushPromises()
}
async function mountDialog() {
  const result = await mountView(RoleDataPolicies, { props: { modelValue: true, roleId: '5', roleName: '审计员' } })
  await flushPromises()
  return result
}

describe('role data policies', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (path === '/api/v1/data-entities') return Promise.resolve({ entities: [users, groups], variables })
      if (path === '/api/v1/org-units') return Promise.resolve(units)
      if (path === '/api/v1/users') return Promise.resolve({ items: [{ id: '77', username: 'Alice' }], page: 0, size: 20, total: 1 })
      if (path.endsWith('/preview')) return Promise.resolve({ withRole: 3, now: 1 })
      if (options?.method) return Promise.resolve(policies[0])
      return Promise.resolve(policies)
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists what the role may see', async () => {
    const { wrapper } = await mountDialog()
    const text = document.body.textContent ?? ''
    expect(text).toContain('数据权限：审计员')
    expect(document.querySelector('[data-policy="user:READ"]')?.textContent).toContain('研发、9')
    expect(document.querySelector('[data-policy="user:EXPORT"]')?.textContent).toContain('按条件')
    expect(document.querySelector('[data-policy="user:EXPORT"]')?.textContent).toContain('拒绝')
    // Entities the console does not know keep their code.
    expect(document.querySelector('[data-policy="invoice:READ"]')?.textContent).toContain('invoice')
    wrapper.unmount()
  })

  it('adds a policy with a condition and shows the problems the server finds', async () => {
    const { wrapper } = await mountDialog()
    button('添加数据权限').click()
    await flushPromises()
    const selects = () => document.querySelectorAll('#data-policy-form > div > div button[role="combobox"]')
    await pick(selects()[2], '按条件')
    button('添加条件').click()
    await flushPromises()
    await pick(document.querySelectorAll('[data-comparison] button[role="combobox"]')[0], '状态')
    await pick(document.querySelectorAll('[data-comparison] button[role="combobox"]')[2], 'ACTIVE')
    api.request.mockImplementationOnce(() => Promise.reject(new ApiError('数据策略的部分内容不正确。', 400, 0, null,
      { title: 'invalid', status: 400, errors: [{ field: 'condition.and[0].value', message: '不是有效的值' }] })))
    document.querySelector<HTMLFormElement>('#data-policy-form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(calls('POST')[0]).toEqual(['/api/v1/roles/5/data-policies', { method: 'POST', body: { entityCode: 'user', action: 'READ',
      effect: 'ALLOW', scope: 'CONDITION', condition: { and: [{ field: 'status', op: 'eq', value: 'ACTIVE' }] }, orgUnitIds: [] } }])
    expect(document.querySelector('[data-comparison]')?.textContent).toContain('不是有效的值')
    expect(document.querySelector('#data-policy-form [role="alert"]:last-child')?.textContent).toContain('数据策略的部分内容不正确。')

    document.querySelector<HTMLFormElement>('#data-policy-form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(useToast().items.map(item => item.message)).toContain('数据权限已添加')
    expect(document.querySelector('#data-policy-form')).toBeNull()
    wrapper.unmount()
  })

  it('edits chosen departments and switches entities', async () => {
    const { wrapper } = await mountDialog()
    ;(document.querySelector('[aria-label="编辑 用户 的数据权限"]') as HTMLElement).click()
    await flushPromises()
    const boxes = () => document.querySelectorAll<HTMLInputElement>('#data-policy-form fieldset input[type="checkbox"]')
    expect(boxes()[1]?.checked).toBe(true)
    boxes()[0]?.click()
    await flushPromises()
    boxes()[1]?.click()
    await flushPromises()
    document.querySelector<HTMLFormElement>('#data-policy-form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(calls('PUT')[0]).toEqual(['/api/v1/data-policies/11', { method: 'PUT', body: { entityCode: 'user', action: 'READ',
      effect: 'ALLOW', scope: 'CUSTOM_ORGS', condition: undefined, orgUnitIds: ['9', '1'] } }])

    // A new policy on groups keeps only scopes groups have.
    button('添加数据权限').click()
    await flushPromises()
    await pick(document.querySelectorAll('#data-policy-form > div > div button[role="combobox"]')[0], '用户组')
    document.querySelector<HTMLFormElement>('#data-policy-form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(calls('POST')[0]?.[1]?.body).toMatchObject({ entityCode: 'group', scope: 'TENANT' })
    wrapper.unmount()
  })

  it('edits a condition back and deletes policies', async () => {
    const { wrapper } = await mountDialog()
    ;(document.querySelectorAll('[aria-label="编辑 用户 的数据权限"]')[1] as HTMLElement).click()
    await flushPromises()
    expect(document.querySelector('[data-comparison]')).not.toBeNull()
    button('取消').click()
    await flushPromises()
    ;(document.querySelectorAll('[aria-label="删除 用户 的数据权限"]')[0] as HTMLElement).click()
    await flushPromises()
    expect(document.body.textContent).toContain('删除“用户”的这条数据权限？')
    button('删除').click()
    await flushPromises()
    expect(calls('DELETE')[0]?.[0]).toBe('/api/v1/data-policies/11')
    api.request.mockImplementationOnce(() => Promise.reject(new ApiError('无权操作', 403)))
    ;(document.querySelectorAll('[aria-label="删除 用户 的数据权限"]')[0] as HTMLElement).click()
    await flushPromises()
    button('删除').click()
    await flushPromises()
    expect(useToast().items.map(item => item.message)).toEqual(expect.arrayContaining(['数据权限已删除', '无权操作']))
    wrapper.unmount()
  })

  it('previews what a user would see with only this role', async () => {
    const { wrapper } = await mountDialog()
    const user = document.querySelector<HTMLInputElement>('section input[placeholder="例如 alice"]')
    if (!user) throw new Error('no user input')
    user.value = 'alice'; user.dispatchEvent(new Event('input'))
    await flushPromises()
    button('预览').click()
    await flushPromises()
    expect(calls('POST')[0]).toEqual(['/api/v1/roles/5/data-policies/preview', { method: 'POST', body: { accountId: '77', entityCode: 'user',
      action: 'READ' } }])
    expect(document.querySelector('[role="status"]')?.textContent).toBe('只拥有该角色时可见 3 行；该用户目前可见 1 行。')
    user.value = 'nobody'; user.dispatchEvent(new Event('input'))
    await flushPromises()
    button('预览').click()
    await flushPromises()
    expect(document.body.textContent).toContain('找不到用户“nobody”')
    api.request.mockImplementationOnce(() => Promise.reject(new ApiError('无权访问', 403)))
    button('预览').click()
    await flushPromises()
    expect(document.body.textContent).toContain('无权访问')
    wrapper.unmount()
  })

  it('previews only the console\'s own entities, whose rows GrantForge can count', async () => {
    const order = { ...groups, code: 'shop:order', name: 'Orders', previewable: false }
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (path === '/api/v1/data-entities') return Promise.resolve({ entities: [order, users, groups], variables })
      if (path === '/api/v1/users') return Promise.resolve({ items: [{ id: '77', username: 'Alice' }], page: 0, size: 20, total: 1 })
      if (path.endsWith('/preview')) return Promise.resolve({ withRole: 3, now: 1 })
      if (options?.method) return Promise.resolve(policies[0])
      return Promise.resolve(policies)
    })
    const { wrapper } = await mountDialog()
    const user = document.querySelector<HTMLInputElement>('section input[placeholder="例如 alice"]')
    if (!user) throw new Error('no user input')
    user.value = 'alice'; user.dispatchEvent(new Event('input'))
    await flushPromises()
    button('预览').click()
    await flushPromises()
    expect(calls('POST')[0]?.[1]).toMatchObject({ body: { entityCode: 'user' } })
    wrapper.unmount()
  })

  it('reports load failures', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/org-units' ? Promise.reject(new ApiError('无权', 403))
      : Promise.reject(new ApiError('加载失败', 500)))
    const { wrapper } = await mountDialog()
    expect(document.querySelector('[role="alert"]')?.textContent).toBe('加载失败')
    wrapper.unmount()
  })
})
