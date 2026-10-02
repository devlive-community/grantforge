// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: HealthView } = await import('./HealthView.vue')

const applications = [{ id: '1', code: 'grantforge-console', name: 'Console', builtin: true, resources: 5 },
  { id: '2', code: 'crm', name: 'CRM', builtin: false, resources: 0 }]
const finding = { relatedId: null, relatedCode: null, tenantCode: null, roleCode: null }
const report = { applicationId: '1', checkedAt: '2026-06-15T12:00:00Z', findings: [
  { ...finding, issue: 'GRANT_ON_DISABLED', resourceId: '10', resourceCode: 'system.user.btn.hidden', tenantCode: 'acme', roleCode: 'auditors' },
  { ...finding, issue: 'ACTION_WITHOUT_API', resourceId: '11', resourceCode: 'system.user.btn.dead' },
  { ...finding, issue: 'ACTION_WITHOUT_API', resourceId: '12', resourceCode: 'system.user.btn.orphan' },
  { ...finding, issue: 'DEPENDENCY_ON_RETIRED_API', resourceId: '11', resourceCode: 'system.user.btn.dead', relatedId: '20', relatedCode: 'api:system.user.retired' },
] }

function answer(path: string) {
  if (path === '/api/v1/applications') return Promise.resolve(applications)
  if (path === '/api/v1/applications/2/health') return Promise.resolve({ applicationId: '2', checkedAt: report.checkedAt, findings: [] })
  return Promise.resolve(report)
}

describe('health view', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = '' })

  it('checks the first application and lists the findings by issue', async () => {
    const { wrapper } = await mountView(HealthView, {}, '/platform/health')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/applications/1/health')
    expect(wrapper.text()).toContain('发现 4 个问题')
    expect(wrapper.findAll('[data-issue]').map(item => item.attributes('data-issue')))
      .toEqual(['GRANT_ON_DISABLED', 'ACTION_WITHOUT_API', 'DEPENDENCY_ON_RETIRED_API'])
    const actions = wrapper.get('[data-issue="ACTION_WITHOUT_API"]')
    expect(actions.text()).toContain('没有关联 API 的按钮')
    expect(actions.text()).toContain('2 项')
    expect(wrapper.get('[data-issue="GRANT_ON_DISABLED"]').text()).toContain('租户 acme · 角色 auditors')
    expect(wrapper.get('[data-issue="DEPENDENCY_ON_RETIRED_API"]').text()).toContain('依赖 api:system.user.retired')

    await wrapper.findAll('button').find(button => button.text().includes('重新体检'))?.trigger('click')
    await flushPromises()
    expect(api.request.mock.calls.filter(([path]) => path === '/api/v1/applications/1/health')).toHaveLength(2)
    wrapper.unmount()
  })

  it('reports a healthy application and failures', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/applications'
      ? Promise.resolve([applications[1]]) : answer(path))
    const healthy = await mountView(HealthView, {}, '/platform/health')
    await flushPromises()
    expect(healthy.wrapper.text()).toContain('一切正常')
    healthy.wrapper.unmount()

    api.request.mockImplementation((path: string) => path === '/api/v1/applications'
      ? Promise.resolve(applications) : Promise.reject(new ApiError('你没有执行此操作的权限。', 403)))
    const refused = await mountView(HealthView, {}, '/platform/health')
    await flushPromises()
    expect(refused.wrapper.get('[role="alert"]').text()).toBe('你没有执行此操作的权限。')
    refused.wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = await mountView(HealthView, {}, '/platform/health')
    await flushPromises()
    expect(failed.wrapper.get('[role="alert"]').text()).toBe('无权访问')
    failed.wrapper.unmount()
  })
})
