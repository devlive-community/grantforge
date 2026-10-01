// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { i18n } from '@/i18n'
import UiTree, { type TreeNode } from './UiTree.vue'

const nodes: TreeNode[] = [
  { id: 'hq', label: '总部', hint: 'hq', children: [
    { id: 'sales', label: '销售', children: [{ id: 'east', label: '华东', children: [] }] },
    { id: 'rnd', label: '研发', children: [] },
  ] },
  { id: 'lab', label: '实验室', children: [] },
]

function mountTree(selected: string | null = null) {
  return mount(UiTree, { props: { nodes, label: '组织', selected }, attachTo: document.body, global: { plugins: [i18n] } })
}
const labels = (wrapper: ReturnType<typeof mountTree>) => wrapper.findAll('[role="treeitem"]').map(item => item.text())
const active = () => (document.activeElement as HTMLElement | null)?.dataset.id

describe('tree', () => {
  afterEach(() => { document.body.innerHTML = '' })

  it('renders levels, hints and one tab stop', () => {
    const wrapper = mountTree()
    const items = wrapper.findAll('[role="treeitem"]')
    expect(labels(wrapper)).toEqual(['总部hq', '销售', '华东', '研发', '实验室'])
    expect(items.map(item => item.attributes('aria-level'))).toEqual(['1', '2', '3', '2', '1'])
    expect(items.map(item => item.attributes('tabindex'))).toEqual(['0', '-1', '-1', '-1', '-1'])
    expect(items[0]?.attributes('aria-expanded')).toBe('true')
    expect(items[4]?.attributes('aria-expanded')).toBeUndefined()
    wrapper.unmount()
  })

  it('moves focus, expands and collapses with the keyboard and selects with Enter', async () => {
    const wrapper = mountTree()
    const key = async (name: string) => {
      document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: name, bubbles: true }))
      await flushPromises()
    }
    ;(wrapper.get('[data-id="hq"]').element as HTMLElement).focus()
    await key('ArrowDown')
    expect(active()).toBe('sales')
    await key('ArrowRight')
    expect(active()).toBe('east')
    await key('ArrowLeft')
    expect(active()).toBe('sales')
    await key('ArrowLeft')
    expect(wrapper.get('[data-id="sales"]').attributes('aria-expanded')).toBe('false')
    expect(labels(wrapper)).not.toContain('华东')
    await key('ArrowRight')
    expect(labels(wrapper)).toContain('华东')
    await key('End')
    expect(active()).toBe('lab')
    await key('Home')
    expect(active()).toBe('hq')
    await key('ArrowUp')
    expect(active()).toBe('hq')
    await key('Enter')
    expect(wrapper.emitted('update:selected')?.at(-1)).toEqual(['hq'])
    await key(' ')
    await key('x')
    await key('ArrowRight')
    expect(active()).toBe('sales')
    wrapper.unmount()
  })

  it('selects on click and toggles with the chevron', async () => {
    const wrapper = mountTree()
    await wrapper.get('[data-id="rnd"]').trigger('click')
    expect(wrapper.emitted('update:selected')?.at(-1)).toEqual(['rnd'])
    await wrapper.get('[aria-label="收起 总部"]').trigger('click')
    expect(labels(wrapper)).toEqual(['总部hq', '实验室'])
    await wrapper.get('[aria-label="展开 总部"]').trigger('click')
    expect(labels(wrapper)).toHaveLength(5)
    // A leaf has nothing to expand.
    ;(wrapper.get('[data-id="lab"]').element as HTMLElement).focus()
    document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await flushPromises()
    expect(active()).toBe('lab')
    wrapper.unmount()
  })

  it('reveals a selection made from outside', async () => {
    const wrapper = mountTree()
    await wrapper.get('[aria-label="收起 总部"]').trigger('click')
    await wrapper.setProps({ selected: 'east' })
    expect(labels(wrapper)).toContain('华东')
    expect(wrapper.get('[data-id="east"]').attributes('tabindex')).toBe('0')
    await wrapper.setProps({ selected: 'nowhere' })
    expect(wrapper.get('[data-id="hq"]').attributes('tabindex')).toBe('0')
    wrapper.unmount()
  })
})
