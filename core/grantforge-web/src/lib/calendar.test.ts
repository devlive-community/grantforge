// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { addDays, addMonths, daysIn, displayValue, formatValue, monthGrid, now, parseValue, weekdayNames, weekStart, withinBounds } from './calendar'

describe('calendar', () => {
  it('reads and writes values the way the native inputs did', () => {
    expect(parseValue('2026-10-09')).toEqual({ year: 2026, month: 10, day: 9, hour: 0, minute: 0 })
    expect(parseValue('2026-10-09T18:05')).toEqual({ year: 2026, month: 10, day: 9, hour: 18, minute: 5 })
    expect(parseValue('2026-10-09T18:05:59.123')).toMatchObject({ hour: 18, minute: 5 })
    for (const wrong of ['', '2026-13-01', '2026-02-30', '2026-10-09T24:00', '09/10/2026']) expect(parseValue(wrong)).toBeUndefined()
    const moment = { year: 2026, month: 3, day: 7, hour: 9, minute: 4 }
    expect(formatValue(moment, false)).toBe('2026-03-07')
    expect(formatValue(moment, true)).toBe('2026-03-07T09:04')
  })

  it('counts days and moves across months and years', () => {
    expect(daysIn(2024, 2)).toBe(29)
    expect(daysIn(2026, 2)).toBe(28)
    expect(addDays({ year: 2026, month: 12, day: 31 }, 1)).toEqual({ year: 2027, month: 1, day: 1 })
    expect(addDays({ year: 2026, month: 3, day: 1 }, -1)).toEqual({ year: 2026, month: 2, day: 28 })
    expect(addMonths({ year: 2026, month: 1, day: 31 }, 1)).toEqual({ year: 2026, month: 2, day: 28 })
    expect(addMonths({ year: 2026, month: 1, day: 15 }, -1)).toEqual({ year: 2025, month: 12, day: 15 })
    expect(addMonths({ year: 2026, month: 5, day: 15 }, 12)).toEqual({ year: 2027, month: 5, day: 15 })
  })

  it('lays out six weeks from the language\'s first weekday', () => {
    expect(weekStart('zh-CN')).toBe(1)
    expect(weekStart('en-US')).toBe(0)
    // October 2026 starts on a Thursday.
    const monday = monthGrid(2026, 10, 1)
    expect(monday).toHaveLength(42)
    expect(monday[0]).toEqual({ year: 2026, month: 9, day: 28, inMonth: false })
    expect(monday[3]).toEqual({ year: 2026, month: 10, day: 1, inMonth: true })
    expect(monthGrid(2026, 10, 0)[0]).toEqual({ year: 2026, month: 9, day: 27, inMonth: false })
    expect(monday.filter(cell => cell.inMonth)).toHaveLength(31)
    expect(weekdayNames('en-US', 0).map(name => name.long)[0]).toBe('Sunday')
    expect(weekdayNames('en-US', 1).map(name => name.long)[6]).toBe('Sunday')
  })

  it('keeps to the bounds by day', () => {
    const day = { year: 2026, month: 10, day: 9 }
    expect(withinBounds(day)).toBe(true)
    expect(withinBounds(day, '2026-10-09T23:59')).toBe(true)
    expect(withinBounds(day, '2026-10-10')).toBe(false)
    expect(withinBounds(day, undefined, '2026-10-08')).toBe(false)
    expect(withinBounds(day, '2026-10-01', '2026-10-31')).toBe(true)
  })

  it('shows values in the interface language and knows the time now', () => {
    expect(displayValue('en-US', { year: 2026, month: 10, day: 9, hour: 18, minute: 5 }, true)).toBe('10/09/2026, 18:05')
    expect(displayValue('zh-CN', { year: 2026, month: 10, day: 9, hour: 0, minute: 0 }, false)).toBe('2026/10/09')
    expect(now(new Date(2026, 9, 9, 7, 30))).toEqual({ year: 2026, month: 10, day: 9, hour: 7, minute: 30 })
  })
})
