// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { accessTypesAt, canEndAt, chooseLevel, childrenOf, coverage, draftOf, emptyDraft, emptyItem, endsFor, localInput,
  requestOf } from './policy'
import { salesPolicy, warehouse } from '../../tests/unit/policies'

describe('policy drafts', () => {
  it('walks the resource levels of a type', () => {
    expect(childrenOf(warehouse).map(level => level.name)).toEqual(['database', 'path'])
    expect(childrenOf(warehouse, 'table').map(level => level.name)).toEqual(['column'])
    const [database, , column] = warehouse.resources
    expect(database && canEndAt(warehouse, database)).toBe(true)
    expect(column && canEndAt(warehouse, column)).toBe(true)
    const table = warehouse.resources[1]
    expect(table && canEndAt(warehouse, { ...table, validLeaf: false })).toBe(false)
    expect(accessTypesAt(warehouse, 'column').map(access => access.name)).toEqual(['select'])
    expect(accessTypesAt(warehouse, 'table').map(access => access.name)).toEqual(['select', 'update'])
    expect(accessTypesAt(warehouse).map(access => access.name)).toEqual(['select', 'update'])
    expect(endsFor(warehouse, 'DATA_MASK')).toEqual(['column'])
    expect(endsFor(warehouse, 'ROW_FILTER')).toEqual(['table'])
    expect(endsFor(warehouse, 'ACCESS')).toHaveLength(4)
  })

  it('starts new policies at the first top level with one item', () => {
    const draft = emptyDraft(warehouse)
    expect(draft.levels).toEqual([{ name: 'database', values: [], excludes: false, recursive: false }])
    expect(draft.allow).toEqual([emptyItem()])
    expect(draft.enabled).toBe(true)
    expect(emptyDraft({ ...warehouse, resources: [] }).levels).toEqual([])
  })

  it('cuts the chain below a level when another is chosen', () => {
    const draft = { ...emptyDraft(warehouse), levels: [
      { name: 'database', values: ['sales'], excludes: false, recursive: false },
      { name: 'table', values: ['orders'], excludes: false, recursive: false },
      { name: 'column', values: ['id'], excludes: true, recursive: false }] }
    expect(chooseLevel(draft, 1, 'table').map(level => level.values)).toEqual([['sales'], ['orders']])
    expect(chooseLevel(draft, 0, 'path')).toEqual([{ name: 'path', values: [], excludes: false, recursive: false }])
    expect(chooseLevel(draft, 2, '')).toHaveLength(2)
  })

  it('turns a stored policy into a draft and back', () => {
    const draft = draftOf(salesPolicy, warehouse)
    expect(draft.levels.map(level => level.name)).toEqual(['database', 'table'])
    expect(draft.override).toBe(true)
    expect(draft.allow[0]?.conditions).toEqual({ 'ip-range': ['10.0.0.0/8'] })
    expect(draft.validity[0]?.from).toBe(localInput('2026-01-01T00:00:00Z'))
    expect(draft.validity[0]?.until).toBe('')

    const request = requestOf(draft, 'ACCESS', salesPolicy.version)
    expect(request).toMatchObject({ type: 'ACCESS', name: 'sales', description: 'sales team', priority: 'OVERRIDE', enabled: true,
      labels: ['pii'], version: 3 })
    expect(request.document.resources).toEqual({ database: salesPolicy.document.resources.database, table: salesPolicy.document.resources.table })
    expect(request.document.allow).toEqual([{ users: ['alice'], groups: [], roles: [], accessTypes: ['select'],
      conditions: [{ type: 'ip-range', values: ['10.0.0.0/8'] }] }])
    expect(request.document.deny[0]?.groups).toEqual(['ops'])
    expect(request.document.validity).toEqual([{ from: '2026-01-01T00:00:00.000Z', until: undefined }])
  })

  it('keeps only the items and parts a kind of policy has', () => {
    const draft = { ...emptyDraft(warehouse), name: ' mask ', description: ' ', validity: [{ from: '', until: '' }],
      allow: [{ ...emptyItem(), users: ['bob'], accessTypes: ['select'], conditions: { 'ip-range': [] }, maskType: 'custom',
        maskValue: ' left(ssn, 3) ', rowFilter: 'x' }],
      deny: [{ ...emptyItem(), users: ['eve'] }] }
    const masking = requestOf(draft, 'DATA_MASK')
    expect(masking.name).toBe('mask')
    expect(masking.description).toBeUndefined()
    expect(masking.priority).toBe('NORMAL')
    expect(masking.document.deny).toEqual([])
    expect(masking.document.validity).toEqual([])
    expect(masking.document.allow[0]).toMatchObject({ maskType: 'custom', maskValue: 'left(ssn, 3)', rowFilter: undefined, conditions: [] })
    const filtering = requestOf({ ...draft, allow: [{ ...emptyItem(), rowFilter: ' region = 1 ', maskType: 'redact' }] }, 'ROW_FILTER')
    expect(filtering.document.allow[0]).toMatchObject({ rowFilter: 'region = 1', maskType: undefined, maskValue: undefined })
  })

  it('describes what a policy covers', () => {
    expect(coverage(salesPolicy, warehouse)).toBe('Database: sales / Table: orders')
    const odd = { ...salesPolicy, document: { ...salesPolicy.document, resources: {
      path: { values: ['/data'], excludes: false, recursive: true }, other: { values: ['a'], excludes: true, recursive: false } } } }
    expect(coverage(odd)).toBe('path: /data … / other: ≠ a')
    expect(localInput()).toBe('')
  })
})
