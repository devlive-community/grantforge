// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import Link from 'next/link'
import { localeOf, pageHref, stripLocale } from '@/lib/i18n'
import type { NavSection } from '@/lib/navigation'
import { titleOf } from '@/lib/navigation'

/**
 * The sidebar of one part of the documentation: every section it holds, each with its groups, in the language
 * of the current page and with that page marked.
 */
export default function Sidebar({ sections, current, label }: { sections: NavSection[]; current: string; label: string }) {
  const locale = localeOf(current)
  return (
    <nav aria-label={label} className="space-y-8 text-sm">
      {sections.map(section => (
        <div key={section.id}>
          {/* A part with several sections names them, so its pages still show where they sit. */}
          {sections.length > 1 && (
            <p className="mb-3 px-3 text-[13px] font-semibold text-ink">{titleOf(section.title, section.en, locale, section.ru)}</p>
          )}
          <div className="space-y-6">
            {section.groups.map(group => (
              <div key={group.title}>
                <p className="eyebrow mb-2 px-3 text-muted">{titleOf(group.title, group.en, locale, group.ru)}</p>
                {/* Pages hang off a rail below their group title so the two levels read as a hierarchy. */}
                <ul className="ml-4 space-y-0.5 border-l border-line pl-2">
                  {group.pages.map(page => (
                    <li key={page.slug}>
                      <Link href={pageHref(page.slug, locale)} aria-current={page.slug === stripLocale(current) ? 'page' : undefined}
                        className={`block rounded-lg px-3 py-1.5 transition ${page.slug === stripLocale(current) ? 'bg-brand-soft font-medium text-brand' : 'text-muted hover:bg-canvas hover:text-ink'}`}>
                        {titleOf(page.title, page.en, locale, page.ru)}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>
      ))}
    </nav>
  )
}
