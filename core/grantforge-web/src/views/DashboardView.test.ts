// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: DashboardView } = await import('./DashboardView.vue')

const page = (total: number, content: unknown[] = []) => ({ content, number: 1, size: 1, totalElements: total, totalPages: 1 })

describe('dashboard', () => {
  beforeEach(() => { api.request.mockReset() })

  it('shows totals, recent members and no warning when everything loads', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(({
      '/api/v1/users': { items: [{ id: '1', username: 'admin', displayName: 'The Admin', status: 'ACTIVE', createdAt: '2026-10-01T08:00:00Z', primaryUnitName: '总部' },
        { id: '2', username: 'bob', status: 'DISABLED', lockedUntil: '9999-01-01T00:00:00Z', createdAt: '2026-10-01T08:00:00Z' }], page: 1, size: 5, total: 12 },
      '/api/v1/role': page(3),
      '/api/v1/menu': page(8),
      '/api/v1/method': page(4),
    } as Record<string, unknown>)[path]))
    const { wrapper } = await mountView(DashboardView)
    await flushPromises()
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('The Admin')
    expect(wrapper.text()).toContain('总部')
    expect(wrapper.text()).toContain('未分配部门')
    expect(wrapper.text()).toContain('已锁定')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('工作空间数据已更新')
    wrapper.unmount()
  })

  it('warns about partial data and shows placeholders for failed totals', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/role' ? Promise.reject(new Error('403'))
      : Promise.resolve(path === '/api/v1/users' ? { items: [], page: 1, size: 5, total: 0 } : page(0)))
    const { wrapper } = await mountView(DashboardView)
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('部分数据暂时不可用')
    expect(wrapper.text()).toContain('—')
    expect(wrapper.text()).toContain('暂时没有可显示的成员')
    wrapper.unmount()
  })

  it('reloads on refresh', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(path === '/api/v1/users'
      ? { items: [], page: 1, size: 5, total: 0 } : page(0)))
    const { wrapper } = await mountView(DashboardView)
    await flushPromises()
    const calls = api.request.mock.calls.length
    await wrapper.findAll('button').find(button => button.text().includes('刷新概览'))?.trigger('click')
    await flushPromises()
    expect(api.request.mock.calls.length).toBe(calls * 2)
    wrapper.unmount()
  })
})
