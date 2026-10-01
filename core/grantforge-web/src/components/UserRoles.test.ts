// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { i18n } from '@/i18n'
import { ApiError } from '@/lib/api'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: UserRoles } = await import('./UserRoles.vue')

const source = (id: string, subjectType: string, subjectName: string, valid = true, validTo?: string) => ({ id, roleId: '1', subjectType,
  subjectId: '9', subjectName, includeSubUnits: false, valid, validTo })
const roles = [
  { role: { id: '1', code: 'tenant-admin', name: 'Tenant administrator', type: 'SYSTEM', enabled: true }, active: true,
    sources: [source('s1', 'USER', 'Alice')] },
  { role: { id: '2', code: 'auditors', name: '审计员', type: 'CUSTOM', enabled: true }, active: true,
    sources: [source('s2', 'GROUP', 'Developers'), source('s3', 'ORG_UNIT', 'Sales', true, '2026-12-31T16:00:00Z')] },
  { role: { id: '3', code: 'old', name: '过期', type: 'CUSTOM', enabled: true }, active: false, sources: [source('s4', 'POSITION', 'CFO', false)] },
  { role: { id: '4', code: 'off', name: '停用', type: 'CUSTOM', enabled: false }, active: false, sources: [source('s5', 'USER', 'Alice')] },
]

function mountDialog() {
  return mount(UserRoles, { props: { modelValue: true, accountId: '9', accountName: 'Alice' }, attachTo: document.body, global: { plugins: [i18n] } })
}

describe('user roles', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockResolvedValue(roles) })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows every role with how the account has it', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/users/9/roles')
    const text = (code: string) => document.querySelector(`[data-role="${code}"]`)?.textContent ?? ''
    expect(text('tenant-admin')).toContain('租户管理员')
    expect(text('tenant-admin')).toContain('直接分配')
    expect(text('auditors')).toContain('通过用户组 Developers')
    expect(text('auditors')).toContain('通过部门 Sales')
    expect(text('auditors')).toContain('截止')
    expect(text('old')).toContain('通过岗位 CFO')
    expect(text('old')).toContain('不在有效期内')
    expect(text('off')).toContain('角色已停用')
    wrapper.unmount()
  })

  it('says when there is nothing to show or loading failed', async () => {
    api.request.mockResolvedValue([])
    const empty = mountDialog()
    await flushPromises()
    expect(document.body.textContent).toContain('这个用户还没有任何角色')
    empty.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = mountDialog()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('无权访问')
    failed.unmount()
  })
})
