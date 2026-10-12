// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn(), download: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: AuditView } = await import('./AuditView.vue')

const event = (id: string, action: string, outcome = 'SUCCESS') => ({ id, occurredAt: '2026-10-01T08:00:00Z', action, outcome, actorName: 'root',
  targetId: `t-${id}`, reason: 'r', clientIp: '10.0.0.1', userAgent: 'Firefox' })

async function mountAudit() {
  const mounted = await mountView(AuditView, {}, '/admin/audit')
  await flushPromises()
  return mounted
}

describe('audit view', () => {
  beforeEach(() => {
    api.request.mockReset(); api.download.mockReset()
    // 25 events in all: the first page of twenty holds two of them here, the second the last five.
    api.request.mockImplementation((_path: string, options?: { query?: { page?: number; size?: number } }) => Promise.resolve(
      options?.query?.page === 2 ? { items: [event('3', 'LOGOUT')], page: 2, size: 20, total: 25 }
        : { items: [event('1', 'LOGIN_SUCCEEDED'), event('2', 'ACCESS_DENIED', 'FAILURE')], page: 1, size: options?.query?.size ?? 20, total: 25 }))
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists events newest first, a page at a time with the pager every list has', async () => {
    const { wrapper } = await mountAudit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/audit-events/pages', { query: expect.objectContaining({ page: 1, size: 20 }) })
    expect(wrapper.find('[data-event="2"]').text()).toContain('ACCESS_DENIED')
    expect(wrapper.find('[data-event="2"]').text()).toContain('失败')
    expect(wrapper.find('[data-event="1"]').text()).toContain('10.0.0.1')
    expect(wrapper.text()).toContain('1 / 2')
    await wrapper.get('[aria-label="下一页"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/audit-events/pages', { query: expect.objectContaining({ page: 2, size: 20 }) })
    // A page replaces the one before rather than adding to it.
    expect(wrapper.findAll('[data-event]')).toHaveLength(1)
    expect(wrapper.text()).toContain('2 / 2')
    expect(wrapper.text()).not.toContain('加载更多')
  })

  it('searches with the filters and exports them', async () => {
    const { wrapper } = await mountAudit()
    await wrapper.get('[aria-label="下一页"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('input').find(input => input.attributes('type') !== 'datetime-local')?.setValue(' root ')
    // Typed but not searched yet: another page keeps to the search made before.
    await wrapper.get('[aria-label="上一页"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/audit-events/pages', { query: expect.objectContaining({ actor: undefined, page: 1 }) })
    await wrapper.get('[aria-label="下一页"]').trigger('click')
    await flushPromises()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    // A search starts again at its first page.
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/audit-events/pages', { query: expect.objectContaining({ actor: 'root', page: 1 }) })

    globalThis.URL.createObjectURL = vi.fn(() => 'blob:x')
    globalThis.URL.revokeObjectURL = vi.fn()
    api.download.mockResolvedValue({ blob: new Blob(['x']), filename: 'audit.csv' })
    await wrapper.findAll('button').find(button => button.text().includes('导出 CSV'))?.trigger('click')
    await flushPromises()
    expect(api.download).toHaveBeenCalledWith('/api/v1/audit-events/export', expect.objectContaining({ actor: 'root' }))
    expect(useToast().items.map(item => item.message)).toContain('审计事件已导出')

    api.download.mockRejectedValue(new ApiError('无权访问。', 403))
    await wrapper.findAll('button').find(button => button.text().includes('导出 CSV'))?.trigger('click')
    await flushPromises()
    expect(useToast().items.at(-1)).toMatchObject({ message: '无权访问。', kind: 'error' })
  })

  it('says when nothing matches and why the search failed', async () => {
    api.request.mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    const { wrapper } = await mountAudit()
    expect(wrapper.text()).toContain('没有符合条件的审计事件')
    api.request.mockRejectedValue(new ApiError('无权访问。', 403))
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('无权访问')
  })
})
