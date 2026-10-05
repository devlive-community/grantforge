// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { plainText, readPage } from '@/lib/content'
import { pages, sectionOf } from '@/lib/navigation'
import { expandGenerated } from '@/lib/reference'

export const dynamic = 'force-static'

/** The search index, written to search.json when the site is built. */
export function GET() {
  const entries = pages.flatMap(entry => {
    const page = readPage(entry.slug)
    return page ? [{ id: entry.slug, title: page.title, section: sectionOf(entry.slug)?.title ?? '', text: plainText(`${page.description} ${expandGenerated(page.body)}`) }] : []
  })
  return Response.json(entries)
}
