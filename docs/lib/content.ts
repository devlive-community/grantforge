// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'
import matter from 'gray-matter'

/** Where the Markdown pages live. */
export const CONTENT_DIR = join(process.cwd(), 'content')

/** A page as read from its Markdown file. */
export interface Page {
  slug: string
  title: string
  description: string
  body: string
}

/**
 * Reads a page. Its front matter gives the title and description; the license header, an HTML comment right
 * below it, is not part of the text.
 */
export function readPage(slug: string, dir: string = CONTENT_DIR): Page | undefined {
  const file = join(dir, `${slug}.md`)
  if (!existsSync(file)) return undefined
  const { data, content } = matter(readFileSync(file, 'utf8'))
  const body = content.replace(/^\s*<!--[\s\S]*?-->\s*/, '')
  return { slug, title: String(data.title ?? slug), description: String(data.description ?? ''), body }
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
