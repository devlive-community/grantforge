// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { emptyComparison, emptyGroup, type ComparisonNode, type GroupNode } from '@/lib/dataCondition'
import ConditionBuilder from './ConditionBuilder.vue'
import { users, variables } from '../../tests/unit/dataEntities'

let latest: GroupNode
function render(group: GroupNode, errors: Record<string, string> = {}, depth = 1) {
  latest = group
  const wrapper: VueWrapper = mount(ConditionBuilder, { attachTo: document.body, props: { fields: users.fields, variables, errors, depth,
    modelValue: group, 'onUpdate:modelValue': (next: GroupNode) => { latest = next; return wrapper.setProps({ modelValue: next }) } } })
  return wrapper
}
async function pick(select: Element | undefined, option: string) {
  (select as HTMLElement).click()
  await flushPromises()
  Array.from(document.querySelectorAll<HTMLElement>('[role="option"]')).find(element => element.textContent?.trim() === option)?.click()
  await flushPromises()
}
const comparison = (index: number) => latest.children[index] as ComparisonNode

describe('condition builder', () => {
  afterEach(() => { document.body.innerHTML = '' })

  it('adds comparisons and groups and switches how they join', async () => {
    const wrapper = render(emptyGroup())
    expect(wrapper.text()).toContain('空条件组')
    await wrapper.findAll('button').find(button => button.text() === '添加条件')?.trigger('click')
    await flushPromises()
    expect(latest.children).toEqual([emptyComparison(users.fields[0])])
    await wrapper.findAll('button').find(button => button.text() === '添加条件组')?.trigger('click')
    await flushPromises()
    expect(latest.children[1]).toEqual(emptyGroup())
    await wrapper.get('[aria-pressed="false"]').trigger('click')
    expect(latest.join).toBe('or')
    await wrapper.get('input[type="checkbox"]').setValue(true)
    expect(latest.negate).toBe(true)
    // The nested group removes itself; comparisons are removed by their button.
    await wrapper.findAll('[aria-label="删除条件组"]')[0]?.trigger('click')
    await flushPromises()
    expect(latest.children).toHaveLength(1)
    await wrapper.get('[aria-label="删除条件 1"]').trigger('click')
    expect(latest.children).toEqual([])
  })

  it('offers what suits the field and keeps values as typed', async () => {
    const wrapper = render({ ...emptyGroup(), children: [emptyComparison(users.fields[0])] })
    const selects = () => document.querySelectorAll('[data-comparison] button[role="combobox"]')
    await pick(selects()[0], 'Age')
    expect(comparison(0)).toMatchObject({ field: 'age', op: 'eq' })
    await wrapper.get('[data-comparison] input[type="number"]').setValue('18')
    expect(comparison(0).value).toBe('18')
    await pick(selects()[1], '属于')
    expect(comparison(0)).toMatchObject({ op: 'in', value: '' })
    // Lists of numbers can come from the reader's departments.
    await pick(selects()[2], '当前用户的部门')
    expect(comparison(0).variable).toBe('subject.orgUnitIds')
    await pick(selects()[1], '等于')
    expect(comparison(0)).toMatchObject({ op: 'eq', variable: '' })
    await pick(selects()[1], '为空')
    expect(wrapper.text()).toContain('无需填写值')

    await pick(selects()[0], '状态')
    await pick(selects()[2], 'DISABLED')
    expect(comparison(0)).toMatchObject({ field: 'status', value: 'DISABLED' })
    await pick(selects()[1], '属于')
    const values = wrapper.get('[data-comparison] input[role="combobox"]')
    await values.setValue('ACTIVE,')
    expect(comparison(0).values).toEqual(['ACTIVE'])

    await pick(selects()[0], 'Admin')
    await pick(selects()[2], '是')
    expect(comparison(0).value).toBe('true')
    await pick(selects()[0], '最近登录')
    expect(wrapper.find('[data-comparison] input[type="datetime-local"]').exists()).toBe(true)
    await pick(selects()[0], '用户名')
    await pick(selects()[2], '当前用户名')
    expect(comparison(0).variable).toBe('subject.username')
  })

  it('shows the problems the server found where they are', () => {
    const group: GroupNode = { type: 'group', join: 'or', negate: true, children: [
      { ...emptyComparison(users.fields[0]), variable: 'subject.username' },
      { type: 'group', join: 'and', negate: false, children: [emptyComparison(users.fields[1])] },
    ] }
    const wrapper = render(group, { condition: '条件最多嵌套 5 层。', 'condition.not.or[0].value': '变量不适用',
      'condition.not.or[0]': '整条不对', 'condition.not.or[1].and[0].field': '不能使用' }, 4)
    expect(wrapper.get('[data-group="condition"] > [role="alert"]').text()).toBe('条件最多嵌套 5 层。')
    expect(wrapper.get('[data-comparison="condition.not.or[0]"]').text()).toContain('变量不适用')
    expect(wrapper.get('[data-comparison="condition.not.or[0]"]').text()).toContain('整条不对')
    expect(wrapper.get('[data-comparison="condition.not.or[1].and[0]"]').text()).toContain('不能使用')
    // At the deepest level no further group can be added.
    expect(wrapper.findAll('button').some(button => button.text() === '添加条件组')).toBe(false)
  })
})
