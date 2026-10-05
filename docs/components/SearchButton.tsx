// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import MiniSearch from 'minisearch'
import { useCallback, useEffect, useRef, useState } from 'react'

interface Entry { id: string; title: string; section: string; text: string }

/** Searches every page by title and text, from an index built with the site; opened with ⌘K or /. */
export default function SearchButton() {
  const dialog = useRef<HTMLDialogElement>(null)
  const index = useRef<MiniSearch<Entry> | null>(null)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Entry[]>([])
  const open = useCallback(async () => {
    if (!index.current) {
      const entries = await fetch('/search.json').then(response => response.json() as Promise<Entry[]>)
      // Chinese has no spaces: index single characters and words alike.
      const tokenize = (text: string) => text.toLowerCase().match(/[a-z0-9_.-]+|[一-鿿]/g) ?? []
      const search = new MiniSearch<Entry>({ fields: ['title', 'text'], storeFields: ['title', 'section', 'text'], tokenize,
        searchOptions: { boost: { title: 3 }, prefix: true, combineWith: 'AND', tokenize } })
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
  function excerpt(text: string) {
    const at = text.toLowerCase().indexOf(query.trim().toLowerCase().split(/\s+/)[0] ?? '')
    return at < 0 ? text.slice(0, 90) : `${at > 30 ? '…' : ''}${text.slice(Math.max(0, at - 30), at + 70)}…`
  }
  return (
    <>
      <button type="button" onClick={() => void open()} className="hidden h-9 items-center gap-2 rounded-xl border border-line bg-canvas/60 px-3 text-sm text-muted transition hover:text-ink md:flex">
        <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
        搜索文档<kbd className="ml-6 rounded border border-line px-1.5 font-sans text-[11px]">⌘K</kbd>
      </button>
      <button type="button" onClick={() => void open()} className="icon-button md:hidden" aria-label="搜索文档">
        <svg viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
      </button>
      <dialog ref={dialog} aria-label="搜索文档" className="mx-auto mt-[12vh] w-[min(40rem,calc(100%-2rem))] rounded-2xl border border-line bg-surface p-0 text-ink shadow-2xl backdrop:bg-[#0a112b80] backdrop:backdrop-blur-sm" onClick={event => { if (event.target === dialog.current) dialog.current?.close() }}>
        <div className="border-b border-line p-3"><input autoFocus value={query} onChange={event => search(event.target.value)} placeholder="搜索标题与正文，例如：数据权限" className="w-full bg-transparent px-2 py-2 text-base outline-none" aria-label="搜索内容" /></div>
        <ul className="max-h-[60vh] overflow-y-auto p-2">
          {results.map(result => (
            <li key={result.id}><a href={`/${result.id}/`} className="block rounded-xl px-3 py-2.5 hover:bg-brand-soft" onClick={() => dialog.current?.close()}>
              <p className="text-sm font-medium">{result.title}<span className="ml-2 text-xs text-muted">{result.section}</span></p>
              <p className="mt-1 line-clamp-2 text-xs text-muted">{excerpt(result.text)}</p>
            </a></li>
          ))}
          {query.trim() && !results.length && <li className="px-3 py-6 text-center text-sm text-muted">没有找到相关内容</li>}
        </ul>
      </dialog>
    </>
  )
}
