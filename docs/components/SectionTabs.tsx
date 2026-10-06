// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { localeOf, pageHref, uiOf } from '@/lib/i18n'
import { sections, titleOf } from '@/lib/navigation'

/** The tabs of the header, one per part of the documentation, with the part of the current page marked. */
export default function SectionTabs() {
  const pathname = usePathname() ?? '/'
  const locale = localeOf(pathname)
  const ui = uiOf(locale)
  // '/en/guide/roles/' -> ['en', 'guide', 'roles']: the section id follows the locale prefix.
  const parts = pathname.split('/').filter(Boolean)
  const activeId = parts[locale === 'en' ? 1 : 0]
  return (
    <nav aria-label={ui.navAria} className="hidden items-center gap-1 lg:flex">
      {sections.map(section => {
        const active = section.id === activeId
        return (
          <Link key={section.id} href={pageHref(section.groups[0]?.pages[0]?.slug ?? '', locale)}
            aria-current={active ? 'page' : undefined} data-section={section.id}
            className={`rounded-lg px-3 py-2 text-sm transition ${active ? 'bg-brand-soft font-medium text-brand' : 'text-muted hover:bg-canvas hover:text-ink'}`}>
            {titleOf(section.title, section.en, locale)}
          </Link>
        )
      })}
    </nav>
  )
}
