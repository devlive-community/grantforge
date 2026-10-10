// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import UiTip from './UiTip.vue'

const wrappers: VueWrapper[] = []

/** A control the tip hangs off, measured the way a 300x50 field 100px down the page is. */
function control(box = { top: 100, bottom: 150, left: 40, right: 340 }) {
  const anchor = document.createElement('div')
  anchor.getBoundingClientRect = () => ({ ...box, width: box.right - box.left, height: box.bottom - box.top }) as DOMRect
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

/** A modal dialog of a measured size, with the control the tip hangs off inside it. */
function dialogControl(dialog = { top: 100, bottom: 500, left: 352, right: 928 }, field = { top: 440, bottom: 490, left: 600, right: 900 }) {
  const host = document.createElement('dialog')
  host.getBoundingClientRect = () => ({ ...dialog, width: dialog.right - dialog.left, height: dialog.bottom - dialog.top }) as DOMRect
  const anchor = document.createElement('div')
  anchor.getBoundingClientRect = () => ({ ...field, width: field.right - field.left, height: field.bottom - field.top }) as DOMRect
  host.append(anchor)
  document.body.append(host)
  return { host, anchor }
}

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  document.body.innerHTML = ''
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
    // Measured at its natural size, then placed by the shared floating helper.
    expect(bubble?.style.left).toBe(`${340 - (bubble?.offsetWidth ?? 0)}px`)
    expect(bubble?.style.top).toBe('158px')
    // The little triangle points back up at the control.
    expect(bubble?.querySelector('span[aria-hidden="true"]')).not.toBeNull()
  })

  it('keeps following the control while the page scrolls', async () => {
    const anchor = control()
    render(anchor)
    await flushPromises()
    expect(tip()?.style.top).toBe('158px')
    anchor.getBoundingClientRect = () => ({ top: 60, bottom: 110, left: 40, right: 340, width: 300, height: 50 }) as DOMRect
    window.dispatchEvent(new Event('scroll'))
    await flushPromises()
    expect(tip()?.style.top).toBe('118px')
  })

  it('stops following the page once it unmounts', async () => {
    const anchor = control()
    const wrapper = render(anchor)
    await flushPromises()
    wrapper.unmount()
    anchor.getBoundingClientRect = () => ({ top: 0, bottom: 50, left: 40, right: 340, width: 300, height: 50 }) as DOMRect
    window.dispatchEvent(new Event('scroll'))
    await flushPromises()
    expect(tip()).toBeNull()
  })

  it('keeps to the dialog it hangs off, even where the window has room below', async () => {
    const { anchor } = dialogControl()
    render(anchor)
    await flushPromises()
    const bubble = tip(), dialog = anchor.closest('dialog')
    const box = dialog?.getBoundingClientRect()
    // Placed inside the dialog's own coordinates, not the window's.
    expect(bubble?.style.position).toBe('absolute')
    const top = (box?.top ?? 0) + Number.parseFloat(bubble?.style.top ?? '0')
    const left = (box?.left ?? 0) + Number.parseFloat(bubble?.style.left ?? '0')
    // The window has room below the control, but the dialog does not, so the tip flips above it.
    expect(top).toBeGreaterThanOrEqual(box?.top ?? 0)
    expect(top).toBeLessThanOrEqual(box?.bottom ?? 0)
    expect(left).toBeGreaterThanOrEqual(box?.left ?? 0)
    expect(left).toBeLessThanOrEqual(box?.right ?? 0)
  })

  it('stays out of the dialog\'s layout while it waits for its control to come back', async () => {
    // The control scrolled above the dialog, so there is nothing left to point at.
    const { host, anchor } = dialogControl({ top: 100, bottom: 500, left: 352, right: 928 }, { top: 40, bottom: 90, left: 600, right: 900 })
    render(anchor)
    await flushPromises()
    const bubble = tip()
    expect(bubble?.style.visibility).toBe('hidden')
    // Still positioned, so it cannot take up room in the dialog and push its contents around.
    expect(bubble?.style.position).toBe('absolute')
    expect(bubble?.style.top).not.toBe('')
    expect(bubble?.parentElement).toBe(host)
  })

  it('stays out of the page\'s layout while it waits for its control to come back', async () => {
    const anchor = control({ top: -60, bottom: -10, left: 40, right: 340 })
    render(anchor)
    await flushPromises()
    const bubble = tip()
    expect(bubble?.style.visibility).toBe('hidden')
    expect(bubble?.style.position).toBe('fixed')
    expect(bubble?.parentElement).toBe(document.body)
  })

  it('stays inside the part of a scrolled form that is on screen', async () => {
    // A dialog whose body scrolls: the control has scrolled up under the dialog's heading.
    const { host, anchor } = dialogControl({ top: 100, bottom: 500, left: 352, right: 928 }, { top: 60, bottom: 110, left: 600, right: 900 })
    const scroller = document.createElement('div')
    scroller.getBoundingClientRect = () => ({ top: 148, bottom: 460, left: 352, right: 928, width: 576, height: 312 }) as DOMRect
    // jsdom reports no layout, so give the scroller a size it can clip to.
    Object.defineProperty(scroller, 'clientWidth', { value: 576 })
    Object.defineProperty(scroller, 'clientHeight', { value: 312 })
    Object.defineProperty(scroller, 'clientLeft', { value: 0 })
    Object.defineProperty(scroller, 'clientTop', { value: 0 })
    Object.defineProperty(scroller, 'offsetWidth', { value: 576 })
    Object.defineProperty(scroller, 'offsetHeight', { value: 312 })
    // jsdom cannot see the stylesheet, so mark the scroller as one that clips, the way overflow-y-auto does.
    scroller.style.overflowY = 'auto'
    scroller.append(anchor)
    host.append(scroller)
    render(anchor)
    await flushPromises()
    const bubble = tip()
    // The control is behind the heading now, so there is nothing on screen left to point at.
    expect(bubble?.style.visibility).toBe('hidden')
    // Still inside the scroller, so it cannot cover the dialog's heading either.
    const top = 148 + Number.parseFloat(bubble?.style.top ?? '0')
    expect(top).toBeGreaterThanOrEqual(148)
    expect(top).toBeLessThanOrEqual(460)
  })
})
