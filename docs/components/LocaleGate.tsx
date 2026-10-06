// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import { useEffect } from 'react'
import { usePathname } from 'next/navigation'
import { LOCALE_STORAGE_KEY, LOCALES, localeOf, switchHref } from '@/lib/i18n'
import type { Locale } from '@/lib/i18n'

function remembered(): Locale | undefined {
  try {
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
    return LOCALES.find(locale => locale === stored)
  } catch { /* private browsing keeps no memory */ }
  return undefined
}

/** The language the browser asks for, falling back to English for anything but Chinese and Russian. */
function browserLanguage(): Locale {
  const language = (navigator.language || '').toLowerCase()
  if (language.startsWith('zh')) return 'zh'
  if (language.startsWith('ru')) return 'ru'
  return 'en'
}

/**
 * Applies the remembered language, or the browser language on a first visit, and keeps <html lang> in sync.
 * Runs after paint: the static export cannot read the browser language on the server.
 */
export default function LocaleGate() {
  const pathname = usePathname() ?? '/'
  useEffect(() => {
    const current = localeOf(pathname)
    document.documentElement.lang = current === 'zh' ? 'zh-CN' : current
    const target = remembered() ?? browserLanguage()
    if (target !== current) window.location.replace(switchHref(pathname, target))
  }, [pathname])
  return null
}
