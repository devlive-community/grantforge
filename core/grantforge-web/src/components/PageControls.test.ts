// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PageControls from './PageControls.vue'

function mountControls(props: { page: number; pages: number; loading?: boolean }) {
  return mount(PageControls, { props: { size: 20, total: 55, ...props }, attachTo: document.body })
}

describe('page controls', () => {
  it('shows the total, the current page and the page count', () => {
    const wrapper = mountControls({ page: 2, pages: 3 })
    expect(wrapper.text()).toContain('55')
    expect(wrapper.text()).toContain('2 / 3')
    wrapper.unmount()
  })

  it('moves between pages and reports the new page through v-model', async () => {
    const wrapper = mountControls({ page: 2, pages: 3 })
    await wrapper.get('[aria-label="下一页"]').trigger('click')
    await wrapper.get('[aria-label="上一页"]').trigger('click')
    expect(wrapper.emitted('update:page')).toEqual([[3], [2]])
    wrapper.unmount()
  })

  it('keeps boundary and loading buttons focusable but inert', async () => {
    const wrapper = mountControls({ page: 1, pages: 1, loading: true })
    const previous = wrapper.get('[aria-label="上一页"]')
    const next = wrapper.get('[aria-label="下一页"]')
    // Native disabled would blur the focused button while a page loads (B-007).
    expect(previous.attributes('disabled')).toBeUndefined()
    expect(next.attributes('disabled')).toBeUndefined()
    expect(previous.attributes('aria-disabled')).toBe('true')
    expect(next.attributes('aria-disabled')).toBe('true')
    ;(next.element as HTMLButtonElement).focus()
    await next.trigger('click')
    await previous.trigger('click')
    expect(wrapper.emitted('update:page')).toBeUndefined()
    expect(document.activeElement).toBe(next.element)
    wrapper.unmount()
  })

  it('enables navigation again once loading finishes', async () => {
    const wrapper = mountControls({ page: 1, pages: 2, loading: true })
    await wrapper.setProps({ loading: false })
    expect(wrapper.get('[aria-label="下一页"]').attributes('aria-disabled')).toBe('false')
    wrapper.unmount()
  })

  it('never shows a page beyond the page count', () => {
    const wrapper = mountControls({ page: 5, pages: 0 })
    expect(wrapper.text()).toContain('1 / 1')
    wrapper.unmount()
  })

  it('emits the chosen page size as a number', async () => {
    const wrapper = mountControls({ page: 1, pages: 3 })
    await wrapper.get('[role="combobox"]').trigger('click')
    const option = document.querySelectorAll<HTMLElement>('[role="option"]')[2]
    expect(option).toBeDefined()
    option?.click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('size')).toEqual([[50]])
    wrapper.unmount()
  })
})
