// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { mountView } from '../../tests/unit/mountView'
import { setDate } from '../../tests/unit/datePicker'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: AccessAuditView } = await import('./AccessAuditView.vue')

const dw = { id: '7', name: 'dw', label: 'Data warehouse', serviceType: 'hive', enabled: true, available: true, values: {}, secretsSet: [] }
const lake = { ...dw, id: '8', name: 'lake', label: 'Lake' }
const event = (id: string, extra: Record<string, unknown> = {}) => ({ id, eventId: id, agentInstance: 'hs2-1', occurredAt: '2026-10-02T12:00:00Z',
  user: 'alice', clientIp: '10.0.0.9', resource: 'sales.orders', accessType: 'select', action: 'SELECT', outcome: 'ALLOWED',
  policyId: '11', policyName: 'sales readers', policyVersion: 3, enforcer: 'GRANTFORGE', request: 'select * from orders', ...extra })

describe('access audit view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string) => {
      if (path === '/api/v1/services') return Promise.resolve([dw, lake])
      if (path.includes('cursor=c1')) return Promise.resolve({ events: [event('e3')] })
      if (path.includes('/8/')) return Promise.resolve({ events: [] })
      return Promise.resolve({ events: [event('e1'), event('e2', { outcome: 'DENIED', enforcer: 'NATIVE', policyId: undefined,
        policyName: undefined, request: undefined, action: undefined, clientIp: undefined })], next: 'c1' })
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists what agents decided, newest first, a page at a time', async () => {
    const { wrapper } = await mountView(AccessAuditView, {}, '/data/access-audit')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/services/7/access-events?limit=50')
    const allowed = wrapper.get('[data-event="e1"]')
    expect(allowed.text()).toContain('alice')
    expect(allowed.text()).toContain('10.0.0.9')
    expect(allowed.text()).toContain('sales readers')
    expect(allowed.text()).toContain('允许')
    expect(allowed.get('details').text()).toContain('select * from orders')
    const denied = wrapper.get('[data-event="e2"]')
    expect(denied.text()).toContain('拒绝')
    expect(denied.text()).toContain('系统自身权限')
    expect(denied.find('details').exists()).toBe(false)

    await wrapper.findAll('button').find(button => button.text() === '加载更多')?.trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/services/7/access-events?limit=50&cursor=c1')
    expect(wrapper.findAll('[data-event]')).toHaveLength(3)
    expect(wrapper.findAll('button').some(button => button.text() === '加载更多')).toBe(false)
    wrapper.unmount()
  })

  it('searches with the filters that are set', async () => {
    const { wrapper, router } = await mountView(AccessAuditView, {}, '/data/access-audit?service=7')
    await flushPromises()
    const inputs = wrapper.findAll('form input')
    await inputs[0]?.setValue(' alice ')
    await inputs[2]?.setValue('select')
    await setDate(wrapper, '开始时间', '2026-10-01T08:00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const asked = new URL(String(api.request.mock.lastCall?.[0]), 'http://x')
    expect(asked.pathname).toBe('/api/v1/services/7/access-events')
    expect(Object.fromEntries(asked.searchParams)).toEqual({ limit: '50', user: 'alice', accessType: 'select',
      from: new Date('2026-10-01T08:00').toISOString() })

    await router.replace('/data/access-audit?service=8')
    await flushPromises()
    expect(wrapper.text()).toContain('没有符合条件的访问记录')
    wrapper.unmount()
  })

  it('names deleted policies and explains failures and missing services', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(path === '/api/v1/services' ? [dw]
      : { events: [event('e1', { policyName: undefined }), event('e2', { policyId: undefined, policyName: undefined })] }))
    const { wrapper } = await mountView(AccessAuditView, {}, '/data/access-audit')
    await flushPromises()
    expect(wrapper.get('[data-event="e1"]').text()).toContain('策略 11（已删除）')
    expect(wrapper.get('[data-event="e2"]').text()).toContain('—')
    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('无权访问')
    wrapper.unmount()

    api.request.mockResolvedValue([])
    const none = await mountView(AccessAuditView, {}, '/data/access-audit')
    await flushPromises()
    expect(none.wrapper.text()).toContain('还没有数据服务')
    none.wrapper.unmount()
  })
})
