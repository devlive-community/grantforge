// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { localeOf, switchHref, uiOf } from '@/lib/i18n'

/**
 * The 404 page of every route. It reads the language from the pathname, because a static export cannot know it
 * while rendering and this shell is shared by all four locales.
 */
export default function NotFound() {
  const pathname = usePathname() ?? '/'
  const locale = localeOf(pathname)
  const ui = uiOf(locale)
  return (
    <main className="mx-auto max-w-xl px-5 py-32 text-center">
      <p className="eyebrow text-brand">404</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight">{ui.notFoundTitle}</h1>
      <p className="mt-3 text-muted">{ui.notFoundText}</p>
      <Link href={switchHref('/', locale)} className="mt-8 inline-block rounded-xl bg-brand px-5 py-2.5 text-sm font-medium text-white">{ui.notFoundHome}</Link>
    </main>
  )
}
