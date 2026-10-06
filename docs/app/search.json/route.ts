// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { plainText, readPage } from '@/lib/content'
import { pages, sectionOf } from '@/lib/navigation'
import { expandGenerated } from '@/lib/reference'

export const dynamic = 'force-static'

/** The search index, written to search.json when the site is built, with every language in one file. */
export function GET() {
  const entries = pages.flatMap(entry => {
    const page = readPage(entry.slug)
    if (!page) return []
    const english = readPage(`en/${entry.slug}`)
    const section = sectionOf(entry.slug)
    return [{
      id: entry.slug,
      title: page.title,
      titleEn: english?.translated ? english.title : page.title,
      section: section?.title ?? '',
      sectionEn: section ? (section.en ?? section.title) : '',
      text: plainText(`${page.description} ${expandGenerated(page.body)}`),
      textEn: english?.translated ? plainText(`${english.description} ${expandGenerated(english.body)}`) : '',
    }]
  })
  return Response.json(entries)
}
