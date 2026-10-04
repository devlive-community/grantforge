// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: UserPermissions } = await import('./UserPermissions.vue')

const access = {
  accountId: '7',
  roles: [{ id: '2', code: 'editors', name: '编辑', active: true, assignedTo: [{ type: 'GROUP', id: '3', name: 'Developers', detail: 'dev' }] },
    { id: '4', code: 'old', name: '旧角色', active: false, assignedTo: [] }],
  resources: [{ code: 'system', name: 'System', type: 'MODULE' }, { code: 'system.user', name: 'Users', nameKey: 'permissionNames.users', type: 'PAGE', parentCode: 'system' }],
  permissions: [{ code: 'system.user.read', name: 'api:system.user.read', type: 'API', parentCode: 'api' }],
  data: [{ entityCode: 'user', action: 'READ', allow: [{ scope: 'CUSTOM_ORGS', conditional: false, orgUnitCount: 2 }, { scope: 'CONDITION', conditional: true, orgUnitCount: 0 }],
    deny: [{ scope: 'SELF', conditional: false, orgUnitCount: 0 }] }],
  fields: { 'user.email': { readMode: 'MASKED', maskStrategy: 'EMAIL', writeMode: 'READONLY' } },
}
const explanation = {
  accountId: '7', kind: 'RESOURCE', code: 'system.user', outcome: 'DENIED', name: 'Users',
  paths: [{ roles: [{ code: 'editors', name: '编辑', assignedTo: [{ type: 'GROUP', id: '3', name: 'Developers' }] }, { code: 'base', name: '基础', assignedTo: [] }],
    resources: [{ code: 'system.user.btn.edit', name: 'Edit', type: 'ACTION', via: 'GRANT' }, { code: 'system.user', name: 'Users', type: 'PAGE', via: 'ANCESTOR' }] }],
  denials: [{ roleCode: 'no-users', roleName: '禁止', resourceCode: 'system.user' }],
}
const roles = [{ id: '2', code: 'editors', name: '编辑', type: 'CUSTOM', enabled: true }, { id: '5', code: 'auditors', name: '审计员', type: 'CUSTOM', enabled: true },
  { id: '6', code: 'off', name: '停用的', type: 'CUSTOM', enabled: false }]
const simulation = { accountId: '7', rolesBefore: ['editors'], rolesAfter: ['auditors'],
  gainedResources: [{ code: 'system.audit', name: 'Audit', type: 'PAGE' }], lostResources: [{ code: 'system.user.btn.edit', name: 'Edit', type: 'ACTION' }],
  gainedPermissions: [], lostPermissions: [{ code: 'system.user.update', name: 'Update', type: 'API' }] }
function answer(path: string) {
  if (path === '/api/v1/roles') return Promise.resolve(roles)
  if (path === '/api/v1/authz/simulate') return Promise.resolve(simulation)
  return Promise.resolve(path === '/api/v1/authz/explain' ? explanation : access)
}
async function mountDialog() {
  const result = await mountView(UserPermissions, { props: { modelValue: true, accountId: '7', accountName: 'Alice' } })
  await flushPromises()
  return result
}
const text = (selector: string) => document.querySelector(selector)?.textContent ?? ''

describe('user permissions', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists roles, resources, permissions, data scopes and restricted fields', async () => {
    await mountDialog()
    expect(api.request).toHaveBeenCalledWith('/api/v1/authz/effective', { method: 'POST', body: { accountId: '7' } })
    expect(text('dialog[open]')).toContain('有效权限：Alice')
    expect(text('[data-role="editors"]')).toContain('用户组 · Developers')
    expect(text('[data-role="old"]')).toContain('未生效')
    expect(text('[data-resource="system.user"]')).toContain('页面')
    expect((document.querySelector('[data-resource="system.user"]') as HTMLElement).style.paddingLeft).toBe('32px')
    expect(text('[data-permission="system.user.read"]')).toContain('system.user.read')
    expect(text('[data-rule="user:READ"]')).toContain('（2 个部门）')
    expect(text('[data-rule="user:READ"]')).toContain('按条件')
    expect(text('[data-rule="user:READ"]')).toContain('拒绝')
    expect(text('[data-field="user.email"]')).toContain('脱敏')
    expect(text('[data-field="user.email"]')).toContain('只读')
  })

  it('explains why a resource is allowed or denied', async () => {
    await mountDialog()
    ;(document.querySelector('[data-resource="system.user"] button') as HTMLButtonElement).click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/authz/explain', { method: 'POST', body: { accountId: '7', kind: 'RESOURCE', code: 'system.user' } })
    const why = text('[role="status"]')
    expect(why).toContain('被拒绝')
    expect(text('[data-path]')).toMatch(/编辑（分配给 用户组 Developers） → 继承\s基础/)
    expect(text('[data-path]')).toContain('授予 Edit → 上级 Users')
    expect(why).toContain('角色“禁止”拒绝了 system.user')

    api.request.mockRejectedValueOnce(new ApiError('无权访问。', 403))
    ;(document.querySelector('[data-permission="system.user.read"] button') as HTMLButtonElement).click()
    await flushPromises()
    expect(text('[role="status"]')).toContain('无权访问')
  })

  it('says when there is nothing and why loading failed', async () => {
    api.request.mockResolvedValue({ accountId: '7', roles: [], resources: [], permissions: [], data: [], fields: {} })
    await mountDialog()
    expect(text('dialog[open]')).toContain('没有角色')
    expect(text('dialog[open]')).toContain('没有数据权限')
    expect(text('dialog[open]')).toContain('所有字段都可见、可修改')
    document.body.innerHTML = ''
    api.request.mockRejectedValue(new ApiError('未找到。', 404))
    await mountDialog()
    expect(text('[role="alert"]')).toContain('未找到')
  })

  it('simulates adding and removing roles', async () => {
    await mountDialog()
    const section = () => document.querySelector('[aria-label="模拟变更"]') as HTMLElement
    expect(section().textContent).toContain('审计员')
    expect(section().textContent).not.toContain('停用的')
    const run = [...section().querySelectorAll('button')].find(button => button.textContent?.trim() === '开始模拟') as HTMLButtonElement
    expect(run.disabled).toBe(true)
    const boxes = [...section().querySelectorAll<HTMLInputElement>('input[type="checkbox"]')]
    // First the roles to add (审计员), then the held ones to remove (编辑).
    boxes[0]?.click()
    boxes[1]?.click()
    await flushPromises()
    run.click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/authz/simulate', { method: 'POST',
      body: { accountId: '7', addRoles: ['5'], removeRoles: ['2'], grants: [] } })
    expect(text('[data-simulation]')).toContain('+ 页面 Audit')
    expect(text('[data-simulation]')).toContain('− 按钮 Edit')
    expect(text('[data-simulation]')).toContain('− system.user.update')

    api.request.mockResolvedValueOnce({ ...simulation, gainedResources: [], lostResources: [], lostPermissions: [] })
    run.click()
    await flushPromises()
    expect(text('[data-simulation]')).toContain('不会改变')
    api.request.mockRejectedValueOnce(new ApiError('无权访问。', 403))
    run.click()
    await flushPromises()
    expect(section().querySelector('[role="alert"]')?.textContent).toContain('无权访问')
  })
})
