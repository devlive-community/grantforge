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

const { default: RolesView } = await import('./RolesView.vue')

const roles = [
  { id: '1', code: 'tenant-admin', name: 'Tenant administrator', type: 'SYSTEM', enabled: true },
  { id: '2', code: 'auditors', name: '审计员', description: '只读', type: 'CUSTOM', enabled: true },
  { id: '3', code: 'buyers', name: '采购', type: 'CUSTOM', enabled: false },
]

async function mountRoles() {
  const mounted = await mountView(RolesView, {}, '/admin/roles')
  await flushPromises()
  return mounted
}
type Wrapper = Awaited<ReturnType<typeof mountRoles>>['wrapper']
const row = (wrapper: Wrapper, code: string) => {
  const found = wrapper.findAll('tbody tr').find(item => item.text().includes(code))
  if (!found) throw new Error('missing row ' + code)
  return found
}
function dialogButton(label: string) {
  const found = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!found) throw new Error('missing dialog button ' + label)
  return found as HTMLButtonElement
}
async function fill(label: string, value: string) {
  const owner = [...document.querySelectorAll<HTMLLabelElement>('dialog[open] label')].find(item => item.textContent?.replace('*', '').trim() === label)
  const input = owner ? document.getElementById(owner.htmlFor) as HTMLInputElement | null : null
  if (!input) throw new Error('missing field ' + label)
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}
const field = (label: string) => {
  const owner = [...document.querySelectorAll<HTMLLabelElement>('dialog[open] label')].find(item => item.textContent?.replace('*', '').trim() === label)
  return document.getElementById(owner?.htmlFor ?? '') as HTMLInputElement | null
}
const toasts = () => useToast().items.map(item => item.message)

describe('roles view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string, options?: { method?: string }) => Promise.resolve(path === '/api/v1/role-links'
      ? [{ roleId: '2', parentId: '1' }] : options?.method ? roles[1] : roles))
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists roles, names system roles in the user\'s language and protects them', async () => {
    const { wrapper } = await mountRoles()
    expect(wrapper.text()).toContain('3 个角色')
    const admin = row(wrapper, 'tenant-admin')
    expect(admin.text()).toContain('租户管理员')
    expect(admin.text()).toContain('系统')
    expect(admin.findAll('button').map(button => button.text().trim())).toEqual(['授权', '分配', '复制'])
    // Only custom roles inherit; the line below a role names what it inherits from.
    expect(row(wrapper, 'auditors').findAll('button').map(button => button.text().trim())).toContain('继承')
    expect(row(wrapper, 'auditors').text()).toContain('继承自 租户管理员')
    expect(row(wrapper, 'buyers').text()).toContain('已停用')
    expect(row(wrapper, 'auditors').text()).toContain('只读')
    wrapper.unmount()
  })

  it('searches after a pause in typing', async () => {
    vi.useFakeTimers()
    const { wrapper } = await mountRoles()
    await wrapper.get('input[aria-label="搜索角色"]').setValue(' 审计 ')
    await flushPromises()
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles', expect.objectContaining({ query: { q: '审计' } }))
    vi.useRealTimers()
    wrapper.unmount()
  })

  it('creates, edits, copies, toggles and deletes roles', async () => {
    const { wrapper } = await mountRoles()
    await wrapper.findAll('button').find(button => button.text() === '新建角色')?.trigger('click')
    await flushPromises()
    dialogButton('新建角色').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请输入角色名称')
    await fill('角色名称', '审计员')
    dialogButton('新建角色').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请输入角色编码')
    await fill('角色编码', 'auditors')
    await fill('说明', '只读')
    dialogButton('新建角色').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles', { method: 'POST', body: { code: 'auditors', name: '审计员', description: '只读' } })

    await row(wrapper, 'auditors').get('[aria-label="编辑 审计员"]').trigger('click')
    await flushPromises()
    await fill('角色名称', '审计')
    dialogButton('保存').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/2', { method: 'PUT', body: { code: 'auditors', name: '审计', description: '只读' } })

    await row(wrapper, 'tenant-admin').get('[aria-label="复制 租户管理员"]').trigger('click')
    await flushPromises()
    expect(document.querySelector('dialog[open]')?.textContent).toContain('基于 租户管理员 新建一个自定义角色')
    expect(field('角色编码')?.value).toBe('tenant-admin-copy')
    expect(field('角色名称')?.value).toBe('租户管理员（副本）')
    expect(field('说明')).toBeNull()
    dialogButton('复制').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/1/copy', { method: 'POST', body: { code: 'tenant-admin-copy', name: '租户管理员（副本）' } })

    await row(wrapper, 'auditors').findAll('button').find(button => button.text() === '停用')?.trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/2/disable', { method: 'POST' })
    await row(wrapper, 'buyers').findAll('button').find(button => button.text() === '启用')?.trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/3/enable', { method: 'POST' })

    await row(wrapper, 'buyers').get('[aria-label="删除 采购"]').trigger('click')
    await flushPromises()
    dialogButton('删除').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/3', { method: 'DELETE' })
    expect(toasts()).toEqual(expect.arrayContaining(['角色已创建', '角色已更新', '角色已复制', '角色状态已更新', '角色已删除']))
    wrapper.unmount()
  })

  it('shows refusals in the dialog or as a toast, and load failures', async () => {
    api.request.mockImplementation((_path: string, options?: { method?: string }) => options?.method
      ? Promise.reject(new ApiError('角色编码“auditors”已被使用。', 409)) : Promise.resolve(roles))
    const { wrapper } = await mountRoles()
    await row(wrapper, 'auditors').get('[aria-label="编辑 审计员"]').trigger('click')
    await flushPromises()
    dialogButton('保存').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('角色编码“auditors”已被使用。')
    dialogButton('取消').click()
    await flushPromises()
    await row(wrapper, 'auditors').findAll('button').find(button => button.text() === '停用')?.trigger('click')
    await flushPromises()
    expect(toasts()).toContain('角色编码“auditors”已被使用。')
    wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = await mountRoles()
    expect(failed.wrapper.get('[role="alert"]').text()).toContain('无权访问')
    failed.wrapper.unmount()
  })

  it('opens what a role inherits', async () => {
    const { wrapper } = await mountRoles()
    api.request.mockImplementation((path: string) => Promise.resolve(path.endsWith('/inheritance')
      ? { role: roles[1], parents: [], ancestors: [], descendants: [] } : path === '/api/v1/role-links' ? [] : roles))
    await row(wrapper, 'auditors').get('[aria-label="设置 审计员 的继承"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/2/inheritance')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('审计员 的继承')
    wrapper.unmount()
  })

  it('opens who has a role', async () => {
    const { wrapper } = await mountRoles()
    api.request.mockImplementation((path: string) => Promise.resolve(path.endsWith('/assignments') ? [] : roles))
    await row(wrapper, 'auditors').get('[aria-label="分配 审计员"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/roles/2/assignments')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('审计员 的分配')
    wrapper.unmount()
  })
})
