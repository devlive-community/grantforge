// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DataTable from './DataTable.vue'

const columns = [{ key: 'name', label: '名称' }, { key: 'email', label: '邮箱' }]

describe('data table', () => {
  it('renders column headers and scalar cells', () => {
    const wrapper = mount(DataTable, { props: { columns, rows: [{ id: 1, name: 'admin', email: 'a@x.org' }, { id: 2, name: 'bob' }] } })
    expect(wrapper.findAll('th').map(th => th.text())).toEqual(['名称', '邮箱'])
    expect(wrapper.findAll('th')[0]?.attributes('scope')).toBe('col')
    const cells = wrapper.findAll('tbody td').map(td => td.text())
    expect(cells).toEqual(['admin', 'a@x.org', 'bob', '—'])
  })

  it('lets callers render a column through a named slot', () => {
    const wrapper = mount(DataTable, {
      props: { columns, rows: [{ id: 1, name: 'admin' }] },
      slots: { name: '<strong>custom</strong>' },
    })
    expect(wrapper.get('tbody strong').text()).toBe('custom')
  })

  it('marks loading as busy and shows placeholders instead of rows', () => {
    const wrapper = mount(DataTable, { props: { columns, rows: [{ id: 1, name: 'x' }], loading: true } })
    expect(wrapper.get('[aria-busy]').attributes('aria-busy')).toBe('true')
    expect(wrapper.findAll('.animate-pulse')).toHaveLength(10)
    expect(wrapper.text()).not.toContain('x')
  })

  it('shows the empty state with custom text', () => {
    const wrapper = mount(DataTable, { props: { columns, rows: [], emptyTitle: '没有用户', emptyDescription: '先创建一个' } })
    expect(wrapper.text()).toContain('没有用户')
    expect(wrapper.text()).toContain('先创建一个')
  })

  it('reports errors as an alert with a retry action', async () => {
    const wrapper = mount(DataTable, { props: { columns, rows: [], error: '网络错误' } })
    expect(wrapper.get('[role="alert"]').text()).toContain('网络错误')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
