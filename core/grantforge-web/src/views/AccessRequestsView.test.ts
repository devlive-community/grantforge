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

const { default: AccessRequestsView } = await import('./AccessRequestsView.vue')

const role = (id: string, code: string, name: string) => ({ id, code, name, type: 'CUSTOM', enabled: true })
const options = [
  { role: role('1', 'reports', '报表'), maxDays: 30, held: false, pending: false },
  { role: role('2', 'payer', '出纳'), maxDays: 7, held: true, pending: false },
  { role: role('3', 'audit', '审计'), maxDays: 7, held: false, pending: true },
]
const mine = [
  { id: '9', requesterId: '5', requesterName: 'Alice', role: role('3', 'audit', '审计'), reason: '年审', requestedDays: 3, status: 'PENDING',
    requestedAt: '2026-10-01T08:00:00Z' },
  { id: '8', requesterId: '5', requesterName: 'Alice', role: role('2', 'payer', '出纳'), reason: '月底', requestedDays: 7, status: 'APPROVED',
    requestedAt: '2026-09-01T08:00:00Z', validUntil: '2026-10-08T08:00:00Z', decidedByName: 'Boss', comment: '仅限本月' },
]
const toasts = () => useToast().items.map(item => item.message)
const dialog = () => document.querySelector('dialog[open]') as HTMLDialogElement
function fill(label: RegExp, value: string) {
  const found = [...dialog().querySelectorAll('label')].find(item => label.test(item.textContent ?? ''))
  const input = dialog().querySelector<HTMLInputElement | HTMLTextAreaElement>(`[id="${found?.getAttribute('for') ?? 'missing'}"]`) as HTMLInputElement
  input.value = value; input.dispatchEvent(new Event('input'))
}
const submit = async () => { dialog().querySelector('form')?.dispatchEvent(new Event('submit')); await flushPromises() }

describe('my access requests', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string, options_?: { method?: string }) => {
      if (path === '/api/v1/me/requestable-roles') return Promise.resolve(options)
      if (path === '/api/v1/me/access-requests' && !options_?.method) return Promise.resolve(mine)
      return Promise.resolve(null)
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows what may be asked for and the requests so far', async () => {
    const { wrapper } = await mountView(AccessRequestsView, {}, '/requests')
    await flushPromises()
    expect(wrapper.get('[data-option="payer"]').text()).toContain('已拥有')
    expect(wrapper.get('[data-option="audit"]').text()).toContain('等待审批')
    const items = wrapper.findAll('[data-request]').map(item => item.text())
    expect(items[0]).toContain('待审批')
    expect(items[1]).toContain('已授予')
    expect(items[1]).toContain('Boss：仅限本月')
    await wrapper.get('[aria-label="撤回 审计 的申请"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/me/access-requests/9/cancel', { method: 'POST' })
    expect(toasts()).toContain('申请已撤回')
    wrapper.unmount()
  })

  it('asks for a role after checking the form', async () => {
    const { wrapper } = await mountView(AccessRequestsView, {}, '/requests')
    await flushPromises()
    await wrapper.get('[data-option="reports"]').findAll('button').find(button => button.text() === '申请')?.trigger('click')
    await flushPromises()
    await submit()
    expect(dialog().querySelector('[role="alert"]')?.textContent).toBe('请填写申请理由')
    fill(/申请理由/, '季度对账'); fill(/天数/, '31')
    await submit()
    expect(dialog().querySelector('[role="alert"]')?.textContent).toBe('请填写 1 到 30 天')
    fill(/天数/, '10')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/me/access-requests', { method: 'POST', body: { roleId: '1', reason: '季度对账', days: 10 } })
    expect(toasts()).toContain('申请已提交')
    wrapper.unmount()
  })

  it('reports failures', async () => {
    api.request.mockRejectedValue(new ApiError('服务暂时不可用。', 503))
    const { wrapper } = await mountView(AccessRequestsView, {}, '/requests')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('服务暂时不可用。')
    wrapper.unmount()
  })
})
