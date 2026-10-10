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
})
