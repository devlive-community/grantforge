// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import Link from 'next/link'
import type { NavSection } from '@/lib/navigation'

/** The pages of one part of the documentation, with the current one marked. */
export default function Sidebar({ section, current }: { section: NavSection; current: string }) {
  return (
    <nav aria-label={section.title} className="space-y-7 text-sm">
      {section.groups.map(group => (
        <div key={group.title}>
          <p className="eyebrow mb-2 px-3 text-muted">{group.title}</p>
          {/* Pages hang off a rail below their group title so the two levels read as a hierarchy. */}
          <ul className="ml-4 space-y-0.5 border-l border-line pl-2">
            {group.pages.map(page => (
              <li key={page.slug}>
                <Link href={`/${page.slug}/`} aria-current={page.slug === current ? 'page' : undefined}
                  className={`block rounded-lg px-3 py-1.5 transition ${page.slug === current ? 'bg-brand-soft font-medium text-brand' : 'text-muted hover:bg-canvas hover:text-ink'}`}>
                  {page.title}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  )
}
