// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import GithubSlugger from 'github-slugger'
import type { Element, Root as HastRoot } from 'hast'
import type { Blockquote, Code, Heading, Paragraph, Root as MdastRoot, Text } from 'mdast'
import rehypeAutolinkHeadings from 'rehype-autolink-headings'
import rehypePrettyCode from 'rehype-pretty-code'
import rehypeSlug from 'rehype-slug'
import rehypeStringify from 'rehype-stringify'
import remarkGfm from 'remark-gfm'
import remarkParse from 'remark-parse'
import remarkRehype from 'remark-rehype'
import { unified } from 'unified'
import { visit } from 'unist-util-visit'

/** A heading of a page, for its table of contents. */
export interface TocEntry {
  depth: 2 | 3
  id: string
  text: string
}

const ALERTS = { NOTE: '说明', TIP: '提示', IMPORTANT: '重要', WARNING: '注意', CAUTION: '警告' } as const

/** Turns GitHub alerts (> [!NOTE] …) into callouts the stylesheet colours by kind. */
function remarkAlerts() {
  return (tree: MdastRoot) => {
    visit(tree, 'blockquote', (node: Blockquote) => {
      const first = node.children[0] as Paragraph | undefined
      const text = first?.type === 'paragraph' ? first.children[0] as Text | undefined : undefined
      const match = text?.type === 'text' ? /^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*/.exec(text.value) : null
      if (!match || !text) return
      const kind = match[1] as keyof typeof ALERTS
      text.value = text.value.slice(match[0].length)
      node.data = { hName: 'aside', hProperties: { className: ['callout', `callout-${kind.toLowerCase()}`], 'data-label': ALERTS[kind] } }
    })
  }
}

/** Leaves Mermaid diagrams as text for the browser to draw, instead of highlighting them as code. */
function remarkMermaid() {
  return (tree: MdastRoot) => {
    visit(tree, 'code', (node: Code, index, parent) => {
      if (node.lang !== 'mermaid' || !parent || index === undefined) return
      // The diagram goes to HTML verbatim (as hChildren), since a paragraph would lose its indentation.
      parent.children[index] = { type: 'paragraph', children: [],
        data: { hName: 'pre', hProperties: { className: ['mermaid'] }, hChildren: [{ type: 'text', value: node.value }] } } as Paragraph
    })
  }
}

/** Opens links to other sites in a new tab. */
function rehypeExternalLinks() {
  return (tree: HastRoot) => {
    visit(tree, 'element', (node: Element) => {
      const href = node.tagName === 'a' ? node.properties?.href : undefined
      if (typeof href === 'string' && /^https?:\/\//.test(href)) {
        node.properties = { ...node.properties, target: '_blank', rel: 'noreferrer' }
      }
    })
  }
}

const processor = unified()
  .use(remarkParse)
  .use(remarkGfm)
  .use(remarkAlerts)
  .use(remarkMermaid)
  .use(remarkRehype)
  .use(rehypeSlug)
  .use(rehypeAutolinkHeadings, { behavior: 'wrap' })
  .use(rehypePrettyCode, { theme: { light: 'github-light', dark: 'github-dark-dimmed' }, keepBackground: false })
  .use(rehypeExternalLinks)
  .use(rehypeStringify)

/** Renders a page's Markdown to HTML. */
export async function render(markdown: string): Promise<string> {
  return String(await processor.process(markdown))
}

/** Lists the second- and third-level headings of a page, with the ids rehype-slug gives them. */
export function tableOfContents(markdown: string): TocEntry[] {
  const slugger = new GithubSlugger()
  const tree = unified().use(remarkParse).use(remarkGfm).parse(markdown) as MdastRoot
  const entries: TocEntry[] = []
  visit(tree, 'heading', (node: Heading) => {
    const text = node.children.map(child => ('value' in child ? child.value : '')).join('')
    const id = slugger.slug(text)
    if (node.depth === 2 || node.depth === 3) entries.push({ depth: node.depth, id, text })
  })
  return entries
}
