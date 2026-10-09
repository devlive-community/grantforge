// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/** A calendar day; months count from 1. */
export interface Day { year: number; month: number; day: number }

/** A picked value: a day, and a time of day when times are picked too. */
export interface Moment extends Day { hour: number; minute: number }

/** One cell of a month's grid. */
export interface Cell extends Day { inMonth: boolean }

const pad = (value: number, length = 2) => String(value).padStart(length, '0')

/** The value's text, as the native inputs write it: `YYYY-MM-DD`, or `YYYY-MM-DDTHH:mm` with a time. */
export function formatValue(moment: Moment, withTime: boolean): string {
  const date = `${pad(moment.year, 4)}-${pad(moment.month)}-${pad(moment.day)}`
  return withTime ? `${date}T${pad(moment.hour)}:${pad(moment.minute)}` : date
}

/** Reads a value written like the native inputs do; seconds are ignored, anything else is no value. */
export function parseValue(value: string): Moment | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?)?$/.exec(value.trim())
  if (!match) return undefined
  const [year, month, day, hour, minute] = match.slice(1).map(part => part === undefined ? 0 : Number(part)) as number[]
  const moment = { year: year ?? 0, month: month ?? 0, day: day ?? 0, hour: hour ?? 0, minute: minute ?? 0 }
  const valid = moment.month >= 1 && moment.month <= 12 && moment.day >= 1 && moment.day <= daysIn(moment.year, moment.month)
    && moment.hour <= 23 && moment.minute <= 59
  return valid ? moment : undefined
}

/** How many days a month has. */
export function daysIn(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate()
}

/** The day a number of days later, or earlier for a negative number. */
export function addDays(day: Day, days: number): Day {
  const date = new Date(Date.UTC(day.year, day.month - 1, day.day + days))
  return { year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, day: date.getUTCDate() }
}

/** The same day some months later or earlier, moved to the month's last day when it has fewer days. */
export function addMonths(day: Day, months: number): Day {
  const index = day.year * 12 + day.month - 1 + months
  const year = Math.floor(index / 12), month = index - year * 12 + 1
  return { year, month, day: Math.min(day.day, daysIn(year, month)) }
}

/** 0 for Sunday through 6 for Saturday. */
export function weekday(day: Day): number {
  return new Date(Date.UTC(day.year, day.month - 1, day.day)).getUTCDay()
}

/** The weekday a week starts on in a language: Sunday in American English, Monday elsewhere, as in China. */
export function weekStart(locale: string): number {
  return locale === 'en-US' ? 0 : 1
}

/** Six weeks covering a month, starting on the week's first day, with the neighbouring months' days marked. */
export function monthGrid(year: number, month: number, firstDay: number): Cell[] {
  const first = { year, month, day: 1 }
  const start = addDays(first, -((weekday(first) - firstDay + 7) % 7))
  return Array.from({ length: 42 }, (_, index) => {
    const day = addDays(start, index)
    return { ...day, inMonth: day.month === month && day.year === year }
  })
}

/** Orders days: negative before, zero the same day, positive after. */
export function compareDays(a: Day, b: Day): number {
  return a.year - b.year || a.month - b.month || a.day - b.day
}

/** Whether a day lies within optional bounds written as values; only their days count. */
export function withinBounds(day: Day, min?: string, max?: string): boolean {
  const low = min ? parseValue(min) : undefined, high = max ? parseValue(max) : undefined
  return (!low || compareDays(day, low) >= 0) && (!high || compareDays(day, high) <= 0)
}

/** Today, and the time now, in the browser's time zone. */
export function now(clock: Date = new Date()): Moment {
  return { year: clock.getFullYear(), month: clock.getMonth() + 1, day: clock.getDate(), hour: clock.getHours(), minute: clock.getMinutes() }
}

/** The weekday names for the grid's header, short and in full, starting with the week's first day. */
export function weekdayNames(locale: string, firstDay: number): { short: string; long: string }[] {
  // 2023-01-01 was a Sunday.
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(Date.UTC(2023, 0, 1 + (firstDay + index) % 7))
    return { short: new Intl.DateTimeFormat(locale, { weekday: 'narrow', timeZone: 'UTC' }).format(date),
      long: new Intl.DateTimeFormat(locale, { weekday: 'long', timeZone: 'UTC' }).format(date) }
  })
}

/** A month's title, such as "2026年10月" or "October 2026". */
export function monthTitle(locale: string, year: number, month: number): string {
  return new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(Date.UTC(year, month - 1, 1)))
}

/** A day as people read it, for labels such as the grid's buttons. */
export function dayLabel(locale: string, day: Day): string {
  return new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long', timeZone: 'UTC' })
    .format(new Date(Date.UTC(day.year, day.month - 1, day.day)))
}

/** A value as the field shows it, in the interface's language. */
export function displayValue(locale: string, moment: Moment, withTime: boolean): string {
  const date = new Date(Date.UTC(moment.year, moment.month - 1, moment.day, moment.hour, moment.minute))
  return new Intl.DateTimeFormat(locale, { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'UTC',
    ...(withTime ? { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' } : {}) }).format(date)
}
