// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { CSSProperties } from 'vue'

/** A rectangle on the screen, as getBoundingClientRect gives it. */
export interface Box { left: number; top: number; right: number; bottom: number; width: number; height: number }

/** Where a floating panel goes next to the element that opened it. */
export interface Placement { left: number; top: number; width: number; maxHeight: number; side: 'top' | 'bottom' }

export interface PlacementOptions {
  /** Space kept free along every edge of the window. */
  margin?: number
  /** Space between the opener and the panel. */
  gap?: number
  /** Which edges of the opener and the panel line up: their left edges, or their right edges. */
  align?: 'start' | 'end'
}

/**
 * Places a panel of a measured size next to its opener without crossing any edge of the window: below when it fits,
 * above when only that fits, otherwise on the roomier side with its height capped (it then scrolls). Sideways it lines
 * up with the opener and shifts back inside the window; a panel wider than the window is narrowed to fit.
 */
export function placement(opener: Box, panel: { width: number; height: number }, viewport: { width: number; height: number },
  { margin = 8, gap = 6, align = 'start' }: PlacementOptions = {}): Placement {
  const below = Math.max(0, viewport.height - opener.bottom - gap - margin)
  const above = Math.max(0, opener.top - gap - margin)
  const side = panel.height <= below ? 'bottom' : panel.height <= above ? 'top' : below >= above ? 'bottom' : 'top'
  const maxHeight = side === 'bottom' ? below : above
  const height = Math.min(panel.height, maxHeight)
  const top = side === 'bottom' ? opener.bottom + gap : opener.top - gap - height
  const width = Math.min(panel.width, Math.max(0, viewport.width - margin * 2))
  const preferred = align === 'end' ? opener.right - width : opener.left
  const left = Math.max(margin, Math.min(preferred, viewport.width - margin - width))
  return { left, top, width, maxHeight, side }
}

/**
 * The style that puts a panel at a placement, inside the element it is rendered in: the page, or a modal dialog,
 * which is positioned and may be scaled while it animates in.
 */
export function placedStyle(at: Placement, container: HTMLElement): CSSProperties {
  if (!(container instanceof HTMLDialogElement)) {
    return { position: 'fixed', left: `${at.left}px`, top: `${at.top}px`, maxWidth: `${at.width}px`, maxHeight: `${at.maxHeight}px` }
  }
  const box = container.getBoundingClientRect()
  const scaleX = container.offsetWidth ? box.width / container.offsetWidth : 1
  const scaleY = container.offsetHeight ? box.height / container.offsetHeight : 1
  return {
    position: 'absolute',
    left: `${(at.left - box.left) / scaleX - container.clientLeft}px`,
    top: `${(at.top - box.top) / scaleY - container.clientTop}px`,
    maxWidth: `${at.width / scaleX}px`,
    maxHeight: `${at.maxHeight / scaleY}px`,
  }
}

/** Whether an opener has scrolled entirely out of the window, so its panel should close rather than float on. */
export function offscreen(opener: Box, viewport: { width: number; height: number }): boolean {
  return opener.bottom < 0 || opener.top > viewport.height || opener.right < 0 || opener.left > viewport.width
}
