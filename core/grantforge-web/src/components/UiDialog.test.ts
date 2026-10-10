// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import UiDialog from './UiDialog.vue'

function dialog() {
  const element = document.body.querySelector('dialog')
  if (!element) throw new Error('dialog not rendered')
  return element
}

/** Styles as the browser has them, with the leaving animation on a closing dialog: jsdom has no stylesheet. */
function animated() {
  const real = window.getComputedStyle
  return vi.spyOn(window, 'getComputedStyle').mockImplementation((element, pseudo) => {
    const style = real(element, pseudo)
    if (!(element instanceof HTMLDialogElement) || !element.hasAttribute('data-closing')) return style
    return { ...style, animationName: 'leave', animationDuration: '0.16s' } as CSSStyleDeclaration
  })
}

describe('dialog', () => {
  afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers() })

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

  it('closes from the close button but never from a click on the backdrop', async () => {
    const wrapper = mount(UiDialog, { props: { modelValue: true, title: 't' }, attachTo: document.body })
    await nextTick()
    // A click on the backdrop lands on the dialog element itself.
    dialog().dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    dialog().querySelector<HTMLButtonElement>('[aria-label="关闭对话框"]')?.click()
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

  it('closes on Escape when idle, through its own leaving animation', async () => {
    const wrapper = mount(UiDialog, { props: { modelValue: true, title: 't' }, attachTo: document.body })
    await nextTick()
    const cancel = new Event('cancel', { cancelable: true })
    dialog().dispatchEvent(cancel)
    // The native close would skip the animation, so it is held back and the dialog closes itself.
    expect(cancel.defaultPrevented).toBe(true)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    wrapper.unmount()
  })

  it('plays its leaving animation before it closes, on a still copy of what it showed', async () => {
    animated()
    // A page clears what its dialog shows as it closes it, as pages usually do.
    const Page = defineComponent({
      props: { open: Boolean, title: { type: String, required: true } },
      setup: props => () => h(UiDialog, { modelValue: props.open, title: props.title },
        { default: () => props.open ? [h('input', { 'aria-label': '名称' }), h('p', '正文')] : [h('p', '已清空')] }),
    })
    const wrapper = mount(Page, { props: { open: true, title: '编辑服务' }, attachTo: document.body })
    await nextTick()
    const typed = dialog().querySelector<HTMLInputElement>('input')
    if (typed) typed.value = 'hive-prod'
    await wrapper.setProps({ open: false, title: '添加服务' })
    await nextTick()
    expect(dialog().hasAttribute('open')).toBe(true)
    expect(dialog().hasAttribute('data-closing')).toBe(true)
    // Nothing in a leaving dialog can be used any more.
    expect(dialog().hasAttribute('inert')).toBe(true)
    // What leaves is the dialog as it was: its heading and what was typed, whatever the page changed meanwhile.
    const copy = dialog().querySelector<HTMLElement>('[data-frozen]')
    expect(copy?.querySelector('h2')?.textContent).toBe('编辑服务')
    expect(copy?.querySelector('input')?.value).toBe('hive-prod')
    expect(copy?.textContent).toContain('正文')
    expect(copy?.textContent).not.toContain('已清空')
    dialog().dispatchEvent(new Event('animationend'))
    await nextTick()
    expect(dialog().hasAttribute('open')).toBe(false)
    expect(dialog().hasAttribute('data-closing')).toBe(false)
    expect(dialog().querySelector('[data-frozen]')).toBeNull()
    expect(dialog().querySelector('h2')?.textContent).toBe('添加服务')
    wrapper.unmount()
  })

  it('closes anyway when the animation never reports its end', async () => {
    animated()
    vi.useFakeTimers()
    const wrapper = mount(UiDialog, { props: { modelValue: true, title: 't' }, attachTo: document.body })
    await nextTick()
    await wrapper.setProps({ modelValue: false })
    await nextTick()
    expect(dialog().hasAttribute('open')).toBe(true)
    vi.advanceTimersByTime(400)
    expect(dialog().hasAttribute('open')).toBe(false)
    wrapper.unmount()
  })

  it('stays open when it is opened again while leaving', async () => {
    animated()
    const wrapper = mount(UiDialog, { props: { modelValue: true, title: 't' }, attachTo: document.body })
    await nextTick()
    await wrapper.setProps({ modelValue: false })
    await nextTick()
    await wrapper.setProps({ modelValue: true })
    await nextTick()
    expect(dialog().hasAttribute('open')).toBe(true)
    expect(dialog().hasAttribute('data-closing')).toBe(false)
    // The live contents are back in place of the copy.
    expect(dialog().querySelector('[data-frozen]')).toBeNull()
    dialog().dispatchEvent(new Event('animationend'))
    await nextTick()
    expect(dialog().hasAttribute('open')).toBe(true)
    wrapper.unmount()
  })
})
