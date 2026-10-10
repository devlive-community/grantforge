// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'
import { salesPolicy, warehouse } from '../../tests/unit/policies'

/** A validation tip floats out of its control, so an element's words are read from the document as well. */
function textOf(root: Element | null | undefined) {
  const tips = Array.from(root?.querySelectorAll('[aria-describedby]') ?? [])
    .map(control => document.getElementById(control.getAttribute('aria-describedby') || '')?.textContent ?? '')
  return [root?.textContent ?? '', ...tips].join('')
}
const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: PoliciesView } = await import('./PoliciesView.vue')

const dw = { id: '7', name: 'dw', label: 'Data warehouse', serviceType: 'warehouse', serviceTypeLabel: 'Warehouse', enabled: true,
  available: true, values: {}, secretsSet: [] }
const lake = { ...dw, id: '8', name: 'lake', label: 'Lake', serviceType: 'hdfs', serviceTypeLabel: undefined, available: false }
type Call = [string, { method?: string, body?: Record<string, unknown> }?]
const calls = (method: string) => (api.request.mock.calls as Call[]).filter(([, options]) => options?.method === method)

function respond(policies: unknown[] = [salesPolicy]) {
  api.request.mockImplementation((path: string, options?: { method?: string }) => {
    if (options?.method === 'DELETE') return Promise.resolve(null)
    if (options?.method) return Promise.resolve(salesPolicy)
    if (path === '/api/v1/services') return Promise.resolve([dw, lake])
    if (path === '/api/v1/service-types') return Promise.resolve([warehouse])
    return Promise.resolve(path.includes('type=ACCESS') ? policies : [])
  })
}
function button(text: string) {
  const found = Array.from(document.querySelectorAll('button')).find(element => element.textContent?.trim() === text)
  if (!found) throw new Error(`no button ${text}`)
  return found
}

describe('policies view', () => {
  beforeEach(() => { api.request.mockReset(); respond() })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists the policies of a service by kind', async () => {
    const { wrapper } = await mountView(PoliciesView, {}, '/data/policies?service=7')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/services/7/policies?type=ACCESS')
    const row = wrapper.get('[data-policy="sales"]')
    expect(row.text()).toContain('Database: sales / Table: orders')
    expect(row.text()).toContain('允许 1 项 · 拒绝 1 项')
    expect(row.text()).toContain('1 个有效期')
    expect(row.text()).toContain('优先')
    expect(row.text()).toContain('pii')
    expect(wrapper.findAll('[aria-pressed]').map(kind => kind.text())).toEqual(['访问', '脱敏', '行过滤'])

    await wrapper.findAll('[aria-pressed]')[1]?.trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/services/7/policies?type=DATA_MASK')
    expect(wrapper.text()).toContain('这个服务还没有此类策略')
    wrapper.unmount()
  })

  it('adds a policy from the generic editor and shows the problems the server finds', async () => {
    const { wrapper } = await mountView(PoliciesView, {}, '/data/policies')
    await flushPromises()
    button('添加策略').click()
    await flushPromises()
    expect(wrapper.text()).toContain('添加访问策略')
    await wrapper.get('form input').setValue('sales')
    await wrapper.get('[data-level="database"] input[role="combobox"]').setValue('sales,')
    await wrapper.get('[data-items="allow"] input[role="combobox"]').setValue('alice,')
    await wrapper.get('[data-items="allow"] input[type="checkbox"]').setValue(true)

    api.request.mockImplementationOnce(() => Promise.reject(new ApiError('策略的部分内容不正确。', 400, 0, null,
      { title: 'invalid', status: 400, errors: [{ field: 'allow[0].users', message: '不存在：alice' }] })))
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const [path, options] = calls('POST')[0] ?? []
    expect(path).toBe('/api/v1/services/7/policies')
    expect(options?.body).toMatchObject({ type: 'ACCESS', name: 'sales', priority: 'NORMAL', enabled: true,
      document: { resources: { database: { values: ['sales'], excludes: false, recursive: false } },
        allow: [{ users: ['alice'], accessTypes: ['select'] }] } })
    expect(textOf(wrapper.get('[data-items="allow"]').element)).toContain('不存在：alice')
    expect(wrapper.get('form > [role="alert"]').text()).toBe('策略的部分内容不正确。')

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(useToast().items.map(item => item.message)).toContain('策略已添加')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })

  it('edits a policy on the version it was read at and explains concurrent changes', async () => {
    const { wrapper } = await mountView(PoliciesView, {}, '/data/policies?service=7')
    await flushPromises()
    await wrapper.get('[aria-label="编辑 sales"]').trigger('click')
    expect(wrapper.text()).toContain('编辑策略“sales”')
    api.request.mockImplementationOnce(() => Promise.reject(new ApiError('冲突', 409)))
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const [path, options] = calls('PUT')[0] ?? []
    expect(path).toBe('/api/v1/policies/11')
    expect(options?.body).toMatchObject({ version: 3, priority: 'OVERRIDE', labels: ['pii'] })
    expect(wrapper.get('form > [role="alert"]').text()).toBe('这条策略在你编辑期间被他人修改了。请刷新后重新编辑。')
    button('取消').click()
    await flushPromises()
    expect(wrapper.find('form').exists()).toBe(false)

    await wrapper.get('[aria-label="编辑 sales"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(useToast().items.map(item => item.message)).toContain('策略已保存')
    wrapper.unmount()
  })

  it('deletes policies after confirmation', async () => {
    const { wrapper } = await mountView(PoliciesView, {}, '/data/policies?service=7')
    await flushPromises()
    await wrapper.get('[aria-label="删除 sales"]').trigger('click')
    expect(document.body.textContent).toContain('删除策略“sales”？')
    button('删除').click()
    await flushPromises()
    expect(calls('DELETE')[0]?.[0]).toBe('/api/v1/policies/11')
    expect(useToast().items.map(item => item.message)).toContain('策略已删除')

    api.request.mockRejectedValueOnce(new ApiError('无权删除', 403))
    await wrapper.get('[aria-label="删除 sales"]').trigger('click')
    button('删除').click()
    await flushPromises()
    expect(useToast().items.map(item => item.message)).toContain('无权删除')
    wrapper.unmount()
  })

  it('switches services and explains what is missing', async () => {
    const { wrapper, router } = await mountView(PoliciesView, {}, '/data/policies?service=8')
    await flushPromises()
    expect(wrapper.get('[role="note"]').text()).toBe('服务类型 hdfs 的插件未运行，暂时无法编辑这个服务的策略。')
    await router.replace('/data/policies?service=7')
    await flushPromises()
    expect(wrapper.find('[data-policy="sales"]').exists()).toBe(true)
    wrapper.unmount()

    respond([])
    const empty = await mountView(PoliciesView, {}, '/data/policies')
    await flushPromises()
    expect(empty.wrapper.text()).toContain('没有任何策略允许时，访问会被拒绝。')
    empty.wrapper.unmount()

    api.request.mockResolvedValue([])
    const none = await mountView(PoliciesView, {}, '/data/policies')
    await flushPromises()
    expect(none.wrapper.text()).toContain('还没有数据服务')
    none.wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = await mountView(PoliciesView, {}, '/data/policies')
    await flushPromises()
    expect(failed.wrapper.get('[role="alert"]').text()).toBe('无权访问')
    failed.wrapper.unmount()
  })
})
