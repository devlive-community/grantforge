// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { orgOptions } from './org'

const units = [
  { id: '4', code: 'lab', name: '实验室', sortOrder: 1, depth: 0 },
  { id: '1', code: 'hq', name: '总部', sortOrder: 0, depth: 0 },
  { id: '5', parentId: '1', code: 'rnd', name: '研发部', sortOrder: 1, depth: 1 },
  { id: '2', parentId: '1', code: 'sales', name: '销售部', sortOrder: 0, depth: 1 },
  { id: '3', parentId: '2', code: 'east', name: '华东', sortOrder: 0, depth: 2 },
]

describe('department options', () => {
  it('lists departments in tree order with indentation', () => {
    expect(orgOptions(units).map(option => option.label)).toEqual(['总部', '— 销售部', '— — 华东', '— 研发部', '实验室'])
    expect(orgOptions(units)[0]).toEqual({ value: '1', label: '总部' })
    expect(orgOptions([])).toEqual([])
  })

  it('leaves out a subtree', () => {
    expect(orgOptions(units, '2').map(option => option.value)).toEqual(['1', '5', '4'])
    expect(orgOptions(units, null)).toHaveLength(5)
  })
})
