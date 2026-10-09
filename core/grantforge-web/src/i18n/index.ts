// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createI18n } from 'vue-i18n'
import enUS from './locales/en-US'
import zhCN, { type Messages } from './locales/zh-CN'
import zhTW from './locales/zh-TW'
import { LOCALE_LABELS } from './locales/labels'

export { LOCALE_LABELS }

/** Supported interface languages, in the order the picker lists them. */
export const LOCALES = ['zh-CN', 'zh-TW', 'en-US'] as const
export type Locale = typeof LOCALES[number]

/** The language of a browser, when we ship it. */
function supported(candidate: string): candidate is Locale {
  return (LOCALES as readonly string[]).includes(candidate)
}

/**
 * The interface language a browser asks for, or undefined when we ship none of its languages. Chinese is the one
 * language we carry in two variants, so its region or script decides which of them the browser gets.
 */
function askedFor(browser: string | undefined): Locale | undefined {
  if (!browser) return undefined
  const asked = browser.toLowerCase()
  const language = asked.split('-')[0]
  if (language === 'zh') {
    const traditional = /hant|tw|hk|mo/.test(asked) ? 'zh-TW' : 'zh-CN'
    return supported(traditional) ? traditional : 'zh-CN'
  }
  return LOCALES.find(locale => locale.toLowerCase().split('-')[0] === language)
}

const STORAGE_KEY = 'GrantForgeLocale'

declare module 'vue-i18n' {
  // Type-checks every message key used with t() against the zh-CN dictionary.
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  export interface DefineLocaleMessage extends Messages {}
}

/**
 * Chooses the interface language: a saved choice wins, otherwise the browser language picks one of ours, and a
 * browser we do not cover gets English.
 */
export function detectLocale(stored: string | null, browser: string | undefined): Locale {
  if (stored && supported(stored)) return stored
  return askedFor(browser) ?? 'en-US'
}

function readStored(): string | null {
  try { return localStorage.getItem(STORAGE_KEY) } catch { return null }
}

export const i18n = createI18n({
  legacy: false,
  locale: detectLocale(readStored(), typeof navigator === 'undefined' ? undefined : navigator.language),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, 'zh-TW': zhTW, 'en-US': enUS },
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
