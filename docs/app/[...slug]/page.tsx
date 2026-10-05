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
import { render, tableOfContents } from '@/lib/markdown'
import { neighbours, pages, sectionOf } from '@/lib/navigation'
import { expandGenerated } from '@/lib/reference'

interface Props { params: Promise<{ slug: string[] }> }

const EDIT = 'https://github.com/devlive-community/grantforge/edit/dev/docs/content'

export function generateStaticParams() {
  // Pages without a file are reported by `pnpm check`; the build leaves them out.
  return pages.filter(page => readPage(page.slug)).map(page => ({ slug: page.slug.split('/') }))
}

export const dynamicParams = false

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const page = readPage((await params).slug.join('/'))
  return page ? { title: page.title, description: page.description } : {}
}

export default async function DocPage({ params }: Props) {
  const slug = (await params).slug.join('/')
  const page = readPage(slug)
  const section = sectionOf(slug)
  if (!page || !section) notFound()
  const markdown = expandGenerated(page.body)
  const html = await render(markdown)
  const toc = tableOfContents(markdown)
  const { previous, next } = neighbours(slug)
  return (
    <div className="mx-auto flex max-w-[90rem] gap-10 px-5">
      <aside className="sticky top-16 hidden h-[calc(100dvh-4rem)] w-60 shrink-0 overflow-y-auto py-8 lg:block"><Sidebar section={section} current={slug} /></aside>
      <main className="min-w-0 flex-1 py-10">
        <p className="eyebrow mb-3 text-brand">{section.title}</p>
        <h1 className="text-3xl font-semibold tracking-tight">{page.title}</h1>
        {page.description && <p className="mt-3 text-base leading-7 text-muted">{page.description}</p>}
        <article className="prose mt-8" dangerouslySetInnerHTML={{ __html: html }} />
        <DocEnhancer />
        <div className="mt-14 flex flex-wrap justify-between gap-4 border-t border-line pt-6 text-sm">
          {previous ? <Link href={`/${previous.slug}/`} className="panel px-4 py-3 hover:border-brand"><span className="block text-xs text-muted">上一篇</span>{previous.title}</Link> : <span />}
          {next ? <Link href={`/${next.slug}/`} className="panel px-4 py-3 text-right hover:border-brand"><span className="block text-xs text-muted">下一篇</span>{next.title}</Link> : <span />}
        </div>
        <p className="mt-8 text-xs text-muted"><a href={`${EDIT}/${slug}.md`} target="_blank" rel="noreferrer" className="hover:text-brand">在 GitHub 上编辑此页</a></p>
      </main>
      {toc.length > 1 && (
        <aside className="sticky top-16 hidden h-[calc(100dvh-4rem)] w-56 shrink-0 overflow-y-auto py-10 xl:block">
          <p className="eyebrow mb-3 text-muted">本页目录</p>
          <ul className="space-y-1.5 border-l border-line text-[13px]">
            {toc.map(entry => <li key={entry.id}><a href={`#${entry.id}`} className={`-ml-px block border-l border-transparent text-muted hover:border-brand hover:text-ink ${entry.depth === 3 ? 'pl-6' : 'pl-3'}`}>{entry.text}</a></li>)}
          </ul>
        </aside>
      )}
    </div>
  )
}
