// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UiButton from './UiButton.vue'

describe('button', () => {
  it('is a plain button by default so it never submits a form by accident', () => {
    const wrapper = mount(UiButton, { slots: { default: '保存' } })
    expect(wrapper.attributes('type')).toBe('button')
    expect(wrapper.text()).toBe('保存')
    expect(wrapper.attributes('aria-busy')).toBe('false')
  })

  it('reports loading as busy and blocks further clicks', () => {
    const wrapper = mount(UiButton, { props: { loading: true, type: 'submit' } })
    expect(wrapper.attributes('aria-busy')).toBe('true')
    expect(wrapper.attributes('disabled')).toBeDefined()
    expect(wrapper.attributes('type')).toBe('submit')
    expect(wrapper.find('.animate-spin').attributes('aria-hidden')).toBe('true')
  })

  it('shows the spinner in place of its own icon while loading', async () => {
    const wrapper = mount(UiButton, { slots: { default: '<svg class="icon"></svg>测试连接' } })
    expect(wrapper.classes()).not.toContain('[&>svg]:hidden')
    expect(wrapper.find('.animate-spin').exists()).toBe(false)
    await wrapper.setProps({ loading: true })
    // The icon is hidden rather than removed, so it comes back as it was once the button is idle again.
    expect(wrapper.classes()).toContain('[&>svg]:hidden')
    expect(wrapper.find('svg.icon').exists()).toBe(true)
    expect(wrapper.find('.animate-spin').exists()).toBe(true)
    expect(wrapper.text()).toBe('测试连接')
  })

  it('can be disabled explicitly', () => {
    expect(mount(UiButton, { props: { disabled: true } }).attributes('disabled')).toBeDefined()
  })
})
