// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UiDatePicker from './UiDatePicker.vue'

function render(props: Record<string, unknown> = {}, attachTo: Element = document.body) {
  const wrapper = mount(UiDatePicker, { attachTo, props: { label: '开始', modelValue: '', ...props,
    'onUpdate:modelValue': (next: string) => wrapper.setProps({ modelValue: next }) } })
  return wrapper
}
const panel = () => document.querySelector<HTMLElement>('[role="dialog"][aria-labelledby]')
const day = (value: string) => document.querySelector<HTMLButtonElement>(`[data-day="${value}"]`)
const click = async (element: Element | null | undefined) => { (element as HTMLElement | null)?.click(); await flushPromises() }
const button = (text: string) => Array.from(panel()?.querySelectorAll('button') ?? []).find(element => element.textContent?.trim() === text)

describe('date picker', () => {
  beforeEach(() => { vi.useFakeTimers({ toFake: ['Date'] }); vi.setSystemTime(new Date(2026, 9, 9, 14, 30)) })
  afterEach(() => { vi.useRealTimers(); document.body.innerHTML = '' })

  it('opens on the current month and writes the picked day the way the native input did', async () => {
    const wrapper = render()
    expect(wrapper.get('button').text()).toBe('选择日期')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(panel()?.textContent).toContain('2026年10月')
    // Monday comes first in Chinese; October 2026 starts on a Thursday, after three September days.
    expect(Array.from(panel()?.querySelectorAll('th abbr') ?? []).map(name => name.getAttribute('title'))[0]).toBe('星期一')
    expect(panel()?.querySelectorAll('[data-day]')[0]?.getAttribute('data-day')).toBe('2026-09-28')
    expect(day('2026-10-09')?.getAttribute('aria-current')).toBe('date')
    expect(document.activeElement).toBe(day('2026-10-09'))
    await click(day('2026-10-21'))
    expect(wrapper.props('modelValue')).toBe('2026-10-21')
    expect(panel()).toBeNull()
    expect(wrapper.get('button').text()).toBe('2026/10/21')
  })

  it('picks a time as well, keeping the time when the day changes', async () => {
    const wrapper = render({ time: true, modelValue: '2026-10-01T09:15' })
    expect(wrapper.get('button').text()).toBe('2026/10/01 09:15')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    await click(day('2026-10-03'))
    expect(wrapper.props('modelValue')).toBe('2026-10-03T09:15')
    // The calendar stays open for the time.
    const [hour, minute] = Array.from(panel()?.querySelectorAll<HTMLInputElement>('input[role="spinbutton"]') ?? [])
    hour?.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }))
    await flushPromises()
    expect(wrapper.props('modelValue')).toBe('2026-10-03T10:15')
    if (minute) { minute.value = '7'; minute.dispatchEvent(new Event('change')) }
    await flushPromises()
    expect(wrapper.props('modelValue')).toBe('2026-10-03T10:07')
    // Minutes wrap around rather than stop.
    for (let step = 0; step < 8; step++) await click(panel()?.querySelector('[aria-label="减小分"]'))
    expect(wrapper.props('modelValue')).toBe('2026-10-03T10:59')
    await click(button('现在'))
    expect(wrapper.props('modelValue')).toBe('2026-10-09T14:30')
    await click(button('完成'))
    expect(panel()).toBeNull()
  })

  it('moves by day, week and month with the keyboard and closes on Escape', async () => {
    const wrapper = render({ modelValue: '2026-10-30' })
    await wrapper.get('button').trigger('keydown', { key: 'ArrowDown' })
    await flushPromises()
    const press = async (key: string, shiftKey = false) => {
      document.activeElement?.closest('table')?.dispatchEvent(new KeyboardEvent('keydown', { key, shiftKey, bubbles: true }))
      await flushPromises()
    }
    await press('ArrowRight')
    expect(document.activeElement).toBe(day('2026-10-31'))
    await press('ArrowDown')
    // A week on lies in November, which the grid turns to.
    expect(panel()?.textContent).toContain('2026年11月')
    expect(document.activeElement).toBe(day('2026-11-07'))
    await press('Home')
    expect(document.activeElement).toBe(day('2026-11-02'))
    await press('PageUp')
    expect(document.activeElement).toBe(day('2026-10-02'))
    await press('PageDown', true)
    expect(document.activeElement).toBe(day('2027-10-02'))
    panel()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(panel()).toBeNull()
    expect(document.activeElement).toBe(wrapper.get('button').element)
    expect(wrapper.props('modelValue')).toBe('2026-10-30')
  })

  it('refuses days outside the bounds and clears the value', async () => {
    const wrapper = render({ modelValue: '2026-10-10', min: '2026-10-05', max: '2026-10-20T18:00' })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(day('2026-10-04')?.getAttribute('aria-disabled')).toBe('true')
    expect(day('2026-10-20')?.getAttribute('aria-disabled')).toBeNull()
    await click(day('2026-10-21'))
    expect(wrapper.props('modelValue')).toBe('2026-10-10')
    await click(button('清除'))
    expect(wrapper.props('modelValue')).toBe('')
    // Without a value it opens on the first month that can be picked.
    const later = render({ min: '2026-12-15T08:00' })
    await later.get('button').trigger('click')
    await flushPromises()
    expect(panel()?.textContent).toContain('2026年12月')
    await later.get('button').trigger('click')
    // Required values offer no clearing.
    const required = render({ modelValue: '2026-10-10', required: true })
    expect(required.find('[aria-label="清除开始"]').exists()).toBe(false)
  })

  it('stays inside the window near its bottom right corner', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1024 })
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 768 })
    const sizes = vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockReturnValue(340)
    const widths = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(296)
    const wrapper = render()
    wrapper.get('button').element.getBoundingClientRect = () => ({ left: 900, top: 700, right: 1010, bottom: 740, width: 110, height: 40,
      x: 900, y: 700, toJSON: () => ({}) })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    const style = panel()?.style
    // Flipped above the field, and shifted left to keep 8px from the right edge.
    expect(style?.position).toBe('fixed')
    expect(style?.left).toBe(`${1024 - 8 - 296}px`)
    expect(style?.top).toBe(`${700 - 6 - 340}px`)
    expect(style?.visibility).toBe('')
    sizes.mockRestore(); widths.mockRestore()
  })

  it('opens inside the dialog it is used in, so a modal dialog does not hide it', async () => {
    const dialog = document.body.appendChild(document.createElement('dialog'))
    const host = dialog.appendChild(document.createElement('div'))
    const wrapper = render({}, host)
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(panel()?.parentElement).toBe(dialog)
    expect(panel()?.style.position).toBe('absolute')
  })
})
