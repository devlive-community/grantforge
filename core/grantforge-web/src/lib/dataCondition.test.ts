// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { childPath, emptyComparison, emptyGroup, fromCondition, takesList, takesValue, toCondition, variablesFor } from './dataCondition'
import { localInput } from './policy'
import { users, variables } from '../../tests/unit/dataEntities'

const field = (code: string) => users.fields.find(candidate => candidate.code === code)

describe('data conditions', () => {
  it('writes values of every kind and variables in the server grammar', () => {
    const group = { type: 'group' as const, join: 'and' as const, negate: false, children: [
      { ...emptyComparison(field('status')), op: 'in', values: ['ACTIVE'] },
      { ...emptyComparison(field('age')), op: 'gt', value: '18' },
      { ...emptyComparison(field('admin')), value: 'true' },
      { ...emptyComparison(field('lastLoginAt')), op: 'lt', value: '2026-01-01T08:00' },
      { ...emptyComparison(field('username')), variable: 'subject.username' },
      { ...emptyComparison(field('username')), op: 'is_null' },
      { type: 'group' as const, join: 'or' as const, negate: true, children: [{ ...emptyComparison(field('age')), op: 'eq', value: '' }] },
    ] }
    expect(toCondition(group, users.fields)).toEqual({ and: [
      { field: 'status', op: 'in', value: ['ACTIVE'] },
      { field: 'age', op: 'gt', value: 18 },
      { field: 'admin', op: 'eq', value: true },
      { field: 'lastLoginAt', op: 'lt', value: new Date('2026-01-01T08:00').toISOString() },
      { field: 'username', op: 'eq', value: { var: 'subject.username' } },
      { field: 'username', op: 'is_null' },
      { not: { or: [{ field: 'age', op: 'eq', value: '' }] } },
    ] })
    expect(toCondition({ ...emptyGroup(), children: [{ ...emptyComparison(field('lastLoginAt')), value: '' }] }, users.fields))
      .toEqual({ and: [{ field: 'lastLoginAt', op: 'eq', value: '' }] })
  })

  it('reads stored conditions back into groups', () => {
    const stored = { not: { or: [{ field: 'status', op: 'eq', value: 'ACTIVE' }, { field: 'lastLoginAt', op: 'gt', value: '2026-01-01T00:00:00Z' },
      { field: 'age', op: 'in', value: [1, 2] }, { field: 'username', op: 'ne', value: { var: 'subject.username' } }, { and: [] }] } }
    const group = fromCondition(stored, users.fields)
    expect(group).toMatchObject({ join: 'or', negate: true })
    expect(group.children[0]).toMatchObject({ field: 'status', op: 'eq', value: 'ACTIVE', variable: '' })
    expect(group.children[1]).toMatchObject({ value: localInput('2026-01-01T00:00:00Z') })
    expect(group.children[2]).toMatchObject({ values: ['1', '2'], value: '' })
    expect(group.children[3]).toMatchObject({ variable: 'subject.username', value: '' })
    expect(group.children[4]).toEqual({ type: 'group', join: 'and', negate: false, children: [] })
    // A lone or negated comparison becomes a group.
    expect(fromCondition({ field: 'age', op: 'is_null' }, users.fields)).toMatchObject({ join: 'and', negate: false, children: [{ field: 'age', value: '' }] })
    expect(fromCondition({ not: { field: 'age', op: 'eq', value: null } }, users.fields)).toMatchObject({ negate: true,
      children: [{ field: 'age', value: '' }] })
    expect(fromCondition({ unknown: true }, [])).toMatchObject({ children: [{ field: '', op: 'eq' }] })
    expect(fromCondition(null, [])).toMatchObject({ children: [{ field: '' }] })
  })

  it('knows which comparisons take values, lists and which variables', () => {
    expect(takesList('not_in')).toBe(true)
    expect(takesList('eq')).toBe(false)
    expect(takesValue('is_null')).toBe(false)
    expect(variablesFor(variables, field('age'), 'in').map(variable => variable.key)).toEqual(['subject.orgUnitIds'])
    expect(variablesFor(variables, field('age'), 'eq').map(variable => variable.key)).toEqual(['subject.id'])
    expect(variablesFor(variables, undefined, 'eq')).toEqual([])
    expect(emptyComparison()).toMatchObject({ field: '', op: 'eq' })
    expect(childPath({ ...emptyGroup(), negate: true }, 'condition', 2)).toBe('condition.not.and[2]')
    expect(childPath({ ...emptyGroup(), join: 'or' }, 'condition.and[0]', 1)).toBe('condition.and[0].or[1]')
  })
})
