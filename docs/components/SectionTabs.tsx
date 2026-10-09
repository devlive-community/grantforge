// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { localeOf, pageHref, uiOf } from '@/lib/i18n'
import { categories, categoryOf, titleOf } from '@/lib/navigation'

/** The tabs of the header, one per part of the documentation, with the part of the current page marked. */
export default function SectionTabs({ className = 'hidden min-w-0 flex-1 items-center gap-1 overflow-x-auto lg:flex' }: { className?: string }) {
  const pathname = usePathname() ?? '/'
  const locale = localeOf(pathname)
  const ui = uiOf(locale)
  const active = categoryOf(pathname)
  return (
    <nav aria-label={ui.navAria} className={className}>
      {categories.map(category => {
        const current = category.id === active?.id
        return (
          <Link key={category.id} href={pageHref(category.sections[0]?.groups[0]?.pages[0]?.slug ?? '', locale)}
            aria-current={current ? 'page' : undefined} data-section={category.id}
            className={`shrink-0 whitespace-nowrap rounded-lg px-3 py-2 text-sm transition ${current ? 'bg-brand-soft font-medium text-brand' : 'text-muted hover:bg-canvas hover:text-ink'}`}>
            {titleOf(category, locale)}
          </Link>
        )
      })}
    </nav>
  )
}
