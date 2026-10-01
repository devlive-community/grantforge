// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UiCheckbox from './UiCheckbox.vue'

describe('custom checkbox', () => {
  it('toggles the controlled model when its visible label is clicked', async () => {
    const wrapper = mount(UiCheckbox, { props: { checked: false, label: '管理员' }, attachTo: document.body })
    wrapper.element.click()
    await nextTick()
    expect(wrapper.emitted('update:checked')).toEqual([[true]])
    await wrapper.setProps({ checked: true })
    wrapper.element.click()
    await nextTick()
    expect(wrapper.emitted('update:checked')).toEqual([[true], [false]])
    wrapper.unmount()
  })

  it('announces and renders a partially selected permission tree', async () => {
    const wrapper = mount(UiCheckbox, { props: { checked: false, label: '系统设置', indeterminate: true } })
    const input = wrapper.get('input')
    expect(input.attributes('aria-checked')).toBe('mixed')
    expect((input.element as HTMLInputElement).indeterminate).toBe(true)
    await wrapper.setProps({ checked: true, indeterminate: false })
    expect(input.attributes('aria-checked')).toBe('true')
    expect((input.element as HTMLInputElement).indeterminate).toBe(false)
    expect((input.element as HTMLInputElement).checked).toBe(true)
    wrapper.unmount()
  })

  it('prevents a disabled checkbox from changing the model', async () => {
    const wrapper = mount(UiCheckbox, { props: { checked: false, label: '管理员', disabled: true }, attachTo: document.body })
    wrapper.element.click()
    await nextTick()
    expect((wrapper.get('input').element as HTMLInputElement).disabled).toBe(true)
    expect(wrapper.emitted('update:checked')).toBeUndefined()
    wrapper.unmount()
  })

  it('associates each visible label with its own native input', () => {
    const wrapper = mount(defineComponent({
      components: { UiCheckbox },
      template: '<UiCheckbox :checked="false" label="管理员" /><UiCheckbox :checked="false" label="查看者" />',
    }))
    const inputs = wrapper.findAll('input')
    const labels = wrapper.findAll('label')
    expect(inputs[0]?.attributes('id')).not.toBe(inputs[1]?.attributes('id'))
    expect(labels.map(label => label.attributes('for'))).toEqual(inputs.map(input => input.attributes('id')))
    expect(inputs.map(input => input.attributes('aria-label'))).toEqual(['管理员', '查看者'])
    wrapper.unmount()
  })
})
