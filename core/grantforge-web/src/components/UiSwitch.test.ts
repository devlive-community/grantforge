// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UiSwitch from './UiSwitch.vue'

describe('switch', () => {
  it('exposes switch semantics labelled by its visible label and toggles the model', async () => {
    const wrapper = mount(UiSwitch, { props: { modelValue: false, label: '启用' } })
    const control = wrapper.get('[role="switch"]')
    const label = wrapper.get('label')
    expect(control.attributes('aria-checked')).toBe('false')
    expect(control.attributes('aria-labelledby')).toBe(label.attributes('id'))
    expect(label.text()).toBe('启用')

    await control.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })

  it('reflects the checked state and can be disabled', async () => {
    const wrapper = mount(UiSwitch, { props: { modelValue: true, label: '启用', disabled: true } })
    expect(wrapper.get('[role="switch"]').attributes('aria-checked')).toBe('true')
    expect(wrapper.get('[role="switch"]').attributes('disabled')).toBeDefined()
  })
})
