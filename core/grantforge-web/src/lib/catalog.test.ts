// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { allowsParent, childTypes, displayName, hasDenyMode, hasRoute, isWithin, layoutDependencies, type Edge, type Resource } from './catalog'

const resource = (id: string, parentId?: string) => ({ id, parentId, applicationId: '1', type: 'MODULE', code: id, name: id,
  sortOrder: 0, depth: 0, visible: true, enabled: true, denyMode: 'HIDE', builtin: false }) as Resource

describe('catalog', () => {
  it('mirrors the server placement rules', () => {
    expect(childTypes(null)).toEqual(['MODULE', 'MENU', 'PAGE', 'API', 'DATA_ENTITY'])
    expect(childTypes('PAGE')).toEqual(['TAB', 'ACTION'])
    expect(childTypes('DATA_ENTITY')).toEqual(['FIELD'])
    expect(childTypes('ACTION')).toEqual([])
    expect(allowsParent('ACTION', null)).toBe(false)
    expect(allowsParent('MENU', 'MENU')).toBe(true)
  })

  it('knows which types have routes and deny modes', () => {
    expect(['MENU', 'PAGE', 'TAB', 'ACTION', 'API'].map(type => [hasRoute(type as Resource['type']), hasDenyMode(type as Resource['type'])]))
      .toEqual([[true, true], [true, true], [true, true], [false, true], [false, false]])
  })

  it('finds ancestors', () => {
    const tree = [resource('a'), resource('b', 'a'), resource('c', 'b'), resource('d')]
    expect(isWithin(tree, 'c', 'a')).toBe(true)
    expect(isWithin(tree, 'a', 'a')).toBe(true)
    expect(isWithin(tree, 'a', 'c')).toBe(false)
    expect(isWithin(tree, 'd', 'a')).toBe(false)
    expect(isWithin(tree, 'missing', 'a')).toBe(false)
  })

  it('lays dependencies out in columns around the root', () => {
    const edges: Edge[] = [
      { resourceId: 'edit', dependsOnId: 'view', kind: 'REQUIRED' },
      { resourceId: 'edit', dependsOnId: 'update', kind: 'REQUIRED' },
      { resourceId: 'view', dependsOnId: 'read', kind: 'OPTIONAL' },
      { resourceId: 'update', dependsOnId: 'read', kind: 'REQUIRED' },
      { resourceId: 'page', dependsOnId: 'edit', kind: 'REQUIRED' },
      { resourceId: 'other', dependsOnId: 'unrelated', kind: 'REQUIRED' },
    ]
    expect(layoutDependencies('edit', edges)).toEqual([
      { id: 'edit', column: 0, row: 0 },
      { id: 'view', column: 1, row: 0 },
      { id: 'update', column: 1, row: 1 },
      { id: 'read', column: 2, row: 0 },
      { id: 'page', column: -1, row: 0 },
    ])
    expect(layoutDependencies('lonely', edges)).toEqual([{ id: 'lonely', column: 0, row: 0 }])
  })

  it('names built-in resources in the user\'s language', () => {
    expect(displayName({ name: 'Users', nameKey: 'titles.users' })).toBe('用户管理')
    expect(displayName({ name: 'Users', nameKey: 'permissionNames.userCreate' })).toBe('新建用户')
    expect(displayName({ name: 'Mine', nameKey: 'no.such.key' })).toBe('Mine')
    expect(displayName({ name: 'Mine', nameKey: null })).toBe('Mine')
  })
})
