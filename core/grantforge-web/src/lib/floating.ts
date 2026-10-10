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
  /** Space kept free along every edge of the area. */
  margin?: number
  /** Space between the opener and the panel. */
  gap?: number
  /** Which edges of the opener and the panel line up: their left edges, or their right edges. */
  align?: 'start' | 'end'
}

/** The window as a box, for a panel that may use all of it. */
export function viewportBox(): Box {
  const width = window.innerWidth, height = window.innerHeight
  return { left: 0, top: 0, right: width, bottom: height, width, height }
}

/** Where two boxes overlap. An empty box when they do not, which no placement can then reach. */
export function intersect(a: Box, b: Box): Box {
  const left = Math.max(a.left, b.left), top = Math.max(a.top, b.top)
  const right = Math.min(a.right, b.right), bottom = Math.min(a.bottom, b.bottom)
  return { left, top, right, bottom, width: Math.max(0, right - left), height: Math.max(0, bottom - top) }
}

/**
 * Places a panel of a measured size next to its opener without crossing any edge of the area it may use: below when it
 * fits, above when only that fits, otherwise on the roomier side with its height capped (it then scrolls). Sideways it
 * lines up with the opener and shifts back inside the area; a panel wider than the area is narrowed to fit. The result
 * is pulled back inside the area whatever the opener does, so a panel whose opener has scrolled away still stays put
 * rather than drifting off over whatever lies beyond.
 */
export function placement(opener: Box, panel: { width: number; height: number }, area: Box,
  { margin = 8, gap = 6, align = 'start' }: PlacementOptions = {}): Placement {
  const below = Math.max(0, area.bottom - opener.bottom - gap - margin)
  const above = Math.max(0, opener.top - area.top - gap - margin)
  const side = panel.height <= below ? 'bottom' : panel.height <= above ? 'top' : below >= above ? 'bottom' : 'top'
  const maxHeight = side === 'bottom' ? below : above
  const height = Math.min(panel.height, maxHeight)
  const beside = side === 'bottom' ? opener.bottom + gap : opener.top - gap - height
  const top = Math.max(area.top + margin, Math.min(beside, area.bottom - margin - height))
  const width = Math.min(panel.width, Math.max(0, area.right - area.left - margin * 2))
  const preferred = align === 'end' ? opener.right - width : opener.left
  const left = Math.max(area.left + margin, Math.min(preferred, area.right - margin - width))
  return { left, top, width, maxHeight, side }
}

/** Whether a box lies entirely outside an area, so a panel hanging off it has nothing left to point at. */
export function outside(box: Box, area: Box): boolean {
  return box.bottom < area.top || box.top > area.bottom || box.right < area.left || box.left > area.right
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
