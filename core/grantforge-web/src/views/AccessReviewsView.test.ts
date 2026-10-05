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

const { default: AccessReviewsView } = await import('./AccessReviewsView.vue')

const role = (id: string, name: string, type = 'CUSTOM') => ({ id, code: name.toLowerCase(), name, type, enabled: true })
const ledger = role('1', 'Ledger'), payer = role('2', 'Payer')
const progress = { total: 2, pending: 1, keep: 1, revoke: 0, revoked: 0 }
const open = { id: '30', reviewId: '9', status: 'OPEN', startedAt: '2026-10-01T00:00:00Z', dueAt: '2026-10-15T00:00:00Z', progress }
const done = { id: '29', reviewId: '9', status: 'COMPLETED', startedAt: '2026-07-01T00:00:00Z', dueAt: '2026-07-15T00:00:00Z', endedAt: '2026-07-15T00:00:00Z',
  progress: { total: 1, pending: 0, keep: 0, revoke: 1, revoked: 1 } }
const quarterly = { id: '9', name: '季度复核', description: '财务角色', roles: [ledger], durationDays: 14, intervalDays: 90, unreviewed: 'REVOKE', enabled: true,
  nextRunAt: '2026-12-30T00:00:00Z', openRound: open }
const annual = { id: '8', name: '年度复核', roles: [payer], durationDays: 30, unreviewed: 'KEEP', enabled: true }
const item = (id: string, subjectName: string, decision: string, extra: object = {}) => ({ id, role: ledger, subjectType: 'USER', subjectId: id,
  subjectName, assigned: true, includeSubUnits: false, decision, ...extra })
const rory = item('51', 'Rory', 'PENDING', { subjectDetail: 'rory' })
const team = item('52', 'Developers', 'KEEP', { subjectType: 'GROUP', decidedByName: 'Admin', decidedAt: '2026-10-02T00:00:00Z', comment: '仍需要' })
const toasts = () => useToast().items.map(entry => entry.message)
const dialog = () => document.querySelector('dialog[open]') as HTMLDialogElement
function field(label: string) {
  const found = [...dialog().querySelectorAll('label')].find(entry => entry.textContent?.replace('*', '').trim() === label)
  return dialog().querySelector<HTMLInputElement>(`[id="${found?.getAttribute('for') ?? 'missing'}"]`) as HTMLInputElement
}
function fill(label: string, value: string) { const input = field(label); input.value = value; input.dispatchEvent(new Event('input')) }
function check(name: string) {
  const box = [...dialog().querySelectorAll<HTMLInputElement>('[data-role-list] input[type="checkbox"]')].find(entry => entry.getAttribute('aria-label') === name)
  box?.click()
}
const submit = async () => { dialog().querySelector('form')?.dispatchEvent(new Event('submit')); await flushPromises() }
const alertText = () => dialog().querySelector('[role="alert"]')?.textContent
const press = async (name: string) => { [...dialog().querySelectorAll<HTMLButtonElement>('button')].find(button => button.textContent?.trim() === name)?.click(); await flushPromises() }

describe('access reviews view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (path === '/api/v1/access-reviews' && !options?.method) return Promise.resolve([quarterly, annual])
      if (path === '/api/v1/access-reviews/9/rounds') return Promise.resolve([open, done])
      if (path === '/api/v1/access-reviews/8/rounds') return Promise.resolve([])
      if (path.startsWith('/api/v1/access-review-rounds/') && path.endsWith('/items')) return Promise.resolve({ items: [rory, team], page: 1, size: 20, total: 2 })
      if (path === '/api/v1/roles') return Promise.resolve([ledger, payer])
      if (path === '/api/v1/access-reviews' || path.startsWith('/api/v1/access-reviews/9')) return Promise.resolve(quarterly)
      if (path.endsWith('/start')) return Promise.resolve({ ...open, id: '31' })
      return Promise.resolve([])
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows the reviews and the open round of the first', async () => {
    const { wrapper } = await mountView(AccessReviewsView, {}, '/admin/access-reviews')
    await flushPromises()
    const review = wrapper.get('[data-review="季度复核"]')
    expect(review.text()).toContain('每轮 14 天，每 90 天一轮')
    expect(review.text()).toContain('未处理的分配：按撤销处理')
    expect(review.text()).toContain('进行中：已处理 1/2')
    expect(wrapper.get('[data-review="年度复核"]').text()).toContain('每轮 30 天，手动开始')
    expect(wrapper.get('[data-round]').text()).toContain('共 2 项，待处理 1，保留 1，撤销 0')
    expect(wrapper.get('[data-item="Rory"]').text()).toContain('待处理')
    expect(wrapper.get('[data-item="Developers"]').text()).toContain('用户组')
    expect(wrapper.get('[data-item="Developers"]').text()).toContain('Admin')
    expect(wrapper.get('[data-item="Developers"]').text()).toContain('仍需要')
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-review-rounds/30/items', { query: { decision: undefined, page: 1, size: 20 } })

    // A finished round is read only.
    // The selects are the round, the decision filter and the page size.
    await wrapper.findAll('[role="combobox"]')[0]?.trigger('click')
    await flushPromises()
    ;(document.querySelectorAll<HTMLElement>('[role="option"]')[1] as HTMLElement).click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-review-rounds/29/items', { query: { decision: undefined, page: 1, size: 20 } })
    expect(wrapper.get('[data-round]').text()).toContain('移除 1 项')
    expect(wrapper.find('[aria-label="保留 Rory 的 Ledger"]').exists()).toBe(false)

    // Another review without rounds.
    await wrapper.get('[aria-label="查看 年度复核"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('还没有开始过复核')
    wrapper.unmount()
  })

  it('keeps, revokes and takes decisions back', async () => {
    const { wrapper } = await mountView(AccessReviewsView, {}, '/admin/access-reviews')
    await flushPromises()
    await wrapper.get('[aria-label="保留 Rory 的 Ledger"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-review-rounds/30/decisions', { method: 'POST',
      body: { itemIds: ['51'], decision: 'KEEP', comment: undefined } })
    expect(toasts()).toContain('决定已记录')

    await wrapper.get('[aria-label="撤回对 Developers 的 Ledger 的决定"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-review-rounds/30/decisions', { method: 'POST',
      body: { itemIds: ['52'], decision: 'PENDING', comment: undefined } })

    await wrapper.get('[aria-label="撤销 Rory 的 Ledger"]').trigger('click')
    await flushPromises()
    expect(dialog().textContent).toContain('本轮完成时将移除这 1 项分配')
    fill('说明', '已调岗')
    api.request.mockRejectedValueOnce(new ApiError('不能复核自己的权限', 403))
    await submit()
    expect(alertText()).toBe('不能复核自己的权限')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-review-rounds/30/decisions', { method: 'POST',
      body: { itemIds: ['51'], decision: 'REVOKE', comment: '已调岗' } })
    wrapper.unmount()
  })

  it('decides about the selected assignments together', async () => {
    const { wrapper } = await mountView(AccessReviewsView, {}, '/admin/access-reviews')
    await flushPromises()
    const keepSelected = () => wrapper.findAll('button').find(button => button.text() === '保留所选')
    expect(keepSelected()?.attributes('disabled')).toBeDefined()
    await wrapper.get('input[aria-label="选择本页全部"]').setValue(true)
    expect(wrapper.text()).toContain('已选 2 项')
    await wrapper.get('input[aria-label="选择 Rory 的 Ledger"]').setValue(false)
    expect(wrapper.text()).toContain('已选 1 项')
    await keepSelected()?.trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-review-rounds/30/decisions', { method: 'POST',
      body: { itemIds: ['52'], decision: 'KEEP', comment: undefined } })

    await wrapper.get('input[aria-label="选择 Rory 的 Ledger"]').setValue(true)
    await wrapper.findAll('button').find(button => button.text() === '撤销所选')?.trigger('click')
    await flushPromises()
    await press('撤销')
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-review-rounds/30/decisions', { method: 'POST',
      body: { itemIds: ['51'], decision: 'REVOKE', comment: undefined } })

    await wrapper.findAll('[role="combobox"]')[1]?.trigger('click')
    await flushPromises()
    ;[...document.querySelectorAll<HTMLElement>('[role="option"]')].find(option => option.textContent?.includes('待处理'))?.click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-review-rounds/30/items', { query: { decision: 'PENDING', page: 1, size: 20 } })
    wrapper.unmount()
  })

  it('starts, completes and cancels rounds', async () => {
    const { wrapper } = await mountView(AccessReviewsView, {}, '/admin/access-reviews')
    await flushPromises()
    expect(wrapper.find('[aria-label="立即开始 季度复核"]').exists()).toBe(false)
    await wrapper.get('[aria-label="立即开始 年度复核"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-reviews/8/start', { method: 'POST' })
    expect(toasts()).toContain('本轮已开始')

    await wrapper.get('[aria-label="查看 季度复核"]').trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '完成本轮')?.trigger('click')
    await flushPromises()
    expect(dialog().textContent).toContain('将移除 0 项被撤销的分配；1 项未处理的分配将按撤销处理')
    await press('完成本轮')
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-review-rounds/30/complete', { method: 'POST' })
    expect(toasts()).toContain('本轮已完成')

    await wrapper.findAll('button').find(button => button.text() === '取消本轮')?.trigger('click')
    await flushPromises()
    api.request.mockRejectedValueOnce(new ApiError('该轮复核已结束', 409))
    await press('取消本轮')
    expect(toasts()).toContain('该轮复核已结束')
    wrapper.unmount()
  })

  it('adds, edits and deletes reviews after checking the form', async () => {
    const { wrapper } = await mountView(AccessReviewsView, {}, '/admin/access-reviews')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '添加复核')?.trigger('click')
    await flushPromises()
    await submit()
    expect(alertText()).toBe('请输入名称')
    fill('名称', '月度复核')
    await submit()
    expect(alertText()).toBe('请至少选择一个角色')
    const filter = dialog().querySelector<HTMLInputElement>('[aria-label="筛选角色"]') as HTMLInputElement
    filter.value = 'pay'; filter.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(dialog().querySelector('[data-role-list]')?.textContent).not.toContain('Ledger')
    check('Payer')
    await flushPromises()
    fill('每轮天数', '0')
    await submit()
    expect(alertText()).toBe('每轮天数须为 1 到 90')
    fill('每轮天数', '7')
    fill('间隔天数（留空则不重复）', '3')
    await submit()
    expect(alertText()).toBe('间隔天数须为 7 到 366')
    fill('间隔天数（留空则不重复）', '30')
    fill('下次开始日期（留空则只能手动开始）', '2026-11-01')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-reviews', { method: 'POST', body: { name: '月度复核', description: undefined,
      roleIds: ['2'], durationDays: 7, intervalDays: 30, unreviewed: 'KEEP', enabled: true, nextRunAt: new Date('2026-11-01T00:00:00').toISOString() } })
    expect(toasts()).toContain('复核已添加')

    await wrapper.get('[aria-label="编辑 季度复核"]').trigger('click')
    await flushPromises()
    expect(field('名称').value).toBe('季度复核')
    expect(field('间隔天数（留空则不重复）').value).toBe('90')
    fill('间隔天数（留空则不重复）', '')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-reviews/9', { method: 'PUT', body: expect.objectContaining({
      description: '财务角色', roleIds: ['1'], durationDays: 14, intervalDays: undefined, unreviewed: 'REVOKE' }) })
    expect(toasts()).toContain('复核已保存')

    await wrapper.get('[aria-label="删除 季度复核"]').trigger('click')
    await flushPromises()
    expect(dialog().textContent).toContain('确定删除 季度复核')
    await press('删除')
    expect(api.request).toHaveBeenCalledWith('/api/v1/access-reviews/9', { method: 'DELETE' })
    expect(toasts()).toContain('复核已删除')
    wrapper.unmount()
  })

  it('says when there is nothing or it failed to load', async () => {
    api.request.mockImplementation(() => Promise.resolve([]))
    const empty = await mountView(AccessReviewsView, {}, '/admin/access-reviews')
    await flushPromises()
    expect(empty.wrapper.text()).toContain('还没有复核')
    empty.wrapper.unmount()
    api.request.mockRejectedValue(new ApiError('服务暂时不可用。', 503))
    const failed = await mountView(AccessReviewsView, {}, '/admin/access-reviews')
    await flushPromises()
    expect(failed.wrapper.get('[role="alert"]').text()).toBe('服务暂时不可用。')
    failed.wrapper.unmount()
  })
})
