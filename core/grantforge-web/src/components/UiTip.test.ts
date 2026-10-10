// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UiTip from './UiTip.vue'

const wrappers: VueWrapper[] = []

type Edges = { top: number; bottom: number; left: number; right: number }
function rect(box: Edges) { return { ...box, width: box.right - box.left, height: box.bottom - box.top, x: box.left, y: box.top } as DOMRect }
/** A control the tip hangs off, measured the way a 300x50 input 100px down a 1024x768 window is. */
function control(box: Edges = { top: 100, bottom: 150, left: 40, right: 340 }) {
  const anchor = document.createElement('div')
  anchor.getBoundingClientRect = () => rect(box)
  document.body.append(anchor)
  return anchor
}
function render(anchor: HTMLElement, props: Partial<{ message: string; state: 'error' | 'success' }> = {}) {
  // The real transition, so the tip is portalled the way the browser sees it.
  const wrapper = mount(UiTip, { attachTo: document.body, props: { message: '格式错误', anchor, ...props }, global: { stubs: { transition: false } } })
  wrappers.push(wrapper)
  return wrapper
}
function tip() { return document.querySelector<HTMLElement>('[role="alert"], [role="status"]') }
/** The little triangle, read by the classes that decide which way it points. */
function arrow(bubble: Element | null | undefined) { return bubble?.querySelector('span[aria-hidden="true"]') }

/** A modal dialog of a measured size, with the control the tip hangs off inside it. */
function dialogControl(dialog: Edges = { top: 100, bottom: 500, left: 352, right: 928 }, field: Edges = { top: 440, bottom: 490, left: 600, right: 900 }) {
  const host = document.createElement('dialog')
  host.getBoundingClientRect = () => rect(dialog)
  const anchor = document.createElement('div')
  anchor.getBoundingClientRect = () => rect(field)
  host.append(anchor)
  document.body.append(host)
  return { host, anchor }
}

/** A dialog whose body scrolls between a heading above 148px and a footer below 460px, with a control in the body. */
function scrolledDialog(field: Edges) {
  const { host, anchor } = dialogControl({ top: 100, bottom: 520, left: 352, right: 928 }, field)
  const scroller = document.createElement('div')
  scroller.getBoundingClientRect = () => rect({ top: 148, bottom: 460, left: 352, right: 928 })
  // jsdom reports no layout, so give the scroller a size it can clip to.
  for (const [name, value] of Object.entries({ clientWidth: 576, clientHeight: 312, clientLeft: 0, clientTop: 0, offsetWidth: 576, offsetHeight: 312 })) {
    Object.defineProperty(scroller, name, { value })
  }
  // jsdom cannot see the stylesheet, so mark the scroller as one that clips, the way overflow-y-auto does.
  scroller.style.overflowY = 'auto'
  scroller.append(anchor)
  host.append(scroller)
  return { host, anchor }
}

beforeEach(() => {
  // jsdom lays nothing out: give the window a size, and the bubble the one a short message takes.
  vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(1024)
  vi.spyOn(document.documentElement, 'clientHeight', 'get').mockReturnValue(768)
  vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(120)
  vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockReturnValue(34)
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('tip', () => {
  it('says nothing and floats nothing without a message', () => {
    const anchor = control()
    render(anchor, { message: '' })
    expect(tip()).toBeNull()
  })

  it('renders the message as an alert that never takes a pointer event', () => {
    const anchor = control()
    render(anchor)
    const bubble = tip()
    expect(bubble?.textContent).toContain('格式错误')
    expect(bubble?.getAttribute('role')).toBe('alert')
    expect(bubble?.className).toContain('pointer-events-none')
    // Nothing was portalled out of the wrapper's own tree.
    expect(bubble?.parentElement).toBe(document.body)
  })

  it('renders a success message as a status instead', () => {
    const anchor = control()
    render(anchor, { state: 'success', message: '名称可用' })
    expect(tip()?.getAttribute('role')).toBe('status')
    expect(tip()?.textContent).toContain('名称可用')
  })

  it('fades in when its message arrives and leaves once it is gone', async () => {
    const anchor = control()
    const wrapper = render(anchor, { message: '' })
    expect(tip()).toBeNull()
    await wrapper.setProps({ message: '格式错误' })
    // The fade starts on the first frame, so it is still transparent when it appears.
    expect(tip()?.className).toContain('opacity-0')
    // It stays for a moment after the message goes: it fades rather than blinking out.
    await wrapper.setProps({ message: '' })
    expect(tip()).not.toBeNull()
    await vi.waitFor(() => expect(tip()).toBeNull())
  })

  it('places itself below the control, lined up with its right edge', async () => {
    const anchor = control()
    render(anchor)
    await flushPromises()
    const bubble = tip()
    expect(bubble?.style.position).toBe('fixed')
    expect(bubble?.style.left).toBe(`${340 - 120}px`)
    expect(bubble?.style.top).toBe('160px')
    expect(bubble?.style.visibility).toBe('visible')
    // The triangle sits on the bubble's top edge, outlined like it, under the control's right end.
    expect(arrow(bubble)?.className).toContain('-top-[5px]')
    expect(arrow(bubble)?.className).toContain('border-t')
    expect(arrow(bubble)?.className).toContain('border-rose-200')
    expect(arrow(bubble)?.getAttribute('style')).toContain('left: 95px')
  })

  it('points at the input itself even when the input clips its own contents', async () => {
    // Browsers give an input overflow: clip; the tip used to treat that as the room it had and crush itself onto it.
    const anchor = control()
    anchor.style.overflow = 'clip'
    render(anchor)
    await flushPromises()
    expect(tip()?.style.top).toBe('160px')
    expect(tip()?.style.visibility).toBe('visible')
  })

  it('points its triangle down at the control when it has to sit above it', async () => {
    // No room below a control at the bottom of the window, so the tip flips above it and points the other way.
    const anchor = control({ top: 700, bottom: 750, left: 40, right: 340 })
    render(anchor)
    await flushPromises()
    const bubble = tip()
    expect(bubble?.style.top).toBe(`${700 - 10 - 34}px`)
    expect(arrow(bubble)?.className).toContain('-bottom-[5px]')
    expect(arrow(bubble)?.className).toContain('border-b')
    expect(arrow(bubble)?.className).not.toContain('border-t')
  })

  it('slides sideways to stay on screen, its triangle still under the control', async () => {
    const anchor = control({ top: 100, bottom: 150, left: 0, right: 60 })
    render(anchor)
    await flushPromises()
    const bubble = tip()
    expect(bubble?.style.left).toBe('8px')
    // Still aimed at the control's right end (40px), not at the bubble's own corner it would have had.
    expect(arrow(bubble)?.getAttribute('style')).toContain(`left: ${40 - 8 - 5}px`)
  })

  it('keeps following the control while the page scrolls', async () => {
    const anchor = control()
    render(anchor)
    await flushPromises()
    expect(tip()?.style.top).toBe('160px')
    anchor.getBoundingClientRect = () => ({ top: 60, bottom: 110, left: 40, right: 340, width: 300, height: 50, x: 40, y: 60 }) as DOMRect
    window.dispatchEvent(new Event('scroll'))
    await flushPromises()
    expect(tip()?.style.top).toBe('120px')
  })

  it('stops following the page once it unmounts', async () => {
    const anchor = control()
    const wrapper = render(anchor)
    await flushPromises()
    wrapper.unmount()
    anchor.getBoundingClientRect = () => ({ top: 0, bottom: 50, left: 40, right: 340, width: 300, height: 50, x: 40, y: 0 }) as DOMRect
    window.dispatchEvent(new Event('scroll'))
    await flushPromises()
    expect(tip()).toBeNull()
  })

  it('renders inside the dialog its control sits in', async () => {
    const { host, anchor } = dialogControl()
    render(anchor)
    await flushPromises()
    const bubble = tip()
    // A modal dialog is in the top layer: a tip in the page beneath it would be hidden behind it.
    expect(bubble?.parentElement).toBe(host)
    expect(bubble?.style.position).toBe('absolute')
  })

  it('waits, hidden, while its control is scrolled off the page', async () => {
    const anchor = control({ top: -60, bottom: -10, left: 40, right: 340 })
    render(anchor)
    await flushPromises()
    const bubble = tip()
    expect(bubble?.style.visibility).toBe('hidden')
    // Still positioned, so it cannot take up room in the page and push its contents around.
    expect(bubble?.style.position).toBe('fixed')
  })

  it('keeps to the part of a scrolled form that is on screen', async () => {
    // A dialog whose body scrolls, with the control on its last row: no room below it inside the body.
    const { anchor } = scrolledDialog({ top: 390, bottom: 440, left: 600, right: 900 })
    render(anchor)
    await flushPromises()
    // The window has room below, but the tip would hang past the body into the footer, so it flips above.
    expect(tip()?.style.visibility).toBe('visible')
    expect(arrow(tip())?.className).toContain('-bottom-[5px]')
  })

  it('hides once its control scrolls up under a dialog\'s heading', async () => {
    const { anchor } = scrolledDialog({ top: 60, bottom: 110, left: 600, right: 900 })
    render(anchor)
    await flushPromises()
    expect(tip()?.style.visibility).toBe('hidden')
  })
})
