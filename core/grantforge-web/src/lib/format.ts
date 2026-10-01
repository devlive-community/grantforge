// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { currentLocale } from '@/i18n'

export function dateLabel(value?: string): string {
  if (!value) return '—'
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat(currentLocale(), { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date)
}
export function initials(name: string): string { return name.slice(0, 2).toUpperCase() }
export function cellLabel(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return typeof value === 'object' ? '—' : String(value)
}
const browsers: [RegExp, string][] = [[/Edg\//, 'Edge'], [/OPR\/|Opera/, 'Opera'], [/Firefox\//, 'Firefox'], [/Chrome\//, 'Chrome'], [/Safari\//, 'Safari']]
const systems: [RegExp, string][] = [[/Windows/, 'Windows'], [/iPhone|iPad/, 'iOS'], [/Mac OS X|Macintosh/, 'macOS'], [/Android/, 'Android'], [/Linux/, 'Linux']]
/** Names the browser and operating system of a user agent, such as "Chrome · macOS"; unknown agents stay as sent. */
export function agentLabel(agent?: string | null): string {
  if (!agent) return '—'
  const browser = browsers.find(([pattern]) => pattern.test(agent))?.[1]
  const system = systems.find(([pattern]) => pattern.test(agent))?.[1]
  return browser || system ? [browser, system].filter(Boolean).join(' · ') : agent
}
