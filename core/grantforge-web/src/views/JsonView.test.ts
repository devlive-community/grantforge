// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'
import JsonView from './JsonView.vue'

function button(label: string) {
  const match = [...document.body.querySelectorAll('button')].find(item => item.textContent?.includes(label))
  if (!match) throw new Error('missing button ' + label)
  return match
}

describe('json workspace', () => {
  afterEach(() => { document.body.innerHTML = '' })

  it('formats and compacts valid JSON and reports its size', async () => {
    const { wrapper } = await mountView(JsonView)
    await wrapper.get('[aria-label="JSON 输入"]').setValue('{"a":1,"b":[1,2]}')
    expect(wrapper.text()).toContain('1 行 · 17 字节')
    button('格式化').click()
    await nextTick()
    expect((wrapper.get('[aria-label="JSON 输出"]').element as HTMLTextAreaElement).value).toBe('{\n  "a": 1,\n  "b": [\n    1,\n    2\n  ]\n}')
    expect(wrapper.text()).toContain('语法有效')
    button('压缩').click()
    await nextTick()
    expect((wrapper.get('[aria-label="JSON 输出"]').element as HTMLTextAreaElement).value).toBe('{"a":1,"b":[1,2]}')
    wrapper.unmount()
  })

  it('shows syntax errors as an alert and clears the result', async () => {
    const { wrapper } = await mountView(JsonView)
    await wrapper.get('[aria-label="JSON 输入"]').setValue('{broken')
    button('格式化').click()
    await nextTick()
    expect(wrapper.get('[role="alert"]').text()).not.toBe('')
    expect(wrapper.find('[aria-label="JSON 输出"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('loads a sample and clears everything', async () => {
    const { wrapper } = await mountView(JsonView)
    button('载入示例').click()
    await nextTick()
    expect((wrapper.get('[aria-label="JSON 输入"]').element as HTMLTextAreaElement).value).toContain('GrantForge')
    button('清空').click()
    await nextTick()
    expect((wrapper.get('[aria-label="JSON 输入"]').element as HTMLTextAreaElement).value).toBe('')
    wrapper.unmount()
  })

  it('copies the result and reports clipboard failures', async () => {
    const writeText = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('denied'))
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    const { wrapper } = await mountView(JsonView)
    await wrapper.get('[aria-label="JSON 输入"]').setValue('[1]')
    button('格式化').click()
    await nextTick()
    await wrapper.get('[aria-label="复制 JSON 结果"]').trigger('click')
    await wrapper.get('[aria-label="复制 JSON 结果"]').trigger('click')
    await vi.waitFor(() => expect(useToast().items.map(item => item.kind)).toEqual(['success', 'error']))
    expect(writeText).toHaveBeenCalledWith('[\n  1\n]')
    wrapper.unmount()
  })

  it('downloads the result as a JSON file', async () => {
    const createObjectURL = vi.fn(() => 'blob:x')
    const revokeObjectURL = vi.fn()
    Object.assign(URL, { createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const { wrapper } = await mountView(JsonView)
    await wrapper.get('[aria-label="JSON 输入"]').setValue('{}')
    button('格式化').click()
    await nextTick()
    await wrapper.get('[aria-label="下载 JSON"]').trigger('click')
    expect(click).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:x')
    wrapper.unmount()
  })
})
