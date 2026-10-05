// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

'use client'

import { useEffect } from 'react'

/** Draws Mermaid diagrams and adds copy buttons to code blocks of the rendered page. */
export default function DocEnhancer() {
  useEffect(() => {
    const diagrams = document.querySelectorAll<HTMLElement>('.prose pre.mermaid:not([data-processed])')
    if (diagrams.length) {
      void import('mermaid').then(({ default: mermaid }) => {
        const dark = document.documentElement.classList.contains('dark')
        // The console's palette: brand violet on the page's own surface, so diagrams read as part of the docs.
        mermaid.initialize({ startOnLoad: false, theme: 'base', fontFamily: 'Inter, PingFang SC, sans-serif', themeVariables: {
          fontSize: '14px', primaryColor: dark ? '#272342' : '#f0efff', primaryBorderColor: '#635bff', primaryTextColor: dark ? '#edf1f8' : '#182234',
          lineColor: dark ? '#97a3b8' : '#7c8799', secondaryColor: dark ? '#171e2d' : '#f6f7fb', tertiaryColor: dark ? '#0f1420' : '#ffffff',
          actorBkg: dark ? '#272342' : '#f0efff', actorBorder: '#635bff', signalColor: dark ? '#edf1f8' : '#182234',
          noteBkgColor: dark ? '#171e2d' : '#fff8e6', noteBorderColor: dark ? '#2a3346' : '#f3d58a',
        } })
        void mermaid.run({ nodes: [...diagrams] })
      })
    }
    for (const pre of document.querySelectorAll<HTMLPreElement>('.prose figure[data-rehype-pretty-code-figure] pre')) {
      if (pre.querySelector('.copy-code')) continue
      const button = document.createElement('button')
      button.type = 'button'
      button.className = 'copy-code absolute right-2 top-2 rounded-lg border border-line bg-surface px-2 py-1 font-sans text-[11px] text-muted opacity-0 transition group-hover:opacity-100 hover:text-ink focus:opacity-100'
      button.textContent = '复制'
      button.addEventListener('click', () => {
        void navigator.clipboard.writeText(pre.querySelector('code')?.innerText ?? '').then(() => {
          button.textContent = '已复制'
          setTimeout(() => { button.textContent = '复制' }, 1500)
        })
      })
      pre.classList.add('group')
      pre.append(button)
    }
  }, [])
  return null
}
