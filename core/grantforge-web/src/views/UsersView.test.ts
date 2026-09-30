// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useToast } from '@/stores/toast'
import { ApiError } from '@/lib/api'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn(), allOptions: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: UsersView } = await import('./UsersView.vue')

const users = [
  { id: 1, name: 'root', isSystem: true, active: true, roles: [{ id: 1, name: '管理员' }] },
  { id: 2, name: 'alex', email: 'alex@example.org', active: true, roles: [] },
]
const page = { content: users, number: 1, size: 20, totalElements: 2, totalPages: 1 }

function dialogButton(label: string) {
  const button = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!button) throw new Error('missing dialog button ' + label)
  return button as HTMLButtonElement
}

async function mountUsers() {
  const mounted = await mountView(UsersView)
  await flushPromises()
  return mounted
}

describe('users view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.allOptions.mockReset()
    api.request.mockImplementation((path: string) => path === '/api/v1/user' ? Promise.resolve(page) : Promise.resolve(3))
    api.allOptions.mockResolvedValue([{ id: 1, name: '管理员' }, { id: 2, name: '审计员', description: '只读' }])
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists users with roles and filters the current page', async () => {
    const { wrapper } = await mountUsers()
    expect(wrapper.text()).toContain('2 位用户')
    expect(wrapper.text()).toContain('alex@example.org')
    expect(wrapper.text()).toContain('尚未分配角色')
    expect(wrapper.get('[aria-label="删除用户 root"]').attributes('disabled')).toBeDefined()
    await wrapper.get('input').setValue('ALEX')
    expect(wrapper.text()).not.toContain('root')
    wrapper.unmount()
  })

  it('validates and creates a user with roles', async () => {
    const { wrapper } = await mountUsers()
    await wrapper.findAll('button').find(button => button.text().includes('创建用户'))?.trigger('click')
    await flushPromises()
    dialogButton('创建用户').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请输入用户名')

    const [name, password] = [...document.querySelectorAll<HTMLInputElement>('dialog[open] input:not([type="checkbox"])')]
    if (!name || !password) throw new Error('missing inputs')
    name.value = 'bob'; name.dispatchEvent(new Event('input'))
    await nextTick()
    dialogButton('创建用户').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请设置初始密码')

    password.value = 'secret'; password.dispatchEvent(new Event('input'))
    document.querySelector<HTMLInputElement>('dialog[open] input[type="checkbox"]')?.click()
    await nextTick()
    dialogButton('创建用户').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/user/register', { method: 'POST', body: { username: 'bob', password: 'secret' } })
    expect(api.request).toHaveBeenCalledWith('/api/v1/user/role', { method: 'PUT', body: { id: '3', values: [1] } })
    expect(useToast().items.at(-1)?.message).toBe('用户已创建')
    wrapper.unmount()
  })

  it('reports a created user whose roles could not be assigned', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/user' ? Promise.resolve(page)
      : path === '/api/v1/user/role' ? Promise.reject(new ApiError('无权分配')) : Promise.resolve(3))
    const { wrapper } = await mountUsers()
    await wrapper.findAll('button').find(button => button.text().includes('创建用户'))?.trigger('click')
    await flushPromises()
    const [name, password] = [...document.querySelectorAll<HTMLInputElement>('dialog[open] input:not([type="checkbox"])')]
    if (!name || !password) throw new Error('missing inputs')
    name.value = 'bob'; name.dispatchEvent(new Event('input'))
    password.value = 'secret'; password.dispatchEvent(new Event('input'))
    document.querySelector<HTMLInputElement>('dialog[open] input[type="checkbox"]')?.click()
    await nextTick()
    dialogButton('创建用户').click()
    await flushPromises()
    expect(useToast().items.at(-1)).toMatchObject({ kind: 'error', message: '用户已创建，但角色分配失败：无权分配' })
    wrapper.unmount()
  })

  it('assigns roles and requires at least one', async () => {
    const { wrapper } = await mountUsers()
    await wrapper.findAll('button').filter(button => button.text().includes('分配角色'))[1]?.trigger('click')
    await flushPromises()
    dialogButton('保存角色').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请至少选择一个角色')
    document.querySelectorAll<HTMLInputElement>('dialog[open] input[type="checkbox"]')[1]?.click()
    await nextTick()
    dialogButton('保存角色').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/user/role', { method: 'PUT', body: { id: '2', values: [2] } })
    wrapper.unmount()
  })

  it('deletes a user after confirmation and shows failures in the dialog', async () => {
    const { wrapper } = await mountUsers()
    await wrapper.get('[aria-label="删除用户 alex"]').trigger('click')
    await nextTick()
    api.request.mockRejectedValueOnce(new ApiError('删除失败'))
    dialogButton('确认删除').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('删除失败')
    dialogButton('确认删除').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/user', { method: 'DELETE', query: { id: 2 } })
    expect(useToast().items.at(-1)?.message).toBe('用户已删除')
    wrapper.unmount()
  })
})
