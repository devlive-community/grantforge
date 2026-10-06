// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { LOCALE_HREFLANG, LOCALE_LABELS, LOCALES, LOCALE_STORAGE_KEY, localeOf, switchHref, uiOf } from '@/lib/i18n'
import type { Locale } from '@/lib/i18n'

/** Switches the current page between the languages; an explicit pick is remembered over the browser language. */
export default function LanguageSwitch() {
  const pathname = usePathname() ?? '/'
  const locale = localeOf(pathname)
  const ui = uiOf(locale)
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    // The list closes the way every menu does: a click outside it, or the Escape key.
    function onPointerDown(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])
  function pick(target: Locale) {
    setOpen(false)
    try { localStorage.setItem(LOCALE_STORAGE_KEY, target) } catch { /* private browsing keeps no memory */ }
  }
  return (
    <div ref={root} className="relative">
      <button type="button" onClick={() => setOpen(!open)} aria-expanded={open} aria-label={ui.languageAria}
        className="flex h-9 items-center gap-1.5 rounded-lg border border-line px-2.5 text-xs text-muted transition hover:text-ink">
        <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3a15 15 0 0 1 0 18 15 15 0 0 1 0-18Z" /></svg>
        <span>{LOCALE_LABELS[locale]}</span>
        <svg viewBox="0 0 24 24" className={`size-3.5 transition ${open ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
      </button>
      {open && (
        <ul className="absolute right-0 top-11 z-40 min-w-32 overflow-hidden whitespace-nowrap rounded-xl border border-line bg-surface py-1 shadow-xl">
          {LOCALES.map(candidate => (
            <li key={candidate}>
              <Link href={switchHref(pathname, candidate)} onClick={() => pick(candidate)}
                hrefLang={LOCALE_HREFLANG[candidate]} lang={LOCALE_HREFLANG[candidate]} aria-current={locale === candidate ? 'true' : undefined}
                className={`block px-3 py-2 text-sm transition ${locale === candidate ? 'bg-brand-soft font-medium text-brand' : 'text-muted hover:bg-canvas hover:text-ink'}`}>
                {LOCALE_LABELS[candidate]}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
