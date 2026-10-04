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
    api.request.mockImplementation((path: string) => Promise.resolve(path.includes('cursor=') ? { events: [event('3', 'LOGOUT')] }
      : { events: [event('1', 'LOGIN_SUCCEEDED'), event('2', 'ACCESS_DENIED', 'FAILURE')], next: 'c1' }))
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists events newest first and loads more', async () => {
    const { wrapper } = await mountAudit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/audit-events?limit=50')
    expect(wrapper.find('[data-event="2"]').text()).toContain('ACCESS_DENIED')
    expect(wrapper.find('[data-event="2"]').text()).toContain('失败')
    expect(wrapper.find('[data-event="1"]').text()).toContain('10.0.0.1')
    const more = wrapper.findAll('button').find(button => button.text() === '加载更多')
    await more?.trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/audit-events?limit=50&cursor=c1')
    expect(wrapper.findAll('[data-event]')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('加载更多')
  })

  it('searches with the filters and exports them', async () => {
    const { wrapper } = await mountAudit()
    await wrapper.findAll('input').find(input => input.attributes('type') !== 'datetime-local')?.setValue(' root ')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/audit-events?limit=50&actor=root')

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
    api.request.mockResolvedValue({ events: [] })
    const { wrapper } = await mountAudit()
    expect(wrapper.text()).toContain('没有符合条件的审计事件')
    api.request.mockRejectedValue(new ApiError('无权访问。', 403))
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('无权访问')
  })
})
