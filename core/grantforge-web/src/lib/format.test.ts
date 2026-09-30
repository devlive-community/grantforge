// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { cellLabel, dateLabel, initials } from './format'

describe('format helpers', () => {
  it('formats server timestamps and falls back for missing or invalid values', () => {
    expect(dateLabel('2026-09-30 09:30:00')).toMatch(/09\/30.*09:30/)
    expect(dateLabel(undefined)).toBe('—')
    expect(dateLabel('')).toBe('—')
    expect(dateLabel('not a date')).toBe('not a date')
  })

  it('builds initials from the first two characters', () => {
    expect(initials('admin')).toBe('AD')
    expect(initials('a')).toBe('A')
    expect(initials('')).toBe('')
  })

  it('renders only scalar cell values', () => {
    expect(cellLabel('x')).toBe('x')
    expect(cellLabel(0)).toBe('0')
    expect(cellLabel(false)).toBe('false')
    expect(cellLabel(null)).toBe('—')
    expect(cellLabel(undefined)).toBe('—')
    expect(cellLabel('')).toBe('—')
    expect(cellLabel({ nested: true })).toBe('—')
  })
})
