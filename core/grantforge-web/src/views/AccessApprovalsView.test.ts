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

const { default: AccessApprovalsView } = await import('./AccessApprovalsView.vue')

const role = (id: string, code: string, name: string, type = 'CUSTOM') => ({ id, code, name, type, enabled: true })
const pending = { id: '9', requesterId: '5', requesterName: 'Alice', requesterUsername: 'alice', role: role('1', 'reports', '报表'), reason: '季度对账',
  requestedDays: 10, status: 'PENDING', requestedAt: '2026-10-01T08:00:00Z' }
const granted = { ...pending, id: '8', status: 'APPROVED', validUntil: '2026-10-08T08:00:00Z' }
const toasts = () => useToast().items.map(item => item.message)
const dialog = () => document.querySelector('dialog[open]') as HTMLDialogElement
/** A field's message floats out of the dialog, so an element's words are read from the document as well. */
function textOf(root: Element | null | undefined) {
  const tips = Array.from(root?.querySelectorAll('[aria-describedby]') ?? [])
    .map(control => document.getElementById(control.getAttribute('aria-describedby') || '')?.textContent ?? '')
  return [root?.textContent ?? '', ...tips].join('')
}
function fill(label: RegExp, value: string) {
  const found = [...dialog().querySelectorAll('label')].find(item => label.test(item.textContent ?? ''))
  const input = dialog().querySelector<HTMLInputElement>(`[id="${found?.getAttribute('for') ?? 'missing'}"]`) as HTMLInputElement
  input.value = value; input.dispatchEvent(new Event('input'))
}
const submit = async () => { dialog().querySelector('form')?.dispatchEvent(new Event('submit')); await flushPromises() }
const button = (label: string) => [...dialog().querySelectorAll<HTMLButtonElement>('button')].find(item => item.textContent?.trim() === label) as HTMLButtonElement

describe('access approvals', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string, options?: { method?: string; query?: { status?: string } }) => {
      if (path === '/api/v1/access-requests') return Promise.resolve(options?.query?.status === 'APPROVED' ? [granted] : [pending])
      if (path === '/api/v1/roles') return Promise.resolve([role('1', 'reports', '报表'), role('2', 'payer', '出纳'), role('3', 'tenant-admin', '管理员', 'SYSTEM')])
      if (path === '/api/v1/requestable-roles' && !options?.method) return Promise.resolve([{ role: role('1', 'reports', '报表'), maxDays: 30 }])
      return Promise.resolve(null)
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('approves for fewer days and rejects with a comment', async () => {
    const { wrapper } = await mountView(AccessApprovalsView, {}, '/admin/access-requests')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-requests', { query: { status: 'PENDING' } })
    expect(wrapper.get('[data-request]').text()).toContain('Alice')
    await wrapper.get('[aria-label="通过 Alice 的申请"]').trigger('click')
    await flushPromises()
    fill(/授予天数/, '11')
    await submit()
    expect(textOf(dialog())).toContain('请填写 1 到 10 天')
    fill(/授予天数/, '5'); fill(/审批意见/, '仅限本月')
    api.request.mockRejectedValueOnce(new ApiError('违反职责分离约束', 409))
    await submit()
    expect(dialog().querySelector('[role="alert"]')?.textContent).toBe('违反职责分离约束')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-requests/9/approve', { method: 'POST', body: { days: 5, comment: '仅限本月' } })
    expect(toasts()).toContain('申请已通过')

    await wrapper.get('[aria-label="驳回 Alice 的申请"]').trigger('click')
    await flushPromises()
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-requests/9/reject', { method: 'POST', body: { comment: undefined } })
    expect(toasts()).toContain('申请已驳回')
    wrapper.unmount()
  })

  it('revokes granted requests', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(path === '/api/v1/access-requests' ? [granted] : null))
    const { wrapper } = await mountView(AccessApprovalsView, {}, '/admin/access-requests')
    await flushPromises()
    await wrapper.get('[aria-label="撤销 Alice 的授权"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-requests/8/revoke', { method: 'POST' })
    expect(toasts()).toContain('授权已撤销')
    api.request.mockRejectedValueOnce(new ApiError('该申请已处理', 409))
    await wrapper.get('[aria-label="撤销 Alice 的授权"]').trigger('click')
    await flushPromises()
    expect(useToast().items.at(-1)).toMatchObject({ message: '该申请已处理', kind: 'error' })
    wrapper.unmount()
  })

  it('chooses the requestable roles, without system roles', async () => {
    const { wrapper } = await mountView(AccessApprovalsView, {}, '/admin/access-requests')
    await flushPromises()
    await wrapper.findAll('button').find(item => item.text() === '可申请角色')?.trigger('click')
    await flushPromises()
    const list = dialog().querySelector('[data-requestable]') as HTMLElement
    expect(list.textContent).not.toContain('管理员')
    const payer = [...list.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')].find(input => input.getAttribute('aria-label') === '出纳') as HTMLInputElement
    payer.click()
    await flushPromises()
    const days = dialog().querySelector<HTMLInputElement>('[aria-label="出纳 的最长天数"]') as HTMLInputElement
    days.value = '400'; days.dispatchEvent(new Event('input'))
    button('保存').click()
    await flushPromises()
    expect(textOf(dialog())).toContain('每个角色的期限须为 1 到 365 天')
    expect(days.getAttribute('aria-describedby')).toBe('days-2-tip')
    days.value = '14'; days.dispatchEvent(new Event('input'))
    button('保存').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/requestable-roles', { method: 'PUT', body: { roles: [{ roleId: '1', maxDays: 30 }, { roleId: '2', maxDays: 14 }] } })
    expect(toasts()).toContain('可申请角色已保存')
    wrapper.unmount()
  })

  it('reports failures', async () => {
    api.request.mockRejectedValue(new ApiError('服务暂时不可用。', 503))
    const { wrapper } = await mountView(AccessApprovalsView, {}, '/admin/access-requests')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('服务暂时不可用。')
    wrapper.unmount()
  })
})
