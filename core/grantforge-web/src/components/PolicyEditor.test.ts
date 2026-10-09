// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { draftOf, emptyDraft, type PolicyDraft, type PolicyKind } from '@/lib/policy'
import { salesPolicy, warehouse } from '../../tests/unit/policies'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: PolicyEditor } = await import('./PolicyEditor.vue')

let latest: PolicyDraft
function render(draft: PolicyDraft, kind: PolicyKind = 'ACCESS', errors: Record<string, string> = {}) {
  latest = draft
  const wrapper: VueWrapper = mount(PolicyEditor, { attachTo: document.body, props: { type: warehouse, kind, serviceId: '7', errors,
    modelValue: draft, 'onUpdate:modelValue': (next: PolicyDraft) => { latest = next; return wrapper.setProps({ modelValue: next }) } } })
  return wrapper
}
/** The draft as the editor last reported it. */
const draftIn = () => latest
async function pick(wrapper: VueWrapper, label: string, option: string) {
  const field = wrapper.findAll('div').find(element => element.find('label, span').exists()
    && element.find('button[role="combobox"]').exists() && element.text().startsWith(label))
  await field?.get('button[role="combobox"]').trigger('click')
  await flushPromises()
  Array.from(document.querySelectorAll<HTMLElement>('[role="option"]')).find(element => element.textContent?.includes(option))?.click()
  await flushPromises()
}

describe('policy editor', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string) => Promise.resolve(path.includes('lookup') ? ['sales', 'support'] : ['alice']))
  })
  afterEach(() => { vi.useRealTimers(); document.body.innerHTML = '' })

  it('shows a stored policy level by level with its items and periods', () => {
    const wrapper = render(draftOf(salesPolicy, warehouse), 'ACCESS', { 'resources.table': '必填。', 'validity[0]': '结束时间必须晚于开始时间。',
      name: '必填。', resources: '请从最上层开始逐层选择资源。' })
    expect(wrapper.findAll('[data-level]').map(level => level.attributes('data-level'))).toEqual(['database', 'table'])
    expect(wrapper.get('[data-level="table"]').text()).toContain('必填。')
    expect(wrapper.get('[data-level="database"]').text()).not.toContain('排除这些值')
    expect(wrapper.get('[data-level="table"]').text()).toContain('排除这些值')
    expect(wrapper.findAll('[data-items]').map(list => list.attributes('data-items'))).toEqual(['allow', 'allowExceptions', 'deny', 'denyExceptions'])
    expect(wrapper.get('[data-period="0"]').text()).toContain('结束时间必须晚于开始时间。')
    expect(wrapper.text()).toContain('请从最上层开始逐层选择资源。')
    expect(wrapper.text()).toContain('继续细化到（可选）')
  })

  it('says why a lookup failed and looks again on retry', async () => {
    vi.useFakeTimers()
    api.request.mockRejectedValueOnce(new ApiError('无法连接目标系统：the example warehouse is offline', 502))
    const wrapper = render(draftOf(salesPolicy, warehouse))
    const database = wrapper.get('[data-level="database"]')
    await database.get('input[role="combobox"]').setValue('s')
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    expect(database.get('[role="status"]').text()).toContain('查找失败：无法连接目标系统：the example warehouse is offline')
    await database.get('[role="status"] button').trigger('mousedown')
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    expect(database.find('[role="status"]').exists()).toBe(false)
    // The chosen value is not offered again.
    expect(database.findAll('[role="option"]').map(option => option.text())).toEqual(['support'])
  })

  it('looks up values from the service and the levels above', async () => {
    vi.useFakeTimers()
    const wrapper = render(draftOf(salesPolicy, warehouse))
    const database = wrapper.get('[data-level="database"] input[role="combobox"]')
    await database.setValue('s')
    await vi.advanceTimersByTimeAsync(250)
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/services/7/lookup', { method: 'POST',
      body: { resource: 'database', userInput: 's', context: {}, limit: 20 }, signal: expect.any(AbortSignal) })
    // Tables offer no lookup: typing there asks nothing.
    api.request.mockClear()
    await wrapper.get('[data-level="table"] input[role="combobox"]').setValue('o')
    await vi.advanceTimersByTimeAsync(250)
    expect(api.request).not.toHaveBeenCalled()
    await wrapper.get('[data-items="allow"] input[role="combobox"]').setValue('al')
    await vi.advanceTimersByTimeAsync(250)
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/policy-subjects?kind=USER&text=al&limit=20')
  })

  it('grows and cuts the chain of levels', async () => {
    const wrapper = render(draftOf(salesPolicy, warehouse))
    await pick(wrapper, '继续细化到', 'Column')
    expect(draftIn().levels.map(level => level.name)).toEqual(['database', 'table', 'column'])
    // Columns only know select.
    expect(wrapper.get('[data-items="allow"]').text()).not.toContain('Update')
    await wrapper.get('[data-level="column"] [role="switch"]').trigger('click')
    await flushPromises()
    expect(draftIn().levels[2]?.excludes).toBe(true)
    await pick(wrapper, '资源层级', 'Path')
    expect(draftIn().levels.map(level => level.name)).toEqual(['path'])
    // Paths can both exclude values and cover what is below them.
    await wrapper.findAll('[data-level="path"] [role="switch"]')[1]?.trigger('click')
    await flushPromises()
    expect(draftIn().levels[0]?.recursive).toBe(true)
    await wrapper.get('[data-level="path"] input[role="combobox"]').setValue('/data,')
    expect(draftIn().levels[0]?.values).toEqual(['/data'])
  })

  it('says where masking and filtering policies may end and which items they have', async () => {
    const masking = render(emptyDraft(warehouse), 'DATA_MASK')
    expect(masking.get('[role="note"]').text()).toBe('这类策略只能止于：Column')
    expect(masking.findAll('[data-items]').map(list => list.attributes('data-items'))).toEqual(['allow'])
    expect(masking.text()).toContain('脱敏条目')
    masking.unmount()
    const filtering = render(emptyDraft(warehouse), 'ROW_FILTER')
    expect(filtering.text()).toContain('行过滤条目')
    expect(filtering.get('[role="note"]').text()).toBe('这类策略只能止于：Table')
    filtering.unmount()
    const strict = mount(PolicyEditor, { attachTo: document.body, props: { type: { ...warehouse, resources: warehouse.resources
      .map(level => ({ ...level, validLeaf: false })) }, kind: 'ACCESS', serviceId: '7', errors: {}, modelValue: emptyDraft(warehouse) } })
    expect(strict.text()).toContain('继续选择下一层（必选）')
  })

  it('edits the description, switches and periods', async () => {
    const wrapper = render(emptyDraft(warehouse))
    await wrapper.get('input').setValue('sales')
    await wrapper.get('textarea').setValue('team')
    expect(draftIn()).toMatchObject({ name: 'sales', description: 'team' })
    await wrapper.findAll('button').find(button => button.text() === '添加有效期')?.trigger('click')
    const [from, until] = wrapper.findAll('[data-period="0"] input')
    await from?.setValue('2026-01-01T08:00')
    await until?.setValue('2026-02-01T08:00')
    expect(draftIn().validity).toEqual([{ from: '2026-01-01T08:00', until: '2026-02-01T08:00' }])
    await wrapper.findAll('button').find(button => button.text() === '添加条目')?.trigger('click')
    expect(draftIn().allow).toHaveLength(2)
    await wrapper.get('[aria-label="删除有效期 1"]').trigger('click')
    expect(draftIn().validity).toEqual([])
  })
})
