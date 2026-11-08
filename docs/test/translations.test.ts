// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { describe, expect, it } from 'vitest'
import { CONTENT_DIR } from '@/lib/content'
import { type Locale, LOCALES, localeDir, pageHref, stripLocale } from '@/lib/i18n'
import { pages } from '@/lib/navigation'
import { SITE, compareOutlines, copiedSentences, foreignScripts, neutralTarget, outline, sentences } from '@/lib/translations'

const SOURCE = `---
title: 用户
description: 管理账号。
---

<!--
  License
-->

## 操作

| 操作 | 效果 |
| --- | --- |
| 禁用 | 无法登录 |

1. 打开 [用户](/guide/users/)
2. 看图 ![图](/images/a.png)

\`\`\`mermaid
flowchart LR
\`\`\`
`

/** The repository root, where the README files are. */
const REPOSITORY = join(CONTENT_DIR, '..', '..')

/** The language of every README, and the README every other one translates. */
const READMES: Record<string, Locale> = {
  'README.md': 'en', 'README.zh-CN.md': 'zh', 'README.zh-TW.md': 'zh-tw', 'README.ru.md': 'ru', 'README.ko.md': 'ko',
  'README.ja.md': 'ja', 'README.de.md': 'de', 'README.fr.md': 'fr', 'README.es.md': 'es', 'README.pt-BR.md': 'pt-br',
  'README.it.md': 'it',
}

function markdownFiles(dir: string): string[] {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? markdownFiles(path) : name.endsWith('.md') ? [path] : []
  })
}

function read(path: string): string {
  return readFileSync(path, 'utf8')
}

describe('translations', () => {
  it('reads the outline of a page without its front matter', () => {
    expect(outline(SOURCE)).toEqual({
      blocks: ['html', 'heading', 'table', 'list', 'code'],
      headings: ['h2'], code: ['mermaid'], tables: ['2x2'], lists: [2], links: ['/guide/users/', '/images/a.png'],
    })
  })

  it('compares links in every language and on the published site alike', () => {
    expect(neutralTarget('/en/guide/users/')).toBe('/guide/users/')
    expect(neutralTarget('/en/')).toBe('/')
    expect(neutralTarget('/guide/users/')).toBe('/guide/users/')
    expect(neutralTarget('https://grantforge.devlive.org/ja/plugins/yarn/')).toBe('/plugins/yarn/')
    expect(neutralTarget('https://grantforge.devlive.org')).toBe('/')
    expect(neutralTarget('https://example.com/en/x')).toBe('https://example.com/en/x')
    expect(neutralTarget('LICENSE')).toBe('LICENSE')
  })

  it('names the first difference of every part a translation dropped or added', () => {
    const translation = SOURCE.replace('| 禁用 | 无法登录 |\n', '').replace('## 操作', '## Actions\n\n### More')
      .replace('](/guide/users/)', '](/en/guide/users/)')
    expect(compareOutlines(outline(SOURCE), outline(translation))).toEqual([
      'blocks: 5 in the source, 6 here; first difference at #3 (source table, here heading)',
      'headings: 1 in the source, 2 here; first difference at #2 (source nothing, here h3)',
      'tables: 1 in the source, 1 here; first difference at #1 (source 2x2, here 1x2)',
    ])
    expect(compareOutlines(outline(SOURCE), outline(SOURCE))).toEqual([])
  })

  it('finds text in a script the language does not write', () => {
    expect(foreignScripts('Sign in\n`<编码>` here', 'en')).toEqual(['line 2: Chinese characters: `<编码>` here'])
    expect(foreignScripts('Привет 안녕 カナ', 'de')).toEqual(['line 1: Hangul, Kana, Cyrillic: Привет 안녕 カナ'])
    expect(foreignScripts('權限时', 'zh-tw')).toEqual(['line 1: simplified Chinese (时): 權限时'])
    expect(foreignScripts('権限を設定する時', 'ja')).toEqual([])
    expect(foreignScripts('権限时', 'ja')).toEqual(['line 1: simplified Chinese (时): 権限时'])
    expect(foreignScripts('권한 설정', 'ko')).toEqual([])
    expect(foreignScripts('Права доступа', 'ru')).toEqual([])
    expect(foreignScripts('设置权限', 'zh')).toEqual([])
    expect(foreignScripts('Language: [English](README.md) · [中文](README.zh-CN.md)', 'de')).toEqual([])
  })

  it('finds sentences copied from the English page', () => {
    const english = '---\ntitle: Users and their accounts here\ndescription: Short\n---\n\nThe account cannot sign in at all.\n\n'
      + '| Setting | Meaning |\n| --- | --- |\n| Timeout | How long the server waits for an answer |\n\n```\nsame code in every language here\n```\n'
    const german = english.replace('The account cannot sign in at all.', 'Das Konto kann sich gar nicht anmelden.')
    expect(sentences(english)).toEqual(new Set(['Users and their accounts here', 'The account cannot sign in at all',
      'How long the server waits for an answer']))
    expect(copiedSentences(german, sentences(english))).toEqual(['Users and their accounts here', 'How long the server waits for an answer'])
    expect(copiedSentences('Short words only, fine.', sentences('Short words only, fine.'))).toEqual([])
  })

  const sourceDir = join(CONTENT_DIR, 'zh')
  const sourcePages = markdownFiles(sourceDir).map(file => relative(sourceDir, file).replace(/\\/g, '/'))
  // What every language compares against, read once: the outline of each Chinese page and the English sentences.
  const shapes = new Map(sourcePages.map(page => [page, outline(read(join(sourceDir, page)))]))
  const english = new Map(sourcePages.map(page => {
    const file = join(CONTENT_DIR, 'en', page)
    return [page, existsSync(file) ? sentences(read(file)) : new Set<string>()]
  }))

  it('writes the Chinese source pages in Chinese', () => {
    const problems = sourcePages.flatMap(page => foreignScripts(read(join(sourceDir, page)), 'zh').map(problem => `zh/${page}: ${problem}`))
    expect(problems).toEqual([])
  })

  it.each(LOCALES.filter(locale => locale !== 'zh'))('translates every page completely into %s', locale => {
    const problems: string[] = []
    for (const page of sourcePages) {
      const name = `${localeDir(locale)}/${page}`
      const file = join(CONTENT_DIR, name)
      if (!existsSync(file)) {
        problems.push(`${name}: not translated`)
        continue
      }
      const translation = read(file)
      for (const problem of compareOutlines(shapes.get(page) ?? outline(''), outline(translation))) problems.push(`${name}: ${problem}`)
      for (const problem of foreignScripts(translation, locale)) problems.push(`${name}: ${problem}`)
      if (locale !== 'en') {
        for (const sentence of copiedSentences(translation, english.get(page) ?? new Set())) {
          problems.push(`${name}: copied from the English page: ${sentence.slice(0, 80)}`)
        }
      }
    }
    const dir = join(CONTENT_DIR, localeDir(locale))
    for (const file of markdownFiles(dir)) {
      const page = relative(dir, file).replace(/\\/g, '/')
      if (!sourcePages.includes(page)) problems.push(`${localeDir(locale)}/${page}: has no Chinese source page`)
    }
    expect(problems).toEqual([])
  })

  it('keeps every README a complete translation of the English one', () => {
    const problems: string[] = []
    const reference = read(join(REPOSITORY, 'README.md'))
    const shape = outline(reference)
    for (const [name, locale] of Object.entries(READMES)) {
      const file = join(REPOSITORY, name)
      if (!existsSync(file)) {
        problems.push(`${name}: missing`)
        continue
      }
      const readme = read(file)
      if (locale !== 'en') {
        for (const problem of compareOutlines(shape, outline(readme))) problems.push(`${name}: ${problem}`)
        for (const sentence of copiedSentences(readme, sentences(reference))) problems.push(`${name}: copied from README.md: ${sentence.slice(0, 80)}`)
      }
      for (const problem of foreignScripts(readme, locale)) problems.push(`${name}: ${problem}`)
      for (const [, url = ''] of readme.matchAll(/\]\((https:\/\/grantforge\.devlive\.org[^)\s]*)\)/g)) {
        const path = url.slice(SITE.length)
        if (path === '' || path === '/') continue
        const slug = stripLocale(path.slice(1)).replace(/\/$/, '')
        if (!pages.some(page => page.slug === slug)) problems.push(`${name}: ${url} is not a page of the documentation`)
        else if (url !== `${SITE}${pageHref(slug, locale)}`) problems.push(`${name}: ${url} should be ${SITE}${pageHref(slug, locale)}`)
      }
    }
    const others = readdirSync(REPOSITORY).filter(name => /^README.*\.md$/.test(name) && !(name in READMES))
    for (const name of others) problems.push(`${name}: a README in a language the documentation does not have`)
    expect(problems).toEqual([])
  })
})
