// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import UiField from './UiField.vue'

describe('field', () => {
  it('associates the label, marks required fields and updates the model', async () => {
    const wrapper = mount(UiField, { props: { modelValue: '', label: '用户名', required: true } })
    const input = wrapper.get('input')
    expect(wrapper.get('label').attributes('for')).toBe(input.attributes('id'))
    expect(wrapper.get('[aria-hidden="true"]').text()).toBe('*')
    expect(input.attributes('required')).toBeDefined()
    await input.setValue('admin')
    expect(wrapper.emitted('update:modelValue')).toEqual([['admin']])
  })

  it('links validation errors to the input', async () => {
    const wrapper = mount(UiField, { props: { modelValue: 'x', label: '邮箱', error: '格式错误' }, attachTo: document.body })
    const input = wrapper.get('input')
    expect(input.attributes('aria-invalid')).toBe('true')
    await nextTick()
    // The tip is portalled out of the field, so its text lives in the document rather than in the wrapper.
    const tip = document.getElementById(input.attributes('aria-describedby') || '')
    expect(tip?.getAttribute('role')).toBe('alert')
    expect(tip?.textContent).toBe('格式错误')
    wrapper.unmount()
  })

  it('renders a textarea when requested', async () => {
    const wrapper = mount(UiField, { props: { modelValue: '', label: '描述', textarea: true } })
    await wrapper.get('textarea').setValue('text')
    expect(wrapper.emitted('update:modelValue')).toEqual([['text']])
  })

  it('steps numbers with labelled buttons and stops at the minimum', async () => {
    const wrapper = mount(UiField, { props: { modelValue: '1', label: '排序', type: 'number', min: 1 }, attachTo: document.body })
    const up = wrapper.get('[aria-label="增大排序"]')
    const down = wrapper.get('[aria-label="减小排序"]')
    expect(down.attributes('disabled')).toBeDefined()
    await up.trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['2'])
    await wrapper.setProps({ modelValue: '2' })
    await down.trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[1]).toEqual(['1'])
    wrapper.unmount()
  })

  it('ignores stepping while disabled', async () => {
    const wrapper = mount(UiField, { props: { modelValue: '3', label: '排序', type: 'number', disabled: true } })
    await wrapper.get('[aria-label="增大排序"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
