// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn(), allOptions: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: ResourceView } = await import('./ResourceView.vue')

const pageOf = (content: unknown[]) => ({ content, number: 1, size: 20, totalElements: content.length, totalPages: 1 })
const roles = [{ id: 1, name: '管理员', code: 'ADMIN', active: true }]
const methods = [{ id: 1, name: 'GET', method: 'GET', active: true }]
const menus = [{ id: 5, name: '用户', url: '/admin/users', parent: 0, active: true, type: { id: 1, name: '菜单' }, methods }]

function dialogButton(label: string) {
  const button = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!button) throw new Error('missing dialog button ' + label)
  return button as HTMLButtonElement
}

function type(label: string, value: string) {
  const field = [...document.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('dialog[open] input, dialog[open] textarea')]
    .find(input => document.querySelector(`label[for="${input.id}"]`)?.textContent?.includes(label))
  if (!field) throw new Error('missing field ' + label)
  field.value = value
  field.dispatchEvent(new Event('input'))
}

async function mountResource(kind: 'roles' | 'methods' | 'menus') {
  const mounted = await mountView(ResourceView, { props: { kind } })
  await flushPromises()
  return mounted
}

describe('resource view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.allOptions.mockReset()
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (options?.method) return Promise.resolve(null)
      if (path === '/api/v1/role') return Promise.resolve(pageOf(roles))
      if (path === '/api/v1/method') return Promise.resolve(pageOf(methods))
      if (path === '/api/v1/menu') return Promise.resolve(pageOf(menus))
      if (path === '/api/v1/role/menus') return Promise.resolve([{ id: 5, title: '用户', checked: true }, { id: 6, title: '角色' }])
      return Promise.reject(new Error('unexpected ' + path))
    })
    api.allOptions.mockImplementation((path: string) => Promise.resolve(({
      '/api/v1/method': methods,
      '/api/v1/system/menu/type': [{ id: 1, name: '菜单' }],
      '/api/v1/icon': [{ id: 9, name: 'users', code: 'users' }],
      '/api/v1/menu': [{ id: 3, name: '系统', url: '#', parent: 0 }, ...menus],
    } as Record<string, unknown>)[path] ?? []))
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists roles with their codes and creates a role', async () => {
    const { wrapper } = await mountResource('roles')
    expect(wrapper.text()).toContain('角色管理')
    expect(wrapper.text()).toContain('ADMIN')
    await wrapper.findAll('button').find(button => button.text().includes('创建角色'))?.trigger('click')
    await nextTick()
    dialogButton('创建角色').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请输入角色名称')
    type('角色名称', ' 审计员 ')
    type('角色编码', 'AUDITOR')
    await nextTick()
    dialogButton('创建角色').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/role', { method: 'POST', body: expect.objectContaining({ name: '审计员', code: 'AUDITOR', active: true }) })
    expect(useToast().items.at(-1)?.message).toBe('角色已创建')
    wrapper.unmount()
  })

  it('edits a request method and stores the verb as its code', async () => {
    const { wrapper } = await mountResource('methods')
    await wrapper.get('[aria-label="编辑请求方式 GET"]').trigger('click')
    await nextTick()
    dialogButton('保存修改').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/method', { method: 'PUT', body: expect.objectContaining({ id: 1, code: 'GET', method: 'GET' }) })
    wrapper.unmount()
  })

  it('validates menus and creates one with its options', async () => {
    const { wrapper } = await mountResource('menus')
    expect(wrapper.text()).toContain('/admin/users')
    await wrapper.findAll('button').find(button => button.text().includes('创建菜单'))?.trigger('click')
    await flushPromises()
    type('菜单名称', '审计')
    await nextTick()
    dialogButton('创建菜单').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请输入菜单路径')
    type('菜单路径', '/admin/audit')
    type('排序', '0')
    await nextTick()
    dialogButton('创建菜单').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('排序必须是大于或等于 1 的整数')
    type('排序', '2')
    await nextTick()
    dialogButton('创建菜单').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请选择至少一种请求方式')
    document.querySelector<HTMLInputElement>('dialog[open] input[type="checkbox"]')?.click()
    await nextTick()
    dialogButton('创建菜单').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/menu', { method: 'POST', body: expect.objectContaining({ name: '审计', url: '/admin/audit', sorted: 2, level: 1, type: '1', iconId: '9' }) })
    wrapper.unmount()
  })

  it('loads and saves role grants', async () => {
    const { wrapper } = await mountResource('roles')
    await wrapper.findAll('button').find(button => button.text().includes('授权'))?.trigger('click')
    await flushPromises()
    expect(document.querySelector('dialog[open]')?.textContent).toContain('已选择 1 项权限')
    document.querySelectorAll<HTMLInputElement>('dialog[open] input[type="checkbox"]')[1]?.click()
    await nextTick()
    const save = [...document.querySelectorAll('dialog[open] button')].find(button => button.textContent?.includes('保存'))
    ;(save as HTMLButtonElement | undefined)?.click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/role/menus', { method: 'PUT', body: { roleId: 1, menus: [5, 6] } })
    expect(useToast().items.at(-1)?.message).toBe('角色权限已更新')
    wrapper.unmount()
  })

  it('deletes after confirmation', async () => {
    const { wrapper } = await mountResource('roles')
    await wrapper.get('[aria-label^="删除角色"]').trigger('click')
    await nextTick()
    const confirm = [...document.querySelectorAll('dialog[open] button')].find(button => button.textContent?.includes('删除'))
    ;(confirm as HTMLButtonElement | undefined)?.click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/role', { method: 'DELETE', query: { id: 1 } })
    wrapper.unmount()
  })
})
