// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import { usePathname } from 'next/navigation'
import { localeOf, uiOf } from '@/lib/i18n'

/** Switches between the light and the dark theme and remembers the choice. */
export default function ThemeToggle() {
  const pathname = usePathname() ?? '/'
  const ui = uiOf(localeOf(pathname))
  function toggle() {
    const dark = document.documentElement.classList.toggle('dark')
    try { localStorage.setItem('GrantForgeDocsTheme', dark ? 'dark' : 'light') } catch { /* private browsing */ }
  }
  return (
    <button type="button" className="icon-button" aria-label={ui.theme} onClick={toggle}>
      <svg viewBox="0 0 24 24" className="size-5 dark:hidden" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" /></svg>
      <svg viewBox="0 0 24 24" className="hidden size-5 dark:block" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
    </button>
  )
}
