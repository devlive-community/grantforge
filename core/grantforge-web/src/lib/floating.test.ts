// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { intersect, naturalSize, outside, placement, type Box } from './floating'

const viewport: Box = { left: 0, top: 0, right: 1000, bottom: 800, width: 1000, height: 800 }
const panel = { width: 300, height: 340 }
function box(left: number, top: number, width = 200, height = 40): Box {
  return { left, top, width, height, right: left + width, bottom: top + height }
}

describe('floating panels', () => {
  it('opens below with the left edges aligned when there is room', () => {
    expect(placement(box(100, 100), panel, viewport)).toEqual({ left: 100, top: 146, width: 300, maxHeight: 646, side: 'bottom' })
  })

  it('flips above near the bottom edge', () => {
    const at = placement(box(100, 700), panel, viewport)
    expect(at.side).toBe('top')
    // Its bottom sits the gap above the opener.
    expect(at.top + panel.height).toBe(694)
  })

  it('takes the roomier side and caps the height when it fits on neither', () => {
    const short: Box = { left: 0, top: 0, right: 1000, bottom: 400, width: 1000, height: 400 }
    const at = placement(box(100, 150), panel, short)
    expect(at.side).toBe('bottom')
    expect(at.maxHeight).toBe(400 - 190 - 6 - 8)
    expect(at.top + Math.min(panel.height, at.maxHeight)).toBeLessThanOrEqual(400 - 8)
    const high = placement(box(100, 230), panel, short)
    expect(high.side).toBe('top')
    expect(high.top).toBeGreaterThanOrEqual(8)
  })

  it('shifts back inside the right and left edges', () => {
    expect(placement(box(900, 100, 80), panel, viewport).left).toBe(1000 - 8 - 300)
    expect(placement(box(-50, 100), panel, viewport).left).toBe(8)
    // Aligned to the end, a panel wider than its opener grows towards the left.
    expect(placement(box(500, 100), panel, viewport, { align: 'end' }).left).toBe(400)
    expect(placement(box(100, 100), panel, viewport, { align: 'end' }).left).toBe(8)
  })

  it('narrows to a window narrower than the panel, such as a phone', () => {
    const phone: Box = { left: 0, top: 0, right: 280, bottom: 640, width: 280, height: 640 }
    expect(placement(box(10, 100, 260), panel, phone)).toMatchObject({ left: 8, width: 264 })
  })

  it('never reports negative room at the top edge', () => {
    const at = placement(box(100, 2), { width: 300, height: 900 }, viewport)
    expect(at.side).toBe('bottom')
    expect(at.maxHeight).toBe(800 - 42 - 6 - 8)
  })

  it('keeps to an area that does not start at the corner of the window', () => {
    // A dialog: the panel may not cross its edges even though the window has room below.
    const dialog: Box = { left: 352, top: 100, right: 928, bottom: 500, width: 576, height: 400 }
    const at = placement(box(600, 440), panel, dialog, { margin: 12, gap: 8, align: 'end' })
    expect(at.side).toBe('top')
    expect(at.top).toBeGreaterThanOrEqual(dialog.top)
    expect(at.left).toBeGreaterThanOrEqual(dialog.left)
    expect(at.left + at.width).toBeLessThanOrEqual(dialog.right)
  })

  it('measures a panel free of the caps an earlier placement gave it', () => {
    // A placement caps a panel to the room it has, so the element reports the cap rather than the panel.
    const element = document.createElement('div')
    Object.defineProperty(element, 'offsetWidth', { value: 280 })
    Object.defineProperty(element, 'offsetHeight', { value: 64 })
    element.style.maxWidth = '120px'
    element.style.maxHeight = '40px'
    expect(naturalSize(element)).toEqual({ width: 280, height: 64 })
    // The caps are put back, so the panel still fits where it was last put.
    expect(element.style.maxWidth).toBe('120px')
    expect(element.style.maxHeight).toBe('40px')
  })

  it('measures the box a panel holds rather than the content inside it', () => {
    // A cap taken from the content would be narrower than the box, which would then wrap and grow.
    const element = document.createElement('div')
    Object.defineProperty(element, 'offsetWidth', { value: 150 })
    Object.defineProperty(element, 'offsetHeight', { value: 36 })
    Object.defineProperty(element, 'scrollWidth', { value: 148 })
    Object.defineProperty(element, 'scrollHeight', { value: 62 })
    expect(naturalSize(element)).toEqual({ width: 150, height: 36 })
  })

  it('pulls a panel back inside the area when its opener has scrolled past an edge', () => {
    const dialog: Box = { left: 352, top: 100, right: 928, bottom: 500, width: 576, height: 400 }
    // Scrolled above the dialog, where there is no room for it at all.
    const at = placement(box(600, -200), panel, dialog, { margin: 12, gap: 8, align: 'end' })
    expect(at.top).toBeGreaterThanOrEqual(dialog.top + 12)
    expect(at.top + Math.min(panel.height, at.maxHeight)).toBeLessThanOrEqual(dialog.bottom - 12)
    expect(at.left + at.width).toBeLessThanOrEqual(dialog.right - 12)
  })

  it('tells when the opener has scrolled away', () => {
    expect(outside(box(100, -60), viewport)).toBe(true)
    expect(outside(box(100, 810), viewport)).toBe(true)
    expect(outside(box(100, -20), viewport)).toBe(false)
  })

  it('tells when the opener has left the area, though it is still in the window', () => {
    const dialog: Box = { left: 352, top: 100, right: 928, bottom: 500, width: 576, height: 400 }
    // Scrolled above the dialog, but still on screen.
    expect(outside(box(600, 40), dialog)).toBe(true)
    expect(outside(box(600, 300), dialog)).toBe(false)
  })

  it('narrows an area to where two boxes overlap', () => {
    expect(intersect(viewport, box(400, 200, 900, 900))).toEqual({ left: 400, top: 200, right: 1000, bottom: 800, width: 600, height: 600 })
    // A box entirely past the window's right edge leaves no room across, which no placement can reach.
    expect(intersect(viewport, box(2000, 0))).toMatchObject({ left: 2000, width: 0 })
  })
})
