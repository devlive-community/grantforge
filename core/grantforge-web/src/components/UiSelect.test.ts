// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import UiSelect from './UiSelect.vue'

type Option = { value: string; label: string; description?: string; disabled?: boolean }
const options: Option[] = [
  { value: 'get', label: 'GET', description: '读取资源' },
  { value: 'head', label: 'HEAD', disabled: true },
  { value: 'post', label: 'POST', description: '创建资源' },
  { value: 'patch', label: 'PATCH' },
  { value: 'delete', label: 'DELETE' },
]
const wrappers: VueWrapper[] = []

function render(props: Partial<{ modelValue: string; label: string; options: Option[]; disabled: boolean; required: boolean; error: string; hideLabel: boolean }> = {}) {
  const wrapper = mount(UiSelect, { attachTo: document.body, props: { modelValue: 'get', label: 'HTTP 方法', options, ...props } })
  wrappers.push(wrapper)
  return wrapper
}
function combobox(wrapper: VueWrapper) { return wrapper.get<HTMLButtonElement>('[role="combobox"]') }
function activeOption(wrapper: VueWrapper) {
  const id = combobox(wrapper).attributes('aria-activedescendant')
  return id ? document.getElementById(id) : null
}
function option(label: string) {
  const element = Array.from(document.querySelectorAll<HTMLElement>('[role="option"]')).find(item => item.textContent?.includes(label))
  if (!element) throw new Error(`Missing option ${label}`)
  return element
}
async function key(wrapper: VueWrapper, value: string) {
  await combobox(wrapper).trigger('keydown', { key: value })
  await flushPromises()
}
async function open(wrapper: VueWrapper) {
  await combobox(wrapper).trigger('click')
  await flushPromises()
}

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  document.body.innerHTML = ''
})

describe('custom select interaction', () => {
  it('associates the accessible label and error with the control', () => {
    const wrapper = render({ required: true, error: '请选择请求方法' })
    const control = combobox(wrapper)
    const label = control.attributes('aria-label') || document.getElementById(control.attributes('aria-labelledby') || '')?.textContent
    expect(label).toContain('HTTP 方法')
    expect(control.attributes('aria-required')).toBe('true')
    expect(control.attributes('aria-invalid')).toBe('true')
    expect(document.getElementById(control.attributes('aria-describedby') || '')?.textContent).toContain('请选择请求方法')
  })

  it('opens at the selected option and skips disabled options without committing navigation', async () => {
    const wrapper = render()
    combobox(wrapper).element.focus()
    await key(wrapper, 'ArrowDown')
    expect(combobox(wrapper).attributes('aria-expanded')).toBe('true')
    expect(activeOption(wrapper)?.textContent).toContain('GET')
    expect(option('GET').getAttribute('aria-selected')).toBe('true')
    await key(wrapper, 'ArrowDown')
    expect(activeOption(wrapper)?.textContent).toContain('POST')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await key(wrapper, 'Enter')
    expect(wrapper.emitted('update:modelValue')).toEqual([['post']])
    expect(combobox(wrapper).attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(combobox(wrapper).element)
  })

  it('cancels exploratory navigation with Escape', async () => {
    const wrapper = render()
    await open(wrapper)
    await key(wrapper, 'End')
    expect(activeOption(wrapper)?.textContent).toContain('DELETE')
    await key(wrapper, 'Escape')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(combobox(wrapper).attributes('aria-expanded')).toBe('false')
    expect(document.querySelector('[role="listbox"]')).toBeNull()
  })

  it('supports Home, End and page navigation with Space to commit', async () => {
    const wrapper = render({ modelValue: 'post' })
    await open(wrapper)
    await key(wrapper, 'Home')
    expect(activeOption(wrapper)?.textContent).toContain('GET')
    await key(wrapper, 'PageDown')
    expect(activeOption(wrapper)?.textContent).toContain('DELETE')
    await key(wrapper, 'PageUp')
    expect(activeOption(wrapper)?.textContent).toContain('GET')
    await key(wrapper, 'End')
    await key(wrapper, ' ')
    expect(wrapper.emitted('update:modelValue')).toEqual([['delete']])
  })

  it('commits the highlighted option on Tab without preventing focus from leaving', async () => {
    const wrapper = render()
    await open(wrapper)
    await key(wrapper, 'ArrowDown')
    const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
    combobox(wrapper).element.dispatchEvent(event)
    await flushPromises()
    expect(event.defaultPrevented).toBe(false)
    expect(wrapper.emitted('update:modelValue')).toEqual([['post']])
    expect(combobox(wrapper).attributes('aria-expanded')).toBe('false')
  })

  it('locates an option by typed characters before the user commits it', async () => {
    const wrapper = render()
    await key(wrapper, 'p')
    expect(activeOption(wrapper)?.textContent).toContain('POST')
    await key(wrapper, 'a')
    expect(activeOption(wrapper)?.textContent).toContain('PATCH')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await key(wrapper, 'Enter')
    expect(wrapper.emitted('update:modelValue')).toEqual([['patch']])
  })

  it('cycles matching options when the user repeats a character', async () => {
    const wrapper = render()
    await key(wrapper, 'p')
    expect(activeOption(wrapper)?.textContent).toContain('POST')
    await key(wrapper, 'p')
    expect(activeOption(wrapper)?.textContent).toContain('PATCH')
    await key(wrapper, 'p')
    expect(activeOption(wrapper)?.textContent).toContain('POST')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('allows pointer selection while refusing disabled options', async () => {
    const wrapper = render()
    await open(wrapper)
    expect(option('HEAD').getAttribute('aria-disabled')).toBe('true')
    option('HEAD').click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    option('POST').click()
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toEqual([['post']])
    expect(combobox(wrapper).attributes('aria-expanded')).toBe('false')
  })

  it('prevents opening and changing a disabled control', async () => {
    const wrapper = render({ disabled: true, hideLabel: true })
    expect(combobox(wrapper).attributes('disabled')).toBeDefined()
    await open(wrapper)
    await key(wrapper, 'End')
    await key(wrapper, 'Enter')
    expect(combobox(wrapper).attributes('aria-expanded')).toBe('false')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('cancels pending navigation when the parent disables the open control', async () => {
    const wrapper = render()
    await open(wrapper)
    await key(wrapper, 'End')
    await wrapper.setProps({ disabled: true })
    expect(combobox(wrapper).attributes('aria-expanded')).toBe('false')
    expect(document.querySelector('[role="listbox"]')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('handles an empty option set without inventing a selected value', async () => {
    const wrapper = render({ modelValue: '', options: [] })
    await key(wrapper, 'ArrowDown')
    await key(wrapper, 'Home')
    await key(wrapper, 'Enter')
    expect(document.querySelectorAll('[role="option"]')).toHaveLength(0)
    expect(activeOption(wrapper)).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('reflects external model changes and safely reconciles replaced options while open', async () => {
    const wrapper = render()
    await wrapper.setProps({ modelValue: 'post' })
    expect(combobox(wrapper).text()).toContain('POST')
    await open(wrapper)
    expect(activeOption(wrapper)?.textContent).toContain('POST')
    await wrapper.setProps({ options: [{ value: 'delete', label: 'DELETE' }] })
    await flushPromises()
    await key(wrapper, 'Home')
    expect(activeOption(wrapper)?.textContent).toContain('DELETE')
    await key(wrapper, 'Enter')
    expect(wrapper.emitted('update:modelValue')).toEqual([['delete']])
  })

  it('preserves an external model update when a closed control receives and loses focus', async () => {
    const wrapper = render()
    await open(wrapper)
    await key(wrapper, 'Escape')
    await wrapper.setProps({ modelValue: 'post' })
    combobox(wrapper).element.focus()
    combobox(wrapper).element.blur()
    await flushPromises()
    expect(combobox(wrapper).text()).toContain('POST')
    expect(combobox(wrapper).attributes('aria-expanded')).toBe('false')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
