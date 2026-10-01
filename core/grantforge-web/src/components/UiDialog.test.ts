// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import UiDialog from './UiDialog.vue'

function dialog() {
  const element = document.body.querySelector('dialog')
  if (!element) throw new Error('dialog not rendered')
  return element
}

describe('dialog', () => {
  it('opens modally, is labelled by its title and renders the footer slot', async () => {
    const wrapper = mount(UiDialog, {
      props: { modelValue: true, title: '创建用户', description: '填写信息' },
      slots: { default: '<p>body</p>', footer: '<button>保存</button>' },
      attachTo: document.body,
    })
    await nextTick()
    expect(dialog().hasAttribute('open')).toBe(true)
    const heading = document.getElementById(dialog().getAttribute('aria-labelledby') ?? '')
    expect(heading?.textContent).toBe('创建用户')
    expect(dialog().textContent).toContain('填写信息')
    expect(dialog().querySelector('footer')?.textContent).toContain('保存')
    wrapper.unmount()
  })

  it('closes from the close button and the backdrop', async () => {
    const wrapper = mount(UiDialog, { props: { modelValue: true, title: 't' }, attachTo: document.body })
    await nextTick()
    dialog().querySelector<HTMLButtonElement>('[aria-label="关闭对话框"]')?.click()
    dialog().dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    await wrapper.setProps({ modelValue: false })
    await nextTick()
    expect(dialog().hasAttribute('open')).toBe(false)
    wrapper.unmount()
  })

  it('stays open on Escape and backdrop clicks while busy', async () => {
    const wrapper = mount(UiDialog, { props: { modelValue: true, title: 't', busy: true }, attachTo: document.body })
    await nextTick()
    const cancel = new Event('cancel', { cancelable: true })
    dialog().dispatchEvent(cancel)
    dialog().dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(cancel.defaultPrevented).toBe(true)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(dialog().querySelector('[aria-label="关闭对话框"]')?.hasAttribute('disabled')).toBe(true)
    wrapper.unmount()
  })

  it('closes on Escape when idle', async () => {
    const wrapper = mount(UiDialog, { props: { modelValue: true, title: 't' }, attachTo: document.body })
    await nextTick()
    dialog().dispatchEvent(new Event('cancel', { cancelable: true }))
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    wrapper.unmount()
  })
})
