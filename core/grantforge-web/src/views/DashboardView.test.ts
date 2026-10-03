// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mountView } from '../../tests/unit/mountView'
import { useAuth } from '@/stores/auth'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: DashboardView } = await import('./DashboardView.vue')

const page = (total: number) => ({ items: [], page: 1, size: 1, total })

describe('dashboard', () => {
  beforeEach(() => { api.request.mockReset() })

  it('shows totals, recent members and no warning when everything loads', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(({
      '/api/v1/users': { items: [{ id: '1', username: 'admin', displayName: 'The Admin', status: 'ACTIVE', createdAt: '2026-10-01T08:00:00Z', primaryUnitName: '总部' },
        { id: '2', username: 'bob', status: 'DISABLED', lockedUntil: '9999-01-01T00:00:00Z', createdAt: '2026-10-01T08:00:00Z' }], page: 1, size: 5, total: 12 },
      '/api/v1/org-units': [{ id: '1' }, { id: '2' }, { id: '3' }],
      '/api/v1/groups': page(8),
      '/api/v1/positions': page(4),
    } as Record<string, unknown>)[path]))
    const { wrapper } = await mountView(DashboardView)
    await flushPromises()
    expect(wrapper.text()).toContain('12')
    expect(wrapper.findAll('.panel p.text-\\[30px\\]').map(total => total.text())).toEqual(['12', '3', '8', '4'])
    expect(api.request.mock.calls.map(call => call[0])).not.toContain('/api/v1/role')
    expect(wrapper.text()).toContain('The Admin')
    expect(wrapper.text()).toContain('总部')
    expect(wrapper.text()).toContain('未分配部门')
    expect(wrapper.text()).toContain('已锁定')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('工作空间数据已更新')
    wrapper.unmount()
  })

  it('warns about partial data and shows placeholders for failed totals', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/groups' ? Promise.reject(new Error('403'))
      : Promise.resolve(path === '/api/v1/users' ? { items: [], page: 1, size: 5, total: 0 } : path === '/api/v1/org-units' ? [] : page(0)))
    const { wrapper } = await mountView(DashboardView)
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('部分数据暂时不可用')
    expect(wrapper.text()).toContain('—')
    expect(wrapper.text()).toContain('暂时没有可显示的成员')
    wrapper.unmount()
  })

  it('reloads on refresh', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(path === '/api/v1/users'
      ? { items: [], page: 1, size: 5, total: 0 } : path === '/api/v1/org-units' ? [] : page(0)))
    const { wrapper } = await mountView(DashboardView)
    await flushPromises()
    const calls = api.request.mock.calls.length
    await wrapper.findAll('button').find(button => button.text().includes('刷新概览'))?.trigger('click')
    await flushPromises()
    expect(api.request.mock.calls.length).toBe(calls * 2)
    wrapper.unmount()
  })

  it('shows and loads only the cards of pages the user may open', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(path === '/api/v1/users'
      ? { items: [], page: 1, size: 5, total: 2 } : path === '/api/v1/org-units' ? [] : page(0)))
    const { wrapper } = await mountView(DashboardView)
    await flushPromises()
    useAuth().authorization = { version: 1, unrestricted: false, roles: [], resources: ['system.user'], permissions: [], fields: {} }
    api.request.mockClear()
    await wrapper.findAll('button').find(button => button.text().includes('刷新概览'))?.trigger('click')
    await flushPromises()
    expect(api.request.mock.calls.map(call => call[0])).toEqual(['/api/v1/users'])
    expect(wrapper.text()).not.toContain('用户组')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
