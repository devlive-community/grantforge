// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import matter from 'gray-matter'
import type { Nodes, Root } from 'mdast'
import remarkGfm from 'remark-gfm'
import remarkParse from 'remark-parse'
import { unified } from 'unified'
import { visit } from 'unist-util-visit'
import { type Locale, stripLocale } from '@/lib/i18n'

/**
 * What a translation must keep of its source page: the parts a reader navigates by or copies, which a translator
 * has no reason to add, drop or reorder.
 */
export interface Outline {
  /** The kind of every top-level block in page order ('paragraph', 'list'), so no paragraph is dropped or added. */
  blocks: string[]
  /** Heading levels in page order ('h2'). */
  headings: string[]
  /** The language of every fenced block in page order ('mermaid', '' when none is given). */
  code: string[]
  /** Every table as rows x columns, the header row included. */
  tables: string[]
  /** The number of items of every list, in page order. */
  lists: number[]
  /** Link and image targets in page order, without the language prefix and the site's own origin. */
  links: string[]
}

/** Where the published site lives; README links to it count as internal ones. */
export const SITE = 'https://grantforge.devlive.org'

/**
 * A link to a README is part of a README's language switcher, which links every language but its own and so
 * differs in every translation by design.
 */
const SWITCHER_TARGET = /^README[\w.-]*\.md$/

const parser = unified().use(remarkParse).use(remarkGfm)

/** The target of a link with the language prefix (and the site's origin) removed, so all languages compare equal. */
export function neutralTarget(url: string): string {
  const local = url.startsWith(SITE) ? url.slice(SITE.length) || '/' : url
  return local.startsWith('/') ? `/${stripLocale(local.slice(1))}` : local
}

/** A page split into its front matter and the syntax tree of its body. */
interface Parsed {
  data: Record<string, unknown>
  tree: Root
}

/** The last pages parsed: a check reads each page's outline and sentences, and each English page once per language. */
const recent = new Map<string, Parsed>()

function parse(markdown: string): Parsed {
  const known = recent.get(markdown)
  if (known) return known
  const { data, content } = matter(markdown)
  const parsed = { data, tree: parser.parse(content) as Root }
  recent.set(markdown, parsed)
  if (recent.size > 8) recent.delete(recent.keys().next().value ?? '')
  return parsed
}

/** Reads the outline of a page (front matter and comments are not part of it). */
export function outline(markdown: string): Outline {
  const { tree } = parse(markdown)
  const result: Outline = { blocks: tree.children.map(node => node.type), headings: [], code: [], tables: [], lists: [], links: [] }
  visit(tree, (node: Nodes) => {
    switch (node.type) {
      case 'heading': result.headings.push(`h${node.depth}`); break
      case 'code': result.code.push(node.lang ?? ''); break
      case 'table': result.tables.push(`${node.children.length}x${node.children[0]?.children.length ?? 0}`); break
      case 'list': result.lists.push(node.children.length); break
      case 'link':
      case 'image': if (!SWITCHER_TARGET.test(node.url)) result.links.push(neutralTarget(node.url)); break
    }
  })
  return result
}

/** Explains how a translation's outline differs from its source's; empty when they agree. */
export function compareOutlines(source: Outline, translation: Outline): string[] {
  const problems: string[] = []
  for (const part of ['blocks', 'headings', 'code', 'tables', 'lists', 'links'] as const) {
    const expected = source[part].map(String)
    const actual = translation[part].map(String)
    if (expected.join('\n') === actual.join('\n')) continue
    const at = expected.findIndex((value, index) => value !== actual[index])
    const index = at < 0 ? expected.length : at
    problems.push(`${part}: ${expected.length} in the source, ${actual.length} here; first difference at #${index + 1}`
      + ` (source ${expected[index] ?? 'nothing'}, here ${actual[index] ?? 'nothing'})`)
  }
  return problems
}

/** Writing systems that belong to one language only. */
const SCRIPTS = [
  { name: 'Chinese characters', pattern: /\p{Script=Han}/u, allowed: ['zh', 'zh-tw', 'ja'] },
  { name: 'Hangul', pattern: /\p{Script=Hangul}/u, allowed: ['ko'] },
  { name: 'Kana', pattern: /[\p{Script=Hiragana}\p{Script=Katakana}]/u, allowed: ['ja'] },
  { name: 'Cyrillic', pattern: /\p{Script=Cyrillic}/u, allowed: ['ru'] },
] as const satisfies readonly { name: string, pattern: RegExp, allowed: readonly Locale[] }[]

/**
 * Frequent characters that exist in simplified Chinese only: neither Traditional Chinese nor Japanese writes them
 * (they use 這, 時, 權, 証 …), so finding one in those languages means a sentence was left in the source. Shapes
 * the three share, such as 号, 数, 会, 点 or 条, are deliberately left out.
 */
const SIMPLIFIED_ONLY = new Set('这们个时为对发进过动关单务应设认证权组织户账码库测试录项显览导页请选择删编辑资访问规则统级'
  + '实现档开启闭间结构节线络缓读获签钥详细门岗变历审计种标报错误运维复联类带处产业么还让从该样无')

/** A line linking another README is the language switcher, which names every language in its own script. */
const LANGUAGE_SWITCHER = /\]\(README[\w.-]*\.md\)/

/** Whether a paragraph links another README, which makes it the language switcher. */
function isSwitcher(node: Nodes): boolean {
  return node.type === 'link' ? SWITCHER_TARGET.test(node.url) : 'children' in node && node.children.some(isSwitcher)
}

/** Lines (1-based, front matter and code included) that use a script foreign to the locale, with the reason. */
export function foreignScripts(markdown: string, locale: Locale): string[] {
  const problems: string[] = []
  markdown.split('\n').forEach((line, index) => {
    if (LANGUAGE_SWITCHER.test(line)) return
    const found: string[] = SCRIPTS
      .filter(script => !(script.allowed as readonly Locale[]).includes(locale) && script.pattern.test(line))
      .map(script => script.name)
    if (locale === 'zh-tw' || locale === 'ja') {
      const simplified = [...new Set([...line].filter(character => SIMPLIFIED_ONLY.has(character)))]
      if (simplified.length) found.push(`simplified Chinese (${simplified.join('')})`)
    }
    if (found.length) problems.push(`line ${index + 1}: ${found.join(', ')}: ${line.trim().slice(0, 80)}`)
  })
  return problems
}

/** At least this many words make a sentence that cannot be the same in two languages by chance. */
const SENTENCE_WORDS = 5

/**
 * The sentences of a page a translator writes: title, description, headings, paragraphs and table cells, without
 * code and link targets, reduced to their words so markup and punctuation do not matter.
 */
export function sentences(markdown: string): Set<string> {
  const { data, tree } = parse(markdown)
  const found = new Set<string>()
  const add = (text: string) => {
    const words = text.match(/\p{L}{2,}/gu) ?? []
    if (words.length >= SENTENCE_WORDS) found.add(words.join(' '))
  }
  add(String(data.title ?? ''))
  add(String(data.description ?? ''))
  const text = (node: Nodes): string => node.type === 'text' ? node.value
    : node.type === 'inlineCode' || node.type === 'code' || node.type === 'html' ? ' '
      : 'children' in node ? node.children.map(text).join('') : ''
  visit(tree, (node: Nodes) => {
    if (node.type === 'paragraph' || node.type === 'heading' || node.type === 'tableCell') {
      if (!isSwitcher(node)) add(text(node))
      return 'skip'
    }
  })
  return found
}

/** The sentences a translation copied unchanged from the English page (given as its sentences) instead of translating them. */
export function copiedSentences(translation: string, english: Set<string>): string[] {
  return [...sentences(translation)].filter(sentence => english.has(sentence))
}
