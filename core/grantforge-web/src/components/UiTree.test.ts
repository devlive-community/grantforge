// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount, type DOMWrapper } from '@vue/test-utils'
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

  it('shows badges and reports drops before, after or inside a node', async () => {
    const drops: unknown[] = []
    const wrapper = mount(UiTree, {
      props: { nodes: [{ id: 'a', label: 'A', badge: '页面', children: [] }, { id: 'b', label: 'B', children: [] }, { id: 'c', label: 'C', children: [] }],
        label: '资源', draggable: true, canDrop: (_source: string, target: string) => target !== 'c', onDrop: (...args: unknown[]) => drops.push(args) },
      attachTo: document.body, global: { plugins: [i18n] },
    })
    expect(wrapper.text()).toContain('页面A')
    const [a, b, c] = ['a', 'b', 'c'].map(id => wrapper.get(`[data-id="${id}"]`)) as [DOMWrapper<Element>, DOMWrapper<Element>, DOMWrapper<Element>]
    for (const item of [a, b, c]) item.element.getBoundingClientRect = () => ({ top: 0, height: 40 }) as DOMRect
    expect(a.attributes('draggable')).toBe('true')

    await a.trigger('dragstart')
    expect(a.classes()).toContain('opacity-50')
    await b.trigger('dragover', { clientY: 5 })
    expect(b.attributes('data-drop')).toBe('before')
    await b.trigger('dragover', { clientY: 20 })
    expect(b.attributes('data-drop')).toBe('inside')
    await b.trigger('dragleave')
    expect(b.attributes('data-drop')).toBeUndefined()
    await b.trigger('dragover', { clientY: 38 })
    expect(b.attributes('data-drop')).toBe('after')
    await c.trigger('dragover', { clientY: 20 })
    expect(c.attributes('data-drop')).toBeUndefined()
    await a.trigger('dragover', { clientY: 20 })
    await b.trigger('dragover', { clientY: 38 })
    await b.trigger('drop')
    expect(drops).toEqual([['a', 'b', 'after']])
    expect(a.classes()).not.toContain('opacity-50')

    // A drop on a row without a marker, or without a drag in progress, does nothing.
    await c.trigger('drop')
    await b.trigger('dragover', { clientY: 5 })
    await a.trigger('dragend')
    expect(drops).toHaveLength(1)
    wrapper.unmount()
  })

  it('shows only what leads to a match while filtering, expanded and marked', async () => {
    const wrapper = mountTree()
    // Collapsed first, so the filter is seen to open the way to a match on its own.
    await wrapper.find('[data-id="hq"] button').trigger('click')
    expect(labels(wrapper)).toEqual(['总部hq', '实验室'])
    await wrapper.setProps({ filter: ' 华东 ' })
    expect(labels(wrapper)).toEqual(['总部hq', '销售', '华东'])
    expect(wrapper.find('[data-id="hq"]').attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('[data-id="east"] mark').text()).toBe('华东')
    expect(wrapper.find('[data-id="hq"] mark').exists()).toBe(false)
    // A hint matches too, ignoring case, and a match does not hide its siblings' matches.
    await wrapper.setProps({ filter: 'HQ' })
    expect(labels(wrapper)).toEqual(['总部hq'])
    expect(wrapper.find('[data-id="hq"] mark').text()).toBe('hq')
    // Dragging is off while some neighbours are hidden.
    await wrapper.setProps({ draggable: true })
    expect(wrapper.find('[data-id="hq"]').attributes('draggable')).toBe('false')
    // Clearing the filter brings back the tree as it was, collapsed node included.
    await wrapper.setProps({ filter: '' })
    expect(labels(wrapper)).toEqual(['总部hq', '实验室'])
    expect(wrapper.find('[data-id="hq"]').attributes('draggable')).toBe('true')
    wrapper.unmount()
  })

  it('says so when nothing matches', async () => {
    const wrapper = mountTree()
    await wrapper.setProps({ filter: 'nowhere' })
    expect(wrapper.find('[role="tree"]').exists()).toBe(false)
    expect(wrapper.find('[role="status"]').text()).toContain('nowhere')
    wrapper.unmount()
  })
})
