// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { plainText, readPage } from '@/lib/content'
import { pages } from '@/lib/navigation'

describe('content', () => {
  it('reads the front matter and drops the license header', () => {
    const dir = mkdtempSync(join(tmpdir(), 'docs-'))
    writeFileSync(join(dir, 'page.md'), '---\ntitle: 标题\ndescription: 说明\n---\n\n<!--\n  License\n-->\n\n正文 <!-- kept -->\n')
    expect(readPage('page', dir)).toEqual({ slug: 'page', title: '标题', description: '说明', body: '正文 <!-- kept -->\n', translated: true })
    expect(readPage('missing', dir)).toBeUndefined()
  })

  it('falls back to the Chinese file until a translation exists', () => {
    const dir = mkdtempSync(join(tmpdir(), 'docs-'))
    const license = '\n<!--\n  License\n-->\n\n'
    writeFileSync(join(dir, 'page.md'), `---\ntitle: 标题\ndescription: 说明\n---\n${license}正文\n`)
    expect(readPage('en/page', dir)).toEqual({ slug: 'en/page', title: '标题', description: '说明', body: '正文\n', translated: false })
    mkdirSync(join(dir, 'en'), { recursive: true })
    writeFileSync(join(dir, 'en', 'page.md'), `---\ntitle: Title\ndescription: Description\n---\n${license}Body\n`)
    expect(readPage('en/page', dir)).toEqual({ slug: 'en/page', title: 'Title', description: 'Description', body: 'Body\n', translated: true })
    expect(readPage('ru/page', dir)).toEqual({ slug: 'ru/page', title: '标题', description: '说明', body: '正文\n', translated: false })
    mkdirSync(join(dir, 'ru'), { recursive: true })
    writeFileSync(join(dir, 'ru', 'page.md'), `---\ntitle: Заголовок\ndescription: Описание\n---\n${license}Текст\n`)
    expect(readPage('ru/page', dir)).toEqual({ slug: 'ru/page', title: 'Заголовок', description: 'Описание', body: 'Текст\n', translated: true })
  })

  it('has a file with a title and a description for every page of the navigation', () => {
    for (const entry of pages) {
      const page = readPage(entry.slug)
      expect(page, entry.slug).toBeDefined()
      expect(page?.title, entry.slug).not.toBe(entry.slug)
      expect(page?.description, entry.slug).not.toBe('')
    }
  })

  it('keeps only the words for the search index', () => {
    expect(plainText('## 标题\n\n见 [角色](/guide/roles/)，![图](/a.png)\n\n```bash\nsecret\n```\n| a | b |')).toBe('标题 见 角色， a b')
  })
})
