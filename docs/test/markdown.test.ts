// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { render, tableOfContents } from '@/lib/markdown'

describe('markdown', () => {
  it('turns GitHub alerts into labelled callouts', async () => {
    const html = await render('> [!WARNING]\n> 小心')
    expect(html).toContain('<aside class="callout callout-warning" data-label="注意">')
    expect(html).toContain('小心')
    expect(html).not.toContain('[!WARNING]')
  })

  it('leaves Mermaid diagrams for the browser and highlights other code', async () => {
    const html = await render('```mermaid\nflowchart LR\n  A --> B\n```\n\n```java\nclass A {}\n```')
    expect(html).toContain('<pre class="mermaid">flowchart LR\n  A --> B</pre>')
    expect(html).toContain('data-language="java"')
  })

  it('opens other sites in a new tab and keeps own links in place', async () => {
    const html = await render('[外部](https://example.org) [内部](/guide/roles/)')
    expect(html).toContain('<a href="https://example.org" target="_blank" rel="noreferrer">')
    expect(html).toContain('<a href="/guide/roles/">')
  })

  it('lists the headings of the page with the ids they are rendered with', async () => {
    const markdown = '# 页\n\n## 安装\n\n### 安装\n\n#### 细节\n\n## `code` 与文字'
    const toc = tableOfContents(markdown)
    expect(toc).toEqual([
      { depth: 2, id: '安装', text: '安装' },
      { depth: 3, id: '安装-1', text: '安装' },
      { depth: 2, id: 'code-与文字', text: 'code 与文字' },
    ])
    const html = await render(markdown)
    for (const entry of toc) expect(html).toContain(`id="${entry.id}"`)
  })
})
