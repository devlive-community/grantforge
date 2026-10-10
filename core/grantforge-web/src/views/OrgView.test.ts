// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'
import { authorization, everything } from '../../tests/unit/authorization'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: OrgView } = await import('./OrgView.vue')

const units = [
  { id: '1', code: 'hq', name: '总部', sortOrder: 0, depth: 0 },
  { id: '4', code: 'lab', name: '实验室', sortOrder: 1, depth: 0 },
  { id: '2', parentId: '1', code: 'sales', name: '销售部', sortOrder: 0, depth: 1 },
  { id: '5', parentId: '1', code: 'rnd', name: '研发部', sortOrder: 1, depth: 1 },
  { id: '3', parentId: '2', code: 'east', name: '华东', sortOrder: 0, depth: 2 },
]
const admin = { username: 'admin', tenantCode: 'default', tenantName: 'Default', systemAccount: true, passwordChangeRequired: false }

function answer(path: string, options?: { method?: string }) {
  if (path === '/api/v1/org-units' && !options?.method) return Promise.resolve(units)
  if (options?.method === 'DELETE') return Promise.resolve(null)
  return Promise.resolve(units[1])
}

async function mountOrg(canChange = true) {
  const mounted = await mountView(OrgView, {}, '/admin/org')
  useAuth().updated(admin)
  useAuth().authorization = authorization(canChange ? everything() : ['system', 'system.org'])
  await flushPromises()
  return mounted
}

async function select(wrapper: Awaited<ReturnType<typeof mountOrg>>['wrapper'], id: string) {
  await wrapper.get(`[role="treeitem"][data-id="${id}"]`).trigger('click')
  await flushPromises()
}

const button = (wrapper: Awaited<ReturnType<typeof mountOrg>>['wrapper'], label: string) => {
  const found = wrapper.findAll('button').find(item => item.text().trim() === label)
  if (!found) throw new Error('missing button ' + label)
  return found
}

function dialogButton(label: string) {
  const found = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!found) throw new Error('missing dialog button ' + label)
  return found as HTMLButtonElement
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

const toasts = () => useToast().items.map(item => item.message)
/** A field's message floats out of the dialog, so an element's words are read from the document as well. */
function textOf(root: Element | null | undefined) {
  const tips = Array.from(root?.querySelectorAll('[aria-describedby]') ?? [])
    .map(control => document.getElementById(control.getAttribute('aria-describedby') || '')?.textContent ?? '')
  return [root?.textContent ?? '', ...tips].join('')
}

describe('organization view', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows the tree and the selected department', async () => {
    const { wrapper } = await mountOrg()
    expect(wrapper.findAll('[role="treeitem"]').map(item => item.attributes('data-id'))).toEqual(['1', '2', '3', '5', '4'])
    expect(wrapper.text()).toContain('在左侧选择一个部门')
    await select(wrapper, '3')
    expect(wrapper.text()).toContain('华东')
    expect(wrapper.text()).toContain('第 3 层')
    expect(wrapper.text()).toContain('总部 / 销售部')
    await select(wrapper, '1')
    expect(wrapper.text()).toContain('（顶级）')
    wrapper.unmount()
  })

  it('hides changes from users who may only read', async () => {
    const { wrapper } = await mountOrg(false)
    await select(wrapper, '2')
    expect(wrapper.findAll('button').map(item => item.text())).not.toContain('编辑')
    expect(wrapper.text()).not.toContain('新建顶级部门')
    wrapper.unmount()
  })

  it('creates root and child departments after checking the form', async () => {
    const { wrapper } = await mountOrg()
    await button(wrapper, '新建顶级部门').trigger('click')
    document.querySelector('form#org-unit')?.dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()
    expect(textOf(document.getElementById('org-unit'))).toContain('请输入部门编码')
    expect(textOf(document.getElementById('org-unit'))).toContain('请输入部门名称')
    await fill('部门编码', 'lab')
    document.querySelector('form#org-unit')?.dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()
    // The code is filled in now, so only the name is still missing.
    expect(textOf(document.getElementById('org-unit'))).not.toContain('请输入部门编码')
    expect(textOf(document.getElementById('org-unit'))).toContain('请输入部门名称')
    await fill('部门名称', '实验室')
    dialogButton('新建部门').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/org-units', { method: 'POST', body: { parentId: null, code: 'lab', name: '实验室' } })
    expect(toasts()).toContain('部门已创建')

    await select(wrapper, '2')
    await button(wrapper, '添加下级部门').trigger('click')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('上级部门：销售部')
    await fill('部门编码', 'west')
    await fill('部门名称', '华西')
    api.request.mockRejectedValueOnce(new ApiError('部门编码“west”已被使用。', 409))
    dialogButton('新建部门').click()
    await flushPromises()
    expect(document.querySelector('form#org-unit [role="alert"]')?.textContent).toBe('部门编码“west”已被使用。')
    wrapper.unmount()
  })

  it('edits, reorders, moves and deletes the selected department', async () => {
    const { wrapper } = await mountOrg()
    await select(wrapper, '5')
    await button(wrapper, '编辑').trigger('click')
    await fill('部门名称', '研发中心')
    dialogButton('保存').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/org-units/5', { method: 'PUT', body: { code: 'rnd', name: '研发中心' } })

    await select(wrapper, '5')
    expect(button(wrapper, '下移').attributes('disabled')).toBeDefined()
    await button(wrapper, '上移').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/org-units/5/move', { method: 'POST', body: { parentId: '1', position: 0 } })

    await select(wrapper, '1')
    await button(wrapper, '下移').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/org-units/1/move', { method: 'POST', body: { parentId: null, position: 1 } })

    await select(wrapper, '2')
    await button(wrapper, '移动到…').trigger('click')
    ;(document.querySelector('dialog[open] [role="combobox"]') as HTMLElement).click()
    await flushPromises()
    // The department and everything below it cannot be its own new parent.
    const options = [...document.querySelectorAll('[role="option"]')].map(item => item.textContent?.trim())
    expect(options).toEqual(['（顶级）', '总部', '— 研发部', '实验室'])
    dialogButton('移动').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/org-units/2/move', { method: 'POST', body: { parentId: '1', position: 2 } })
    expect(toasts()).toContain('部门已移动')

    await select(wrapper, '3')
    await button(wrapper, '删除').trigger('click')
    dialogButton('删除').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/org-units/3', { method: 'DELETE' })
    expect(toasts()).toContain('部门已删除')
    wrapper.unmount()
  })

  it('reports failed quick moves and failed loading', async () => {
    const { wrapper } = await mountOrg()
    await select(wrapper, '5')
    api.request.mockRejectedValueOnce(new ApiError('无权执行此操作。', 403))
    await button(wrapper, '上移').trigger('click')
    await flushPromises()
    expect(useToast().items.at(-1)).toMatchObject({ message: '无权执行此操作。', kind: 'error' })
    wrapper.unmount()

    api.request.mockRejectedValueOnce(new ApiError('服务暂时不可用。', 503))
    const failed = await mountOrg()
    expect(failed.wrapper.text()).toContain('服务暂时不可用。')
    failed.wrapper.unmount()

    api.request.mockResolvedValueOnce([])
    const empty = await mountOrg()
    expect(empty.wrapper.text()).toContain('还没有部门')
    empty.wrapper.unmount()
  })
})
