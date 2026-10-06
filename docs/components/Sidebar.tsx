// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import Link from 'next/link'
import { localeOf, pageHref, stripLocale } from '@/lib/i18n'
import type { NavSection } from '@/lib/navigation'
import { titleOf } from '@/lib/navigation'

/** The pages of one part of the documentation, in the language of the current page, with it marked. */
export default function Sidebar({ section, current }: { section: NavSection; current: string }) {
  const locale = localeOf(current)
  return (
    <nav aria-label={titleOf(section.title, section.en, locale)} className="space-y-7 text-sm">
      {section.groups.map(group => (
        <div key={group.title}>
          <p className="eyebrow mb-2 px-3 text-muted">{titleOf(group.title, group.en, locale)}</p>
          {/* Pages hang off a rail below their group title so the two levels read as a hierarchy. */}
          <ul className="ml-4 space-y-0.5 border-l border-line pl-2">
            {group.pages.map(page => (
              <li key={page.slug}>
                <Link href={pageHref(page.slug, locale)} aria-current={page.slug === stripLocale(current) ? 'page' : undefined}
                  className={`block rounded-lg px-3 py-1.5 transition ${page.slug === stripLocale(current) ? 'bg-brand-soft font-medium text-brand' : 'text-muted hover:bg-canvas hover:text-ink'}`}>
                  {titleOf(page.title, page.en, locale)}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  )
}
