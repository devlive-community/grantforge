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

const { default: SodView } = await import('./SodView.vue')

const role = (id: string, name: string) => ({ id, code: name.toLowerCase(), name, type: 'CUSTOM', enabled: true })
const payer = role('1', 'Payer'), approver = role('2', 'Approver'), auditor = role('3', 'Auditor')
const payments = { id: '9', code: 'payments', name: '付款与审批', roles: [approver, payer], maxRoles: 1, mode: 'ENFORCE', enabled: true }
const conflicts = [{ constraintId: '9', constraintName: '付款与审批', mode: 'ENFORCE', maxRoles: 1, accountId: '5', accountName: 'Alice', username: 'alice',
  roles: [approver, payer] }]
const toasts = () => useToast().items.map(item => item.message)
const dialog = () => document.querySelector('dialog[open]') as HTMLDialogElement
function field(label: string) {
  const found = [...dialog().querySelectorAll('label')].find(item => item.textContent?.replace('*', '').trim() === label)
  return dialog().querySelector<HTMLInputElement>(`[id="${found?.getAttribute('for') ?? 'missing'}"]`) as HTMLInputElement
}
function fill(label: string, value: string) { const input = field(label); input.value = value; input.dispatchEvent(new Event('input')) }
function check(name: string) {
  const box = [...dialog().querySelectorAll<HTMLElement>('[data-role-list] [role="checkbox"], [data-role-list] input[type="checkbox"]')]
    .find(item => item.closest('label, div')?.textContent?.includes(name))
  box?.click()
}
const submit = async () => { dialog().querySelector('form')?.dispatchEvent(new Event('submit')); await flushPromises() }
const alertText = () => dialog().querySelector('[role="alert"]')?.textContent

describe('separation of duties view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (path === '/api/v1/sod-constraints' && !options?.method) return Promise.resolve([payments])
      if (path === '/api/v1/sod-conflicts') return Promise.resolve(conflicts)
      if (path === '/api/v1/roles') return Promise.resolve([payer, approver, auditor])
      return Promise.resolve(null)
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows the constraints and who conflicts with them', async () => {
    const { wrapper } = await mountView(SodView, {}, '/admin/sod')
    await flushPromises()
    const constraint = wrapper.get('[data-constraint="payments"]')
    expect(constraint.text()).toContain('付款与审批')
    expect(constraint.text()).toContain('强制')
    expect(constraint.text()).toContain('每个账号最多持有其中 1 个角色')
    expect(constraint.text()).toContain('Approver')
    expect(wrapper.text()).toContain('冲突（1）')
    expect(wrapper.get('[data-conflict]').text()).toContain('付款与审批：持有 Approver、Payer，最多 1 个')
    wrapper.unmount()
  })

  it('adds a constraint after checking the form', async () => {
    const { wrapper } = await mountView(SodView, {}, '/admin/sod')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '添加约束')?.trigger('click')
    await flushPromises()
    await submit()
    expect(alertText()).toBe('请输入编码')
    fill('编码', 'audits'); fill('名称', '审计独立')
    check('Payer')
    await flushPromises()
    await submit()
    expect(alertText()).toBe('请至少选择两个角色')
    // The filter narrows the list.
    const filter = dialog().querySelector<HTMLInputElement>('[aria-label="筛选角色"]') as HTMLInputElement
    filter.value = 'aud'; filter.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(dialog().querySelector('[data-role-list]')?.textContent).not.toContain('Approver')
    check('Auditor')
    await flushPromises()
    fill('每个账号最多持有', '2')
    await submit()
    expect(alertText()).toBe('每个账号最多持有的角色数须为 1 到 1')
    fill('每个账号最多持有', '1')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/sod-constraints', { method: 'POST', body: {
      code: 'audits', name: '审计独立', description: undefined, roleIds: ['1', '3'], maxRoles: 1, mode: 'ENFORCE', enabled: true } })
    expect(toasts()).toContain('约束已添加')
    wrapper.unmount()
  })

  it('edits and deletes constraints', async () => {
    const { wrapper } = await mountView(SodView, {}, '/admin/sod')
    await flushPromises()
    await wrapper.get('[aria-label="编辑 付款与审批"]').trigger('click')
    await flushPromises()
    expect(field('编码').disabled).toBe(true)
    fill('说明', '付款人不能审批')
    api.request.mockRejectedValueOnce(new ApiError('职责分离约束无效', 400))
    await submit()
    expect(alertText()).toBe('职责分离约束无效')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/sod-constraints/9', { method: 'PUT', body: expect.objectContaining({
      description: '付款人不能审批', roleIds: ['2', '1'] }) })
    expect(toasts()).toContain('约束已保存')

    await wrapper.get('[aria-label="删除 付款与审批"]').trigger('click')
    await flushPromises()
    ;[...dialog().querySelectorAll<HTMLButtonElement>('button')].find(button => button.textContent?.trim() === '删除')?.click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/sod-constraints/9', { method: 'DELETE' })
    expect(toasts()).toContain('约束已删除')
    wrapper.unmount()
  })

  it('says when there is nothing or it failed to load', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(path.startsWith('/api/v1/sod') ? [] : null))
    const empty = await mountView(SodView, {}, '/admin/sod')
    await flushPromises()
    expect(empty.wrapper.text()).toContain('还没有约束')
    expect(empty.wrapper.text()).toContain('没有冲突')
    empty.wrapper.unmount()
    api.request.mockRejectedValue(new ApiError('服务暂时不可用。', 503))
    const failed = await mountView(SodView, {}, '/admin/sod')
    await flushPromises()
    expect(failed.wrapper.get('[role="alert"]').text()).toBe('服务暂时不可用。')
    failed.wrapper.unmount()
  })
})
