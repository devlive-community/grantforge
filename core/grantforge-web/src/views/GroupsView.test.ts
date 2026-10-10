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

const { default: GroupsView } = await import('./GroupsView.vue')

const ops = { id: '1', code: 'ops', name: '运维组', description: '值班', members: 1, createdAt: '2026-10-01T08:00:00Z' }
const dev = { id: '2', code: 'dev', name: 'Developers', members: 0, createdAt: '2026-10-01T08:00:00Z' }
const erin = { accountId: '11', username: 'erin', displayName: '艾琳', addedAt: '2026-10-01T08:00:00Z' }
const users = [
  { id: '11', username: 'erin', displayName: '艾琳', status: 'ACTIVE', systemAccount: false, mustChangePassword: false, createdAt: '2026-10-01T08:00:00Z' },
  { id: '12', username: 'frank', status: 'ACTIVE', systemAccount: false, mustChangePassword: false, createdAt: '2026-10-01T08:00:00Z' },
]

function answer(path: string, options?: { method?: string }) {
  if (path === '/api/v1/groups' && !options?.method) return Promise.resolve({ items: [ops, dev], page: 1, size: 20, total: 2 })
  if (path === '/api/v1/groups/1/members' && !options?.method) return Promise.resolve({ items: [erin], page: 1, size: 50, total: 60 })
  if (path.endsWith('/members') && !options?.method) return Promise.resolve({ items: [], page: 1, size: 50, total: 0 })
  if (path === '/api/v1/users') return Promise.resolve({ items: users, page: 1, size: 50, total: 2 })
  if (path.endsWith('/members') || path.endsWith('/members/remove')) return Promise.resolve({ changed: 1 })
  return Promise.resolve(options?.method === 'DELETE' ? null : ops)
}

async function mountGroups() {
  const mounted = await mountView(GroupsView, {}, '/admin/groups')
  await flushPromises()
  return mounted
}

async function fill(label: string, value: string) {
  const owner = [...document.querySelectorAll<HTMLLabelElement>('dialog[open] label')]
    .find(item => item.textContent?.replace('*', '').trim() === label)
  const input = owner ? document.getElementById(owner.htmlFor) as HTMLInputElement | null : null
  if (!input) throw new Error('missing field ' + label)
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function submit() {
  document.querySelector('form#group-form')?.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

function dialogButton(label: string) {
  const found = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim().startsWith(label))
  if (!found) throw new Error('missing dialog button ' + label)
  return found as HTMLButtonElement
}

function checkbox(label: string) {
  const found = [...document.querySelectorAll<HTMLInputElement>('dialog[open] input[type="checkbox"]')]
    .find(input => input.getAttribute('aria-label') === label || input.closest('label')?.textContent?.includes(label))
  if (!found) throw new Error('missing checkbox ' + label)
  return found
}

const toasts = () => useToast().items.map(item => item.message)
/** A field's message floats out of the dialog, so an element's words are read from the document as well. */
function textOf(root: Element | null | undefined) {
  const tips = Array.from(root?.querySelectorAll('[aria-describedby]') ?? [])
    .map(control => document.getElementById(control.getAttribute('aria-describedby') || '')?.textContent ?? '')
  return [root?.textContent ?? '', ...tips].join('')
}

describe('groups view', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = ''; vi.useRealTimers() })

  it('lists groups and searches after a pause', async () => {
    vi.useFakeTimers()
    const { wrapper } = await mountGroups()
    expect(wrapper.text()).toContain('2 个用户组')
    expect(wrapper.text()).toContain('值班')
    await wrapper.get('[aria-label="搜索用户组"]').setValue(' 运维 ')
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/groups', expect.objectContaining({ query: { q: '运维', page: 1, size: 20 } }))
    wrapper.unmount()
  })

  it('checks, creates, edits and deletes groups', async () => {
    const { wrapper } = await mountGroups()
    await wrapper.findAll('button').find(button => button.text().includes('创建用户组'))?.trigger('click')
    await submit()
    expect(textOf(document.getElementById('group-form'))).toContain('请输入组编码')
    expect(textOf(document.getElementById('group-form'))).toContain('请输入组名称')
    await fill('组编码', 'qa')
    // Filling the code is enough: its message goes without waiting for another submit.
    await flushPromises()
    expect(textOf(document.getElementById('group-form'))).not.toContain('请输入组编码')
    expect(textOf(document.getElementById('group-form'))).toContain('请输入组名称')
    await submit()
    // The code is filled in now, so only the name is still missing.
    expect(textOf(document.getElementById('group-form'))).not.toContain('请输入组编码')
    expect(textOf(document.getElementById('group-form'))).toContain('请输入组名称')
    await fill('组名称', '测试组')
    api.request.mockRejectedValueOnce(new ApiError('用户组编码“qa”已被使用。', 409))
    await submit()
    expect(document.querySelector('form#group-form [role="alert"]')?.textContent).toBe('用户组编码“qa”已被使用。')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/groups', { method: 'POST', body: { code: 'qa', name: '测试组', description: '' } })
    expect(toasts()).toContain('用户组已创建')

    await wrapper.get('[aria-label="编辑 运维组"]').trigger('click')
    await fill('组名称', 'Ops')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/groups/1', { method: 'PUT', body: { code: 'ops', name: 'Ops', description: '值班' } })

    await wrapper.get('[aria-label="删除 运维组"]').trigger('click')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('1 位成员会离开该组')
    dialogButton('删除用户组').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/groups/1', { method: 'DELETE' })
    expect(toasts()).toContain('用户组已删除')
    wrapper.unmount()
  })

  it('adds and removes members in batches', async () => {
    vi.useFakeTimers()
    const { wrapper } = await mountGroups()
    await wrapper.get('[aria-label="管理 运维组 的成员"]').trigger('click')
    await flushPromises()
    const dialog = document.querySelector('dialog[open]')
    expect(dialog?.textContent).toContain('共 60 位成员，仅显示前 1 位')
    // Members already in the group are not offered again.
    expect(dialog?.textContent).toContain('frank')
    expect([...dialog?.querySelectorAll('input[type="checkbox"]') ?? []]).toHaveLength(2)
    expect(dialogButton('添加所选').disabled).toBe(true)

    checkbox('frank').click()
    await flushPromises()
    dialogButton('添加所选（1）').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/groups/1/members', { method: 'POST', body: { accountIds: ['12'] } })
    expect(toasts()).toContain('已添加 1 位成员')

    checkbox('艾琳').click()
    await flushPromises()
    dialogButton('移除所选（1）').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/groups/1/members/remove', { method: 'POST', body: { accountIds: ['11'] } })
    expect(toasts()).toContain('已移除 1 位成员')

    const type = (label: string, value: string) => {
      const input = document.querySelector<HTMLInputElement>(`dialog[open] input[aria-label="${label}"]`)
      if (!input) throw new Error('missing input ' + label)
      input.value = value
      input.dispatchEvent(new Event('input'))
    }
    type('搜索成员', '艾')
    type('搜索要添加的用户', 'fr')
    await flushPromises()
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/groups/1/members', { query: { q: '艾', page: 1, size: 50 } })
    expect(api.request).toHaveBeenCalledWith('/api/v1/users', { query: { q: 'fr', page: 1, size: 50 } })
    wrapper.unmount()
  })

  it('shows failures inside the member dialog and of the list', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) => path === '/api/v1/users'
      ? Promise.reject(new ApiError('无权执行此操作。', 403)) : answer(path, options))
    const { wrapper } = await mountGroups()
    await wrapper.get('[aria-label="管理 Developers 的成员"]').trigger('click')
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('无权执行此操作。')
    wrapper.unmount()

    api.request.mockRejectedValueOnce(new ApiError('服务暂时不可用。', 503))
    const failed = await mountGroups()
    expect(failed.wrapper.text()).toContain('服务暂时不可用。')
    failed.wrapper.unmount()
  })
})
