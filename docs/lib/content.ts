// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import matter from 'gray-matter'
import { localeOf, localePrefix, stripLocale } from '@/lib/i18n'

/** Where the Markdown pages live. Translated pages live in content/<prefix> and fall back to the Chinese file. */
export const CONTENT_DIR = join(process.cwd(), 'content')

/** A page as read from its Markdown file. */
export interface Page {
  slug: string
  title: string
  description: string
  body: string
  /** False when a translated page shows the Chinese original because its translation does not exist yet. */
  translated: boolean
}

/**
 * Reads a page. Its front matter gives the title and description; the license header, an HTML comment right
 * below it, is not part of the text. A page without a translation falls back to the Chinese file.
 */
export function readPage(slug: string, dir: string = CONTENT_DIR): Page | undefined {
  const key = stripLocale(slug)
  const locale = localeOf(slug)
  const localized = join(dir, localePrefix(locale), `${key}.md`)
  const file = existsSync(localized) ? localized : locale === 'zh' ? localized : join(dir, `${key}.md`)
  if (!existsSync(file)) return undefined
  const { data, content } = matter(readFileSync(file, 'utf8'))
  const body = content.replace(/^\s*<!--[\s\S]*?-->\s*/, '')
  return {
    slug,
    title: String(data.title ?? key),
    description: String(data.description ?? ''),
    body,
    translated: locale === 'zh' || file === localized,
  }
}

/** Plain text of a page for the search index: no code fences, markup or link targets. */
export function plainText(markdown: string): string {
  return markdown
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/[#>*_`|-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}
