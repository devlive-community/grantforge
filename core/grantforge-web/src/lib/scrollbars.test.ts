// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LINGER, revealScrollbarsWhileScrolling } from './scrollbars'

describe('scrollbars', () => {
  let stop: () => void
  beforeEach(() => {
    vi.useFakeTimers()
    stop = revealScrollbarsWhileScrolling()
  })
  afterEach(() => {
    stop()
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('marks a box as scrolling while it scrolls and a moment after', () => {
    const box = document.createElement('div')
    document.body.append(box)
    box.dispatchEvent(new Event('scroll'))
    expect(box.hasAttribute('data-scrolling')).toBe(true)
    vi.advanceTimersByTime(LINGER - 100)
    // Still scrolling: the moment starts again.
    box.dispatchEvent(new Event('scroll'))
    vi.advanceTimersByTime(LINGER - 100)
    expect(box.hasAttribute('data-scrolling')).toBe(true)
    vi.advanceTimersByTime(100)
    expect(box.hasAttribute('data-scrolling')).toBe(false)
  })

  it('marks the page itself when the window scrolls', () => {
    document.dispatchEvent(new Event('scroll'))
    expect(document.documentElement.hasAttribute('data-scrolling')).toBe(true)
    vi.advanceTimersByTime(LINGER)
    expect(document.documentElement.hasAttribute('data-scrolling')).toBe(false)
  })

  it('keeps each box to its own moment', () => {
    const one = document.createElement('div'), two = document.createElement('div')
    document.body.append(one, two)
    one.dispatchEvent(new Event('scroll'))
    vi.advanceTimersByTime(LINGER / 2)
    two.dispatchEvent(new Event('scroll'))
    vi.advanceTimersByTime(LINGER / 2)
    expect(one.hasAttribute('data-scrolling')).toBe(false)
    expect(two.hasAttribute('data-scrolling')).toBe(true)
  })

  it('stops watching', () => {
    stop()
    const box = document.createElement('div')
    document.body.append(box)
    box.dispatchEvent(new Event('scroll'))
    expect(box.hasAttribute('data-scrolling')).toBe(false)
  })
})
