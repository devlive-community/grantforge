// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { i18n, setLocale } from '@/i18n'
import LocalePicker from './LocalePicker.vue'

const mountPicker = () => mount(LocalePicker, { attachTo: document.body, global: { plugins: [i18n] } })

describe('locale picker', () => {
  afterEach(() => setLocale('zh-CN'))

  it('opens the list of languages on a click', async () => {
    const wrapper = mountPicker()
    expect(wrapper.find('li').exists()).toBe(false)
    await wrapper.get('button[aria-expanded]').trigger('click')
    expect(wrapper.findAll('li').map(item => item.text())).toEqual(['中文', 'English'])
    wrapper.unmount()
  })

  it('switches to the picked language and closes the list', async () => {
    const wrapper = mountPicker()
    await wrapper.get('button[aria-expanded]').trigger('click')
    await wrapper.get('button[lang="en-US"]').trigger('click')
    expect(i18n.global.locale.value).toBe('en-US')
    expect(document.documentElement.lang).toBe('en-US')
    expect(localStorage.getItem('GrantForgeLocale')).toBe('en-US')
    expect(wrapper.find('li').exists()).toBe(false)
    wrapper.unmount()
  })

  it('marks the language in use and closes on a click outside or on Escape', async () => {
    const wrapper = mountPicker()
    await wrapper.get('button[aria-expanded]').trigger('click')
    expect(wrapper.get('button[lang="zh-CN"]').attributes('aria-current')).toBe('true')
    expect(wrapper.get('button[lang="en-US"]').attributes('aria-current')).toBeUndefined()
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('li').exists()).toBe(false)

    await wrapper.get('button[aria-expanded]').trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('li').exists()).toBe(false)
    wrapper.unmount()
  })
})
