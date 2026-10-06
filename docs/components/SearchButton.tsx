// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import MiniSearch from 'minisearch'
import { useCallback, useEffect, useRef, useState } from 'react'
import { usePathname } from 'next/navigation'
import { localeOf, pageHref, uiOf } from '@/lib/i18n'
import { titleOf } from '@/lib/navigation'
import { searchTokens } from '@/lib/search-index'

interface Entry {
  id: string
  title: string
  titleTw: string
  titleEn: string
  titleRu: string
  titleKo: string
  titleJa: string
  titleDe: string
  titleFr: string
  titleEs: string
  section: string
  sectionTw: string
  sectionEn: string
  sectionRu: string
  sectionKo: string
  sectionJa: string
  sectionDe: string
  sectionFr: string
  sectionEs: string
  text: string
  textTw: string
  textEn: string
  textRu: string
  textKo: string
  textJa: string
  textDe: string
  textFr: string
  textEs: string
}

/** Searches every page by title and text, from an index built with the site; opened with ⌘K or /. */
export default function SearchButton() {
  const pathname = usePathname() ?? '/'
  const locale = localeOf(pathname)
  const ui = uiOf(locale)
  const dialog = useRef<HTMLDialogElement>(null)
  const index = useRef<MiniSearch<Entry> | null>(null)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Entry[]>([])
  const open = useCallback(async () => {
    if (!index.current) {
      const entries = await fetch('/search.json').then(response => response.json() as Promise<Entry[]>)
      const search = new MiniSearch<Entry>({
        fields: ['title', 'titleTw', 'titleEn', 'titleRu', 'titleKo', 'titleJa', 'titleDe', 'titleFr', 'titleEs', 'text', 'textTw', 'textEn', 'textRu', 'textKo', 'textJa', 'textDe', 'textFr', 'textEs'],
        storeFields: Object.keys(entries[0] ?? { id: '' }),
        tokenize: searchTokens,
        searchOptions: { boost: { title: 3, titleTw: 3, titleEn: 3, titleRu: 3, titleKo: 3, titleJa: 3, titleDe: 3, titleFr: 3, titleEs: 3 }, prefix: true, combineWith: 'AND', tokenize: searchTokens },
      })
      search.addAll(entries)
      index.current = search
    }
    dialog.current?.showModal()
  }, [])
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const typing = event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement
      if ((event.key === 'k' && (event.metaKey || event.ctrlKey)) || (event.key === '/' && !typing)) { event.preventDefault(); void open() }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])
  function search(value: string) {
    setQuery(value)
    setResults(value.trim() && index.current ? index.current.search(value).slice(0, 12) as unknown as Entry[] : [])
  }
  function excerpt(entry: Entry) {
    const text = locale === 'ko' ? entry.textKo || entry.text
      : locale === 'ru' ? entry.textRu || entry.text
      : locale === 'zh-tw' ? entry.textTw || entry.text
      : locale === 'en' ? entry.textEn || entry.text
      : locale === 'ja' ? entry.textJa || entry.text
      : locale === 'de' ? entry.textDe || entry.text
      : locale === 'fr' ? entry.textFr || entry.text
      : locale === 'es' ? entry.textEs || entry.text
      : entry.text
    const at = text.toLowerCase().indexOf(query.trim().toLowerCase().split(/\s+/)[0] ?? '')
    return at < 0 ? text.slice(0, 90) : `${at > 30 ? '…' : ''}${text.slice(Math.max(0, at - 30), at + 70)}…`
  }
  return (
    <>
      <button type="button" onClick={() => void open()} className="hidden h-9 items-center gap-2 rounded-xl border border-line bg-canvas/60 px-3 text-sm text-muted transition hover:text-ink md:flex">
        <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
        {ui.search}<kbd className="ml-6 rounded border border-line px-1.5 font-sans text-[11px]">⌘K</kbd>
      </button>
      <button type="button" onClick={() => void open()} className="icon-button md:hidden" aria-label={ui.search}>
        <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
      </button>
      <dialog ref={dialog} aria-label={ui.search} className="mx-auto mt-[12vh] w-[min(40rem,calc(100%-2rem))] rounded-2xl border border-line bg-surface p-0 text-ink shadow-2xl backdrop:bg-[#0a112b80] backdrop:backdrop-blur-sm" onClick={event => { if (event.target === dialog.current) dialog.current?.close() }}>
        <div className="border-b border-line p-3"><input autoFocus value={query} onChange={event => search(event.target.value)} placeholder={ui.searchPlaceholder} className="w-full bg-transparent px-2 py-2 text-base outline-none" aria-label={ui.searchAria} /></div>
        <ul className="max-h-[60vh] overflow-y-auto p-2">
          {results.map(result => {
            // The index leaves a field empty until the page is translated, and the label then falls back.
            const page = { title: result.title, en: result.titleEn || result.title, ru: result.titleRu || undefined, tw: result.titleTw || undefined, ko: result.titleKo || undefined, ja: result.titleJa || undefined, de: result.titleDe || undefined, fr: result.titleFr || undefined, es: result.titleEs || undefined }
            const part = { title: result.section, en: result.sectionEn || result.section, ru: result.sectionRu || undefined, tw: result.sectionTw || undefined, ko: result.sectionKo || undefined, ja: result.sectionJa || undefined, de: result.sectionDe || undefined, fr: result.sectionFr || undefined, es: result.sectionEs || undefined }
            return (
              <li key={result.id}><a href={pageHref(result.id, locale)} className="block rounded-xl px-3 py-2.5 hover:bg-brand-soft" onClick={() => dialog.current?.close()}>
                <p className="text-sm font-medium">{titleOf(page, locale)}<span className="ml-2 text-xs text-muted">{titleOf(part, locale)}</span></p>
                <p className="mt-1 line-clamp-2 text-xs text-muted">{excerpt(result)}</p>
              </a></li>
            )
          })}
          {query.trim() && !results.length && <li className="px-3 py-6 text-center text-sm text-muted">{ui.noResults}</li>}
        </ul>
      </dialog>
    </>
  )
}
