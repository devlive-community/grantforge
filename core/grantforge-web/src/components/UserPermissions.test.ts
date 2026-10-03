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
  roles: [{ code: 'editors', name: '编辑', active: true, assignedTo: [{ type: 'GROUP', id: '3', name: 'Developers', detail: 'dev' }] },
    { code: 'old', name: '旧角色', active: false, assignedTo: [] }],
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
function answer(path: string) {
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
})
