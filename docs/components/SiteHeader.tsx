// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import Link from 'next/link'
import LanguageSwitch from './LanguageSwitch'
import SectionTabs from './SectionTabs'
import SearchButton from './SearchButton'
import ThemeToggle from './ThemeToggle'

/** The bar on every page: the logo, one tab per part of the documentation, the language, search and the theme. */
export default function SiteHeader() {
  return (
    <header className="sticky top-0 z-30 border-b border-line bg-surface/85 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-[90rem] items-center gap-6 px-5">
        <Link href="/" className="flex shrink-0 items-center gap-2.5 font-semibold tracking-tight">
          <img src="/images/grantforge-logo.png" alt="" className="size-8" />
          <span>GrantForge</span>
          <span className="rounded-md bg-brand-soft px-1.5 py-0.5 text-[11px] font-medium text-brand">Docs</span>
        </Link>
        <SectionTabs />
        <div className="ml-auto flex items-center gap-1">
          <LanguageSwitch />
          <SearchButton />
          <ThemeToggle />
          <a href="https://github.com/devlive-community/grantforge" target="_blank" rel="noreferrer" className="icon-button" aria-label="GitHub">
            <svg viewBox="0 0 24 24" className="size-5" fill="currentColor" aria-hidden="true"><path d="M12 .5a12 12 0 0 0-3.8 23.4c.6.1.8-.3.8-.6v-2.2c-3.3.7-4-1.4-4-1.4-.6-1.4-1.4-1.8-1.4-1.8-1.1-.7.1-.7.1-.7 1.2.1 1.9 1.2 1.9 1.2 1.1 1.8 2.8 1.3 3.5 1 .1-.8.4-1.3.8-1.6-2.7-.3-5.5-1.3-5.5-6a4.7 4.7 0 0 1 1.3-3.2 4.3 4.3 0 0 1 .1-3.2s1-.3 3.3 1.2a11.4 11.4 0 0 1 6 0C17.3 4.6 18.3 5 18.3 5a4.3 4.3 0 0 1 .1 3.2 4.7 4.7 0 0 1 1.3 3.2c0 4.6-2.8 5.6-5.5 5.9.4.4.8 1.1.8 2.2v3.3c0 .3.2.7.8.6A12 12 0 0 0 12 .5Z" /></svg>
          </a>
        </div>
      </div>
    </header>
  )
}
