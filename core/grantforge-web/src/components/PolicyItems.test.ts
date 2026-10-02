// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { emptyItem, type ItemDraft, type PolicyKind } from '@/lib/policy'
import PolicyItems from './PolicyItems.vue'
import { warehouse } from '../../tests/unit/policies'

function render(kind: PolicyKind, items: ItemDraft[], errors: Record<string, string> = {}) {
  const suggest = vi.fn((_kind: string, text: string) => Promise.resolve([`${text}x`]))
  const wrapper = mount(PolicyItems, { attachTo: document.body, props: { list: 'allow', title: '允许', description: '这些人可以', kind,
    type: warehouse, accessTypes: warehouse.accessTypes, errors, suggest, modelValue: items,
    'onUpdate:modelValue': (next: ItemDraft[]) => wrapper.setProps({ modelValue: next }) } })
  return { wrapper, suggest }
}
const items = (wrapper: ReturnType<typeof render>['wrapper']) => wrapper.props('modelValue') as ItemDraft[]

describe('policy items', () => {
  afterEach(() => { vi.useRealTimers(); document.body.innerHTML = '' })

  it('edits whom items are about and what they allow', async () => {
    vi.useFakeTimers()
    const { wrapper, suggest } = render('ACCESS', [{ ...emptyItem(), users: ['alice'] }],
      { 'allow[0].users': '不存在：alice', 'allow[0].subjects': '请至少指定一个', 'allow[0].accessTypes': '必填。', allow: '最多 100 项。' })
    expect(wrapper.text()).toContain('不存在：alice')
    expect(wrapper.text()).toContain('请至少指定一个')
    expect(wrapper.get('[role="alert"]').text()).toBe('最多 100 项。')
    expect(wrapper.find('[data-item="allow[0]"]').text()).not.toContain('脱敏方式')

    const [users, groups, roles, addresses] = wrapper.findAll('input[role="combobox"]')
    await groups?.setValue('op')
    await vi.advanceTimersByTimeAsync(250)
    expect(suggest).toHaveBeenLastCalledWith('GROUP', 'op')
    await groups?.trigger('keydown', { key: 'Enter' })
    await roles?.setValue('analyst,')
    await users?.setValue('bob,')
    await addresses?.setValue('10.0.0.0/8,')
    await wrapper.findAll('input[type="checkbox"]')[1]?.setValue(true)
    expect(items(wrapper)[0]).toMatchObject({ users: ['alice', 'bob'], groups: ['op'], roles: ['analyst'], accessTypes: ['update'],
      conditions: { 'ip-range': ['10.0.0.0/8'] } })
    await wrapper.findAll('input[type="checkbox"]')[1]?.setValue(false)
    expect(items(wrapper)[0]?.accessTypes).toEqual([])

    await wrapper.findAll('button').find(button => button.text() === '添加条目')?.trigger('click')
    expect(items(wrapper)).toHaveLength(2)
    await wrapper.get('[aria-label="删除允许条目 1"]').trigger('click')
    expect(items(wrapper)).toEqual([emptyItem()])
    await wrapper.get('[aria-label="删除允许条目 1"]').trigger('click')
    expect(wrapper.text()).toContain('暂无条目')
  })

  it('asks masking items how to mask and filtering items for their filter', async () => {
    const masking = render('DATA_MASK', [emptyItem()], { 'allow[0].maskType': '请选择一种脱敏方式。' })
    expect(masking.wrapper.text()).toContain('请选择一种脱敏方式。')
    await masking.wrapper.get('button[role="combobox"]').trigger('click')
    await flushPromises()
    Array.from(document.querySelectorAll<HTMLElement>('[role="option"]')).find(option => option.textContent?.includes('Custom'))?.click()
    await flushPromises()
    expect(items(masking.wrapper)[0]?.maskType).toBe('custom')
    const expression = masking.wrapper.findAll('input').find(input => input.attributes('placeholder') === '{expr}')
    await expression?.setValue('left(ssn, 3)')
    expect(items(masking.wrapper)[0]?.maskValue).toBe('left(ssn, 3)')
    masking.wrapper.unmount()

    const filtering = render('ROW_FILTER', [emptyItem()])
    await filtering.wrapper.get('textarea').setValue("region = 'eu'")
    expect(items(filtering.wrapper)[0]?.rowFilter).toBe("region = 'eu'")
    expect(filtering.wrapper.text()).not.toContain('脱敏方式')
  })
})
