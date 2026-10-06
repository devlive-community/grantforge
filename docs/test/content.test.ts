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
    expect(readPage('zh-tw/page', dir)).toEqual({ slug: 'zh-tw/page', title: '标题', description: '说明', body: '正文\n', translated: false })
    mkdirSync(join(dir, 'zh-tw'), { recursive: true })
    writeFileSync(join(dir, 'zh-tw', 'page.md'), `---\ntitle: 標題\ndescription: 說明\n---\n${license}內文\n`)
    expect(readPage('zh-tw/page', dir)).toEqual({ slug: 'zh-tw/page', title: '標題', description: '說明', body: '內文\n', translated: true })
    expect(readPage('en/page', dir)).toEqual({ slug: 'en/page', title: '标题', description: '说明', body: '正文\n', translated: false })
    mkdirSync(join(dir, 'en'), { recursive: true })
    writeFileSync(join(dir, 'en', 'page.md'), `---\ntitle: Title\ndescription: Description\n---\n${license}Body\n`)
    expect(readPage('en/page', dir)).toEqual({ slug: 'en/page', title: 'Title', description: 'Description', body: 'Body\n', translated: true })
    expect(readPage('ru/page', dir)).toEqual({ slug: 'ru/page', title: '标题', description: '说明', body: '正文\n', translated: false })
    mkdirSync(join(dir, 'ru'), { recursive: true })
    writeFileSync(join(dir, 'ru', 'page.md'), `---\ntitle: Заголовок\ndescription: Описание\n---\n${license}Текст\n`)
    expect(readPage('ru/page', dir)).toEqual({ slug: 'ru/page', title: 'Заголовок', description: 'Описание', body: 'Текст\n', translated: true })
    expect(readPage('ko/page', dir)).toEqual({ slug: 'ko/page', title: '标题', description: '说明', body: '正文\n', translated: false })
    mkdirSync(join(dir, 'ko'), { recursive: true })
    writeFileSync(join(dir, 'ko', 'page.md'), `---\ntitle: 제목\ndescription: 설명\n---\n${license}본문\n`)
    expect(readPage('ko/page', dir)).toEqual({ slug: 'ko/page', title: '제목', description: '설명', body: '본문\n', translated: true })
  })

  it('has a file with a title and a description for every page of the navigation, in Chinese and in translation', () => {
    for (const entry of pages) {
      const page = readPage(entry.slug)
      expect(page, entry.slug).toBeDefined()
      expect(page?.title, entry.slug).not.toBe(entry.slug)
      expect(page?.description, entry.slug).not.toBe('')
      const traditional = readPage(`zh-tw/${entry.slug}`)
      expect(traditional, `zh-tw/${entry.slug}`).toBeDefined()
      expect(traditional?.translated, `zh-tw/${entry.slug}`).toBe(true)
      expect(traditional?.title, `zh-tw/${entry.slug}`).not.toBe(entry.slug)
      const korean = readPage(`ko/${entry.slug}`)
      expect(korean, `ko/${entry.slug}`).toBeDefined()
      expect(korean?.translated, `ko/${entry.slug}`).toBe(true)
      expect(korean?.title, `ko/${entry.slug}`).not.toBe(entry.slug)
    }
  })

  it('keeps only the words for the search index', () => {
    expect(plainText('## 标题\n\n见 [角色](/guide/roles/)，![图](/a.png)\n\n```bash\nsecret\n```\n| a | b |')).toBe('标题 见 角色， a b')
  })
})
