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

const { default: ApisView } = await import('./ApisView.vue')

const seen = '2026-10-01T08:00:00Z'
const endpoints = [
  { id: '1', httpMethod: 'GET', pathPattern: '/api/v1/bootstrap', handler: 'SetupController#bootstrap', access: 'PUBLIC', active: true, lastSeenAt: seen },
  { id: '2', httpMethod: 'GET', pathPattern: '/api/v1/me', handler: 'MeController#me', access: 'AUTHENTICATED', active: true, change: 'CHANGED', changedAt: seen, lastSeenAt: seen },
  { id: '3', httpMethod: 'GET', pathPattern: '/api/v1/users', handler: 'UserController#list', access: 'PERMISSION', permission: 'system.user.read', resourceId: '9', active: true, change: 'ADDED', changedAt: seen, lastSeenAt: seen },
  { id: '4', httpMethod: 'GET', pathPattern: '/api/v1/users/{id}', handler: 'UserController#find', access: 'PERMISSION', permission: 'system.user.read', resourceId: '9', active: true, lastSeenAt: seen },
  { id: '5', httpMethod: 'PATCH', pathPattern: '/api/v1/old', handler: 'OldController#old', access: 'PERMISSION', permission: 'system.old.write', resourceId: '8', active: false, change: 'REMOVED', changedAt: seen, lastSeenAt: seen },
]

async function mountApis(platform = true) {
  const mounted = await mountView(ApisView, {}, '/platform/apis')
  useAuth().authorization = authorization(platform ? everything() : [])
  await flushPromises()
  return mounted
}
type Wrapper = Awaited<ReturnType<typeof mountApis>>['wrapper']
const routes = (wrapper: Wrapper) => wrapper.findAll('tbody tr').map(row => row.find('td').text())
/** Picks an option of the access (first) or state (second) filter. */
async function choose(wrapper: Wrapper, label: '访问要求' | '状态', option: string) {
  await wrapper.findAll('[role="combobox"]')[label === '访问要求' ? 0 : 1]?.trigger('click')
  await flushPromises()
  ;[...document.querySelectorAll<HTMLElement>('[role="option"]')].find(item => item.textContent?.trim() === option)?.click()
  await flushPromises()
}
function dialogButton(label: string) {
  const found = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!found) throw new Error('missing dialog button ' + label)
  return found as HTMLButtonElement
}

describe('API catalog view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((_path: string, options?: { method?: string }) => Promise.resolve(options?.method ? { reviewed: 3 } : endpoints))
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows the endpoints in use with their access, permission and changes', async () => {
    const { wrapper } = await mountApis()
    expect(routes(wrapper)).toEqual(['GET/api/v1/bootstrap', 'GET/api/v1/me', 'GET/api/v1/users', 'GET/api/v1/users/{id}'])
    expect(wrapper.text()).toContain('system.user.read')
    expect(wrapper.text()).toContain('登录即可')
    expect(wrapper.text()).toContain('新增')
    expect(wrapper.text()).toContain('已变更')
    // In use, permissions and changes to confirm.
    expect(wrapper.findAll('.panel p.text-\\[26px\\]').map(item => item.text())).toEqual(['4', '1', '3'])
    wrapper.unmount()
  })

  it('filters by text, access and state', async () => {
    const { wrapper } = await mountApis()
    await wrapper.get('input').setValue('USERCONTROLLER#FIND')
    expect(routes(wrapper)).toEqual(['GET/api/v1/users/{id}'])
    await wrapper.get('input').setValue('')
    await choose(wrapper, '访问要求', '公开')
    expect(routes(wrapper)).toEqual(['GET/api/v1/bootstrap'])
    await choose(wrapper, '访问要求', '全部')
    await choose(wrapper, '状态', '已下线')
    expect(routes(wrapper)).toEqual(['PATCH/api/v1/old'])
    expect(wrapper.text()).toContain('已下线')
    await choose(wrapper, '状态', '待确认')
    expect(routes(wrapper)).toEqual(['GET/api/v1/me', 'GET/api/v1/users', 'PATCH/api/v1/old'])
    await choose(wrapper, '状态', '全部')
    expect(routes(wrapper)).toHaveLength(5)
    await wrapper.get('input').setValue('nothing')
    expect(wrapper.text()).toContain('没有符合条件的接口')
    wrapper.unmount()
  })

  it('confirms the changes shown', async () => {
    const { wrapper } = await mountApis()
    await choose(wrapper, '状态', '全部')
    const review = wrapper.findAll('button').find(item => item.text().includes('确认变更'))
    expect(review?.text()).toContain('3')
    await review?.trigger('click')
    await flushPromises()
    dialogButton('确认').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/api-endpoints/review', { method: 'POST', body: { endpointIds: ['2', '3', '5'] } })
    expect(useToast().items.map(item => item.message)).toContain('已确认 3 项变更')

    api.request.mockImplementation((_path: string, options?: { method?: string }) => options?.method
      ? Promise.reject(new ApiError('没有权限', 403)) : Promise.resolve(endpoints))
    await review?.trigger('click')
    await flushPromises()
    dialogButton('确认').click()
    await flushPromises()
    expect(useToast().items.map(item => item.message)).toContain('没有权限')
    wrapper.unmount()
  })

  it('is read-only for tenant administrators and reports load failures', async () => {
    const { wrapper } = await mountApis(false)
    expect(wrapper.findAll('button').some(item => item.isVisible() && item.text().includes('确认变更'))).toBe(false)
    wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = await mountApis()
    expect(failed.wrapper.get('[role="alert"]').text()).toContain('无权访问')
    failed.wrapper.unmount()
  })
})
