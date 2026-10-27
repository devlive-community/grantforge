// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import Link from 'next/link'
import type { ReactNode } from 'react'
import { localeOf, pageHref, stripLocale } from '@/lib/i18n'
import type { Locale } from '@/lib/i18n'
import type { NavGroup, NavPage, NavSection } from '@/lib/navigation'
import { holds, titleOf } from '@/lib/navigation'

/** Points right when its branch is folded and down when it is open. */
function Chevron() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true"
      className="size-3.5 shrink-0 text-muted transition-transform [details[open]>summary>&]:rotate-90">
      <path d="M6 4l4 4-4 4" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** The summary row of a branch, without the browser's own marker. */
const SUMMARY = 'flex cursor-pointer list-none items-center gap-1.5 rounded-md px-2 py-1 transition select-none hover:bg-canvas [&::-webkit-details-marker]:hidden'

/** What lies below a branch hangs off a rail under its chevron, so every level reads as part of the one above. */
function Rail({ children }: { children: ReactNode }) {
  return <div className="ml-[15px] space-y-px border-l border-line pl-1.5">{children}</div>
}

/** A branch that folds: its row, then what lies below it on a rail. */
function Branch({ open, label, className, children }: { open: boolean; label: string; className: string; children: ReactNode }) {
  return (
    <details open={open}>
      <summary className={`${SUMMARY} ${className}`}>
        <Chevron />
        <span className="min-w-0">{label}</span>
      </summary>
      <Rail>{children}</Rail>
    </details>
  )
}

function Pages({ pages, current, locale }: { pages: NavPage[]; current: string; locale: Locale }) {
  return (
    <ul className="space-y-px">
      {pages.map(page => {
        const here = page.slug === current
        return (
          <li key={page.slug}>
            <Link href={pageHref(page.slug, locale)} aria-current={here ? 'page' : undefined}
              className={`block rounded-md px-2.5 py-1 transition ${here ? 'bg-brand-soft font-medium text-brand' : 'text-muted hover:bg-canvas hover:text-ink'}`}>
              {titleOf(page.short ?? page, locale)}
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

/**
 * A group and everything below it. A heading stays open until the reader folds it; a catalog entry, such as one
 * system among the plug-ins, opens only when it holds the current page.
 */
function Group({ group, section, current, locale }: { group: NavGroup; section: string; current: string; locale: Locale }) {
  const inside = holds(group, current)
  return (
    <Branch open={group.folded ? inside : true} label={titleOf(group, locale)}
      className={group.folded ? (inside ? 'text-ink' : 'text-muted hover:text-ink') : 'font-medium text-ink/85'}>
      {group.pages.length > 0 && <Pages pages={group.pages} current={current} locale={locale} />}
      {group.groups?.map(child => (
        // Keyed by the English name, not the Chinese one: keys travel in the shipped payload, so a translated
        // page must not carry the source language inside them.
        <Group key={`${section}/${child.en}`} group={child} section={section} current={current} locale={locale} />
      ))}
    </Branch>
  )
}

/**
 * The sidebar of one part of the documentation: every section it holds as a tree that folds at each level, in the
 * language of the current page, with the page marked and the branches above it open.
 */
export default function Sidebar({ sections, current, label }: { sections: NavSection[]; current: string; label: string }) {
  const locale = localeOf(current)
  const page = stripLocale(current).replace(/^\/+|\/+$/g, '')
  const groups = (section: NavSection) => section.groups.map(group => (
    <Group key={`${section.id}/${group.en}`} group={group} section={section.id} current={page} locale={locale} />
  ))
  return (
    <nav aria-label={label} className="space-y-2 text-sm">
      {sections.map(section => section.groups.length === 0 ? (
        // A section of pages alone, such as an overview, opens the sidebar without a branch of its own.
        <Pages key={section.id} pages={section.pages ?? []} current={page} locale={locale} />
      ) : sections.length > 1 ? (
        // A part with several sections names them, so its pages still show where they sit.
        <Branch key={section.id} open label={titleOf(section, locale)} className="font-semibold text-ink">
          {groups(section)}
        </Branch>
      ) : <div key={section.id} className="space-y-px">{groups(section)}</div>)}
    </nav>
  )
}
