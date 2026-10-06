// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import DocEnhancer from '@/components/DocEnhancer'
import Sidebar from '@/components/Sidebar'
import { readPage } from '@/lib/content'
import { localeOf, localePrefix, pageHref, stripLocale, uiOf } from '@/lib/i18n'
import { render, tableOfContents } from '@/lib/markdown'
import { categoryOf, neighbours, pages, sectionOf, titleOf } from '@/lib/navigation'
import { expandGenerated } from '@/lib/reference'

interface Props { params: Promise<{ slug: string[] }> }

const EDIT = 'https://github.com/devlive-community/grantforge/edit/dev/docs/content'

export function generateStaticParams() {
  // Pages without a file are reported by `pnpm check`; the build leaves them out. Every page also exists in
  // Traditional Chinese, English, Russian, Korean and Japanese, where a missing translation falls back to the Chinese original.
  return pages.flatMap(page => readPage(page.slug)
    ? [{ slug: page.slug.split('/') }, { slug: ['zh-tw', ...page.slug.split('/')] }, { slug: ['en', ...page.slug.split('/')] }, { slug: ['ru', ...page.slug.split('/')] }, { slug: ['ko', ...page.slug.split('/')] }, { slug: ['ja', ...page.slug.split('/')] }]
    : [])
}

export const dynamicParams = false

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const slug = (await params).slug.join('/')
  const page = readPage(slug)
  if (!page) return {}
  const locale = localeOf(slug)
  return locale !== 'zh'
    ? { title: { absolute: `${page.title} · GrantForge Docs` }, description: page.description }
    : { title: page.title, description: page.description }
}

export default async function DocPage({ params }: Props) {
  const slug = (await params).slug.join('/')
  const page = readPage(slug)
  const section = sectionOf(slug)
  const category = categoryOf(slug)
  if (!page || !section || !category) notFound()
  const locale = localeOf(slug)
  const ui = uiOf(locale)
  const markdown = expandGenerated(page.body)
  const html = await render(markdown)
  const toc = tableOfContents(markdown)
  const { previous, next } = neighbours(slug)
  return (
    <div className="mx-auto flex max-w-[90rem] gap-10 px-5">
      <aside className="sticky top-16 hidden h-[calc(100dvh-4rem)] w-60 shrink-0 overflow-y-auto py-8 lg:block"><Sidebar sections={category.sections} current={slug} label={titleOf(category, locale)} /></aside>
      <main className="min-w-0 flex-1 py-10">
        <p className="eyebrow mb-3 text-brand">{titleOf(section, locale)}</p>
        <h1 className="text-3xl font-semibold tracking-tight">{page.title}</h1>
        {page.description && <p className="mt-3 text-base leading-7 text-muted">{page.description}</p>}
        {!page.translated && (
          <p className="mt-6 rounded-xl border border-amber-400/30 bg-amber-400/5 px-4 py-3 text-sm text-muted">{ui.untranslated}</p>
        )}
        <article className="prose mt-8" dangerouslySetInnerHTML={{ __html: html }} />
        <DocEnhancer />
        <div className="mt-14 flex flex-wrap justify-between gap-4 border-t border-line pt-6 text-sm">
          {previous ? (
            <Link href={pageHref(previous.slug, locale)} className="panel px-4 py-3 hover:border-brand">
              <span className="block text-xs text-muted">{ui.previous}</span>{titleOf(previous, locale)}
            </Link>
          ) : <span />}
          {next ? (
            <Link href={pageHref(next.slug, locale)} className="panel px-4 py-3 text-right hover:border-brand">
              <span className="block text-xs text-muted">{ui.next}</span>{titleOf(next, locale)}
            </Link>
          ) : <span />}
        </div>
        <p className="mt-8 text-xs text-muted">
          <a href={`${EDIT}/${page.translated && locale !== 'zh' ? `${localePrefix(locale)}/` : ''}${stripLocale(slug)}.md`} target="_blank" rel="noreferrer" className="hover:text-brand">{ui.editPage}</a>
        </p>
      </main>
      {toc.length > 1 && (
        <aside className="sticky top-16 hidden h-[calc(100dvh-4rem)] w-56 shrink-0 overflow-y-auto py-10 xl:block">
          <p className="eyebrow mb-3 text-muted">{ui.onThisPage}</p>
          <ul className="space-y-1.5 border-l border-line text-[13px]">
            {toc.map(entry => <li key={entry.id}><a href={`#${entry.id}`} className={`-ml-px block border-l border-transparent text-muted hover:border-brand hover:text-ink ${entry.depth === 3 ? 'pl-6' : 'pl-3'}`}>{entry.text}</a></li>)}
          </ul>
        </aside>
      )}
    </div>
  )
}
