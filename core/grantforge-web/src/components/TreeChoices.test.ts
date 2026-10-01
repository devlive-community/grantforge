// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { MenuTree } from '@/types/api'
import TreeChoices from './TreeChoices.vue'

const nodes: MenuTree[] = [
  { id: 1, title: '系统', children: [{ id: 2, title: '用户' }, { id: 3, title: '角色' }] },
  { id: 4, title: '工具' },
]

describe('tree choices', () => {
  it('renders nested nodes with indentation', () => {
    const wrapper = mount(TreeChoices, { props: { nodes, selected: [] } })
    expect(wrapper.text()).toContain('用户')
    const labels = wrapper.findAll('label')
    expect(labels).toHaveLength(4)
    expect(labels[1]?.attributes('style')).toContain('padding-left: 32px')
  })

  it('derives checked and partial states from the selection', () => {
    const partial = mount(TreeChoices, { props: { nodes, selected: [2] } })
    expect(partial.findAll('input')[0]?.attributes('aria-checked')).toBe('mixed')
    const full = mount(TreeChoices, { props: { nodes, selected: [1, 2, 3] } })
    expect(full.findAll('input')[0]?.attributes('aria-checked')).toBe('true')
  })

  it('emits toggles from any depth with the node id', async () => {
    const wrapper = mount(TreeChoices, { props: { nodes, selected: [] } })
    await wrapper.findAll('input')[1]?.setValue(true)
    await wrapper.findAll('input')[3]?.setValue(true)
    expect(wrapper.emitted('toggle')).toEqual([[2, true], [4, true]])
  })
})
