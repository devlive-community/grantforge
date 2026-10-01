// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createI18n } from 'vue-i18n'
import enUS from './locales/en-US'
import zhCN, { type Messages } from './locales/zh-CN'

/** Supported interface languages. */
export const LOCALES = ['zh-CN', 'en-US'] as const
export type Locale = typeof LOCALES[number]

const STORAGE_KEY = 'GrantForgeLocale'

declare module 'vue-i18n' {
  // Type-checks every message key used with t() against the zh-CN dictionary.
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  export interface DefineLocaleMessage extends Messages {}
}

/**
 * Chooses the interface language: a saved choice wins, otherwise Chinese browsers get zh-CN and all other
 * browsers get en-US.
 */
export function detectLocale(stored: string | null, browser: string | undefined): Locale {
  if (stored && (LOCALES as readonly string[]).includes(stored)) return stored as Locale
  return browser?.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
}

function readStored(): string | null {
  try { return localStorage.getItem(STORAGE_KEY) } catch { return null }
}

export const i18n = createI18n({
  legacy: false,
  locale: detectLocale(readStored(), typeof navigator === 'undefined' ? undefined : navigator.language),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, 'en-US': enUS },
})

/** Returns the active interface language. */
export function currentLocale(): Locale {
  return i18n.global.locale.value as Locale
}

/** Switches the interface language, remembers the choice and updates the document language. */
export function setLocale(locale: Locale): void {
  i18n.global.locale.value = locale
  document.documentElement.lang = locale
  try { localStorage.setItem(STORAGE_KEY, locale) } catch { /* storage may be unavailable; the choice still applies */ }
}

/** Translates outside components (stores, API client, router). */
export const translate = i18n.global.t
