// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/** How long a scrollbar stays visible after the last scroll. */
export const LINGER = 800

/**
 * Shows a scrollbar only while its box scrolls: every scroll marks the scrolled element with `data-scrolling`, which
 * main.css turns into a visible thumb, and the mark goes once it has been still for a moment. One listener serves the
 * whole page, since scroll events do not bubble but can be caught on their way down.
 *
 * @returns a function that stops watching
 */
export function revealScrollbarsWhileScrolling(document: Document = window.document): () => void {
  const timers = new WeakMap<Element, ReturnType<typeof setTimeout>>()
  function scrolled(event: Event) {
    // The page itself scrolls on its scrolling element, though the event comes from the document.
    const element = event.target instanceof Element ? event.target : document.scrollingElement ?? document.documentElement
    if (!element) return
    clearTimeout(timers.get(element))
    if (!element.hasAttribute('data-scrolling')) element.setAttribute('data-scrolling', '')
    timers.set(element, setTimeout(() => {
      element.removeAttribute('data-scrolling')
      timers.delete(element)
    }, LINGER))
  }
  document.addEventListener('scroll', scrolled, { capture: true, passive: true })
  return () => document.removeEventListener('scroll', scrolled, { capture: true })
}
