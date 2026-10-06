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
    const traditional = readPage(`zh-tw/${entry.slug}`)
    const english = readPage(`en/${entry.slug}`)
    const russian = readPage(`ru/${entry.slug}`)
    const korean = readPage(`ko/${entry.slug}`)
    const japanese = readPage(`ja/${entry.slug}`)
    const german = readPage(`de/${entry.slug}`)
    const french = readPage(`fr/${entry.slug}`)
    const spanish = readPage(`es/${entry.slug}`)
    const section = sectionOf(entry.slug)
    return [{
      id: entry.slug,
      title: page.title,
      titleTw: traditional?.translated ? traditional.title : '',
      titleEn: english?.translated ? english.title : page.title,
      titleRu: russian?.translated ? russian.title : '',
      titleKo: korean?.translated ? korean.title : '',
      titleJa: japanese?.translated ? japanese.title : '',
      titleDe: german?.translated ? german.title : '',
      titleFr: french?.translated ? french.title : '',
      titleEs: spanish?.translated ? spanish.title : '',
      section: section?.title ?? '',
      sectionTw: section?.tw ?? '',
      sectionEn: section ? (section.en ?? section.title) : '',
      sectionRu: section?.ru ?? '',
      sectionKo: section?.ko ?? '',
      sectionJa: section?.ja ?? '',
      sectionDe: section?.de ?? '',
      sectionFr: section?.fr ?? '',
      sectionEs: section?.es ?? '',
      text: plainText(`${page.description} ${expandGenerated(page.body)}`),
      textTw: traditional?.translated ? plainText(`${traditional.description} ${expandGenerated(traditional.body)}`) : '',
      textEn: english?.translated ? plainText(`${english.description} ${expandGenerated(english.body)}`) : '',
      textRu: russian?.translated ? plainText(`${russian.description} ${expandGenerated(russian.body)}`) : '',
      textKo: korean?.translated ? plainText(`${korean.description} ${expandGenerated(korean.body)}`) : '',
      textJa: japanese?.translated ? plainText(`${japanese.description} ${expandGenerated(japanese.body)}`) : '',
      textDe: german?.translated ? plainText(`${german.description} ${expandGenerated(german.body)}`) : '',
      textFr: french?.translated ? plainText(`${french.description} ${expandGenerated(french.body)}`) : '',
      textEs: spanish?.translated ? plainText(`${spanish.description} ${expandGenerated(spanish.body)}`) : '',
    }]
  })
  return Response.json(entries)
}
