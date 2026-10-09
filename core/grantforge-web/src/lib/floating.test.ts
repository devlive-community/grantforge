// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { offscreen, placement, type Box } from './floating'

const viewport = { width: 1000, height: 800 }
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
    const short = { width: 1000, height: 400 }
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
    const phone = { width: 280, height: 640 }
    expect(placement(box(10, 100, 260), panel, phone)).toMatchObject({ left: 8, width: 264 })
  })

  it('never reports negative room at the top edge', () => {
    const at = placement(box(100, 2), { width: 300, height: 900 }, viewport)
    expect(at.side).toBe('bottom')
    expect(at.maxHeight).toBe(800 - 42 - 6 - 8)
  })

  it('tells when the opener has scrolled away', () => {
    expect(offscreen(box(100, -60), viewport)).toBe(true)
    expect(offscreen(box(100, 810), viewport)).toBe(true)
    expect(offscreen(box(100, -20), viewport)).toBe(false)
  })
})
