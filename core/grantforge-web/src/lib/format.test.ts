// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { agentLabel, cellLabel, dateLabel, initials } from './format'

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
  it('names browsers and systems of user agents', () => {
    expect(agentLabel('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36')).toBe('Chrome · macOS')
    expect(agentLabel('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0 Safari/537.36 Edg/128.0')).toBe('Edge · Windows')
    expect(agentLabel('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1')).toBe('Safari · iOS')
    expect(agentLabel('Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0')).toBe('Firefox · Linux')
    expect(agentLabel('curl/8.7.1')).toBe('curl/8.7.1')
    expect(agentLabel(null)).toBe('—')
  })
})
