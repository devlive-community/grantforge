// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import { useEffect } from 'react'
import { usePathname } from 'next/navigation'
import { LOCALE_STORAGE_KEY, localeOf, switchHref } from '@/lib/i18n'
import type { Locale } from '@/lib/i18n'

function remembered(): Locale | undefined {
  try {
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
    return stored === 'zh' || stored === 'en' ? stored : undefined
  } catch { /* private browsing keeps no memory */ }
  return undefined
}

/**
 * Applies the remembered language, or the browser language on a first visit, and keeps <html lang> in sync.
 * Runs after paint: the static export cannot read the browser language on the server.
 */
export default function LocaleGate() {
  const pathname = usePathname() ?? '/'
  useEffect(() => {
    const current = localeOf(pathname)
    document.documentElement.lang = current === 'en' ? 'en' : 'zh-CN'
    const target = remembered() ?? ((navigator.language || '').toLowerCase().startsWith('zh') ? 'zh' : 'en')
    if (target !== current) window.location.replace(switchHref(pathname, target))
  }, [pathname])
  return null
}
