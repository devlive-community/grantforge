// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TooltipLayer from './TooltipLayer.vue'

let layer: VueWrapper | undefined

function button(attributes: Record<string, string>, parent: HTMLElement = document.body) {
  const element = document.createElement('button')
  element.className = 'table-action'
  for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, value)
  element.innerHTML = '<svg></svg>'
  element.getBoundingClientRect = () => ({ top: 200, bottom: 228, left: 300, right: 328, width: 28, height: 28, x: 300, y: 200 }) as DOMRect
  parent.append(element)
  return element
}
function pointer(type: string, target: Element, pointerType = 'mouse', relatedTarget: EventTarget | null = null) {
  const event = new MouseEvent(type, { bubbles: true, relatedTarget })
  Object.defineProperty(event, 'pointerType', { value: pointerType })
  target.dispatchEvent(event)
}
const tooltip = () => document.querySelector<HTMLElement>('[data-tooltip-layer]')

describe('tooltip layer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // jsdom lays nothing out: give the window a size, and the tooltip the one a short text takes.
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(1024)
    vi.spyOn(document.documentElement, 'clientHeight', 'get').mockReturnValue(768)
    vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(60)
    vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockReturnValue(24)
    layer = mount(TooltipLayer, { attachTo: document.body })
  })
  afterEach(() => {
    layer?.unmount()
    document.body.innerHTML = ''
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('names an icon button by its label after a short pause under the mouse', async () => {
    const edit = button({ 'aria-label': '编辑 hive-prod' })
    pointer('pointerover', edit.querySelector('svg') ?? edit)
    await flushPromises()
    expect(tooltip()).toBeNull()
    await vi.advanceTimersByTimeAsync(400)
    expect(tooltip()?.textContent).toBe('编辑 hive-prod')
    // The button already has its name for screen readers; the tooltip only shows it.
    expect(tooltip()?.getAttribute('aria-hidden')).toBe('true')
    expect(tooltip()?.className).toContain('pointer-events-none')
  })

  it('prefers a shorter tooltip text where a button gives one', async () => {
    const grant = button({ 'aria-label': '为 审计员 授权', 'data-tooltip': '授权' })
    pointer('pointerover', grant)
    await vi.advanceTimersByTimeAsync(400)
    expect(tooltip()?.textContent).toBe('授权')
  })

  it('sits above its button, its triangle pointing down at it', async () => {
    const edit = button({ 'aria-label': '编辑' })
    pointer('pointerover', edit)
    await vi.advanceTimersByTimeAsync(400)
    await flushPromises()
    expect(tooltip()?.style.top).toBe(`${200 - 8 - 24}px`)
    expect(tooltip()?.style.visibility).toBe('visible')
    expect(tooltip()?.querySelector('span')?.className).toContain('-bottom-1')
    // It grows down out of the button once it has its place, not from where it waited.
    expect(tooltip()?.className).toContain('tooltip-in-top')
  })

  it('flips below a button at the top of the window', async () => {
    const edit = button({ 'aria-label': '编辑' })
    edit.getBoundingClientRect = () => ({ top: 4, bottom: 32, left: 300, right: 328, width: 28, height: 28, x: 300, y: 4 }) as DOMRect
    pointer('pointerover', edit)
    await vi.advanceTimersByTimeAsync(400)
    await flushPromises()
    expect(tooltip()?.style.top).toBe(`${32 + 8}px`)
    expect(tooltip()?.querySelector('span')?.className).toContain('-top-1')
    expect(tooltip()?.className).toContain('tooltip-in-bottom')
  })

  it('goes on a press, on Escape and when the pointer leaves', async () => {
    const edit = button({ 'aria-label': '编辑' })
    pointer('pointerover', edit)
    await vi.advanceTimersByTimeAsync(400)
    pointer('pointerdown', edit)
    await flushPromises()
    expect(tooltip()).toBeNull()

    pointer('pointerover', document.body)
    await vi.advanceTimersByTimeAsync(1000)
    pointer('pointerover', edit)
    await vi.advanceTimersByTimeAsync(400)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(tooltip()).toBeNull()

    await vi.advanceTimersByTimeAsync(1000)
    pointer('pointerover', edit)
    await vi.advanceTimersByTimeAsync(400)
    pointer('pointerout', edit, 'mouse', document.body)
    await flushPromises()
    expect(tooltip()).toBeNull()
  })

  it('moves straight to the next button without another pause', async () => {
    const edit = button({ 'aria-label': '编辑' }), remove = button({ 'aria-label': '删除' })
    pointer('pointerover', edit)
    await vi.advanceTimersByTimeAsync(400)
    pointer('pointerout', edit, 'mouse', remove)
    pointer('pointerover', remove)
    await flushPromises()
    expect(tooltip()?.textContent).toBe('删除')
  })

  it('stays away from a touch, which has no hover', async () => {
    pointer('pointerover', button({ 'aria-label': '编辑' }), 'touch')
    await vi.advanceTimersByTimeAsync(1000)
    expect(tooltip()).toBeNull()
  })

  it('shows at once on keyboard focus, not on the focus a click gives', async () => {
    const edit = button({ 'aria-label': '编辑' })
    const matches = edit.matches.bind(edit)
    let keyboard = false
    edit.matches = (selector: string) => selector === ':focus-visible' ? keyboard : matches(selector)
    edit.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await flushPromises()
    expect(tooltip()).toBeNull()
    keyboard = true
    edit.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await flushPromises()
    expect(tooltip()?.textContent).toBe('编辑')
    edit.dispatchEvent(new FocusEvent('focusout', { bubbles: true }))
    await flushPromises()
    expect(tooltip()).toBeNull()
  })

  it('renders inside the dialog its button sits in, above the dialog\'s backdrop', async () => {
    const dialog = document.createElement('dialog')
    document.body.append(dialog)
    const edit = button({ 'aria-label': '编辑' }, dialog)
    pointer('pointerover', edit)
    await vi.advanceTimersByTimeAsync(400)
    expect(tooltip()?.closest('dialog')).toBe(dialog)
    expect(tooltip()?.style.position).toBe('absolute')
  })

  it('ignores buttons that name nothing or opt out', async () => {
    pointer('pointerover', button({}))
    pointer('pointerover', button({ 'aria-label': '编辑', 'data-tooltip': 'off' }))
    await vi.advanceTimersByTimeAsync(1000)
    expect(tooltip()).toBeNull()
  })
})
