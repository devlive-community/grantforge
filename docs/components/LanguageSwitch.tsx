// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { LOCALE_STORAGE_KEY, localeOf, switchHref, uiOf } from '@/lib/i18n'
import type { Locale } from '@/lib/i18n'

/** Switches the current page between the languages; an explicit pick is remembered over the browser language. */
export default function LanguageSwitch() {
  const pathname = usePathname() ?? '/'
  const locale = localeOf(pathname)
  const ui = uiOf(locale)
  function pick(target: Locale) {
    try { localStorage.setItem(LOCALE_STORAGE_KEY, target) } catch { /* private browsing keeps no memory */ }
  }
  return (
    <div role="group" aria-label={ui.languageAria} className="hidden items-center rounded-lg border border-line p-0.5 text-xs sm:flex">
      {(['zh', 'en'] as const).map(candidate => (
        <Link key={candidate} href={switchHref(pathname, candidate)} onClick={() => pick(candidate)}
          hrefLang={candidate === 'en' ? 'en' : 'zh-CN'} aria-current={locale === candidate ? 'true' : undefined}
          className={`rounded-md px-2 py-1 transition ${locale === candidate ? 'bg-brand-soft font-medium text-brand' : 'text-muted hover:text-ink'}`}>
          {candidate === 'zh' ? '中文' : 'EN'}
        </Link>
      ))}
    </div>
  )
}
