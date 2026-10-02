// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { i18n } from '@/i18n'
import type { Edge, Resource } from '@/lib/catalog'
import DependencyGraph from './DependencyGraph.vue'

const resource = (id: string, name: string) => ({ id, applicationId: '1', type: 'ACTION', code: `code.${id}`, name, sortOrder: 0, depth: 0,
  visible: true, enabled: true, denyMode: 'HIDE', builtin: false }) as Resource
const resources = [resource('page', '用户'), resource('edit', '编辑'), resource('view', '查看'), resource('read', '读取用户')]
const edges: Edge[] = [
  { resourceId: 'page', dependsOnId: 'edit', kind: 'REQUIRED' },
  { resourceId: 'edit', dependsOnId: 'view', kind: 'OPTIONAL' },
  { resourceId: 'view', dependsOnId: 'read', kind: 'REQUIRED' },
]

describe('dependency graph', () => {
  it('places what needs the root on the left and what it needs on the right', () => {
    const wrapper = mount(DependencyGraph, { props: { root: 'edit', edges, resources }, global: { plugins: [i18n] } })
    const columns = Object.fromEntries(wrapper.findAll('[data-node]').map(node => [node.attributes('data-node'), node.attributes('data-column')]))
    expect(columns).toEqual({ edit: '0', view: '1', read: '2', page: '-1' })
    expect(wrapper.get('[data-node="page"]').attributes('style')).toContain('left: 0px')
    expect(wrapper.get('[data-node="edit"]').classes()).toContain('text-brand')
    expect(wrapper.get('[data-node="read"]').text()).toBe('读取用户')
    expect(wrapper.get('[data-node="read"]').attributes('title')).toBe('code.read')
    expect(wrapper.get('[data-edge="edit-view"]').attributes('stroke-dasharray')).toBe('4 4')
    expect(wrapper.get('[data-edge="view-read"]').attributes('stroke-dasharray')).toBeUndefined()
    expect(wrapper.get('[role="img"]').attributes('aria-label')).toBe('依赖关系图，共 3 个相关资源')
  })

  it('says so when a resource has no dependencies and names unknown nodes by ID', () => {
    expect(mount(DependencyGraph, { props: { root: 'lonely', edges, resources }, global: { plugins: [i18n] } }).text())
      .toContain('这个资源没有任何依赖关系')
    const wrapper = mount(DependencyGraph, { props: { root: 'gone', edges: [{ resourceId: 'gone', dependsOnId: 'read', kind: 'REQUIRED' }], resources },
      global: { plugins: [i18n] } })
    expect(wrapper.get('[data-node="gone"]').text()).toBe('gone')
  })
})
