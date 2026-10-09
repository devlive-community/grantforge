// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import UiTagInput from './UiTagInput.vue'

function render(values: string[], suggest?: (text: string) => Promise<string[]>) {
  const wrapper = mount(UiTagInput, { attachTo: document.body, props: { label: '用户', modelValue: values, hint: '提示', suggest,
    'onUpdate:modelValue': (next: string[]) => wrapper.setProps({ modelValue: next }) } })
  return wrapper
}

describe('tag input', () => {
  afterEach(() => { vi.useRealTimers(); document.body.innerHTML = '' })

  it('adds typed values on Enter or comma and removes them', async () => {
    const wrapper = render(['alice'])
    const input = wrapper.get('input')
    await input.setValue(' bob ')
    await input.trigger('keydown', { key: 'Enter' })
    await input.setValue('carol, dave,alice,')
    expect(wrapper.props('modelValue')).toEqual(['alice', 'bob', 'carol', 'dave'])
    await wrapper.get('[aria-label="移除 bob"]').trigger('click')
    expect(wrapper.props('modelValue')).toEqual(['alice', 'carol', 'dave'])
    await input.setValue('')
    await input.trigger('keydown', { key: 'Backspace' })
    expect(wrapper.props('modelValue')).toEqual(['alice', 'carol'])
    expect(wrapper.text()).toContain('提示')
    await wrapper.setProps({ error: '不存在：x' })
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).not.toContain('提示')
  })

  it('offers suggestions to pick with the mouse or the keyboard', async () => {
    vi.useFakeTimers()
    const suggest = vi.fn((text: string) => Promise.resolve(['alice', 'albert', 'bob'].filter(name => name.includes(text))))
    const wrapper = render(['albert'], suggest)
    const input = wrapper.get('input')
    await input.setValue('al')
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    expect(suggest).toHaveBeenLastCalledWith('al', expect.any(AbortSignal))
    expect(input.attributes('aria-expanded')).toBe('true')
    // Values already chosen are not offered again.
    expect(wrapper.findAll('[role="option"]').map(option => option.text())).toEqual(['alice'])
    await input.trigger('keydown', { key: 'ArrowDown' })
    expect(input.attributes('aria-activedescendant')).toMatch(/option-0$/)
    await input.trigger('keydown', { key: 'Enter' })
    expect(wrapper.props('modelValue')).toEqual(['albert', 'alice'])

    await input.setValue('b')
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    await input.trigger('keydown', { key: 'ArrowUp' })
    await input.trigger('keydown', { key: 'Escape' })
    expect(input.attributes('aria-expanded')).toBe('false')
    await input.trigger('focus')
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    await wrapper.get('[role="option"]').trigger('mousedown')
    expect(wrapper.props('modelValue')).toEqual(['albert', 'alice', 'bob'])

    suggest.mockRejectedValueOnce(new Error('offline'))
    await input.setValue('x')
    await vi.advanceTimersByTimeAsync(250)
    await flushPromises()
    expect(input.attributes('aria-expanded')).toBe('false')
    // A failure is not an empty result: it says why, and typing on still works.
    expect(wrapper.get('[role="status"]').text()).toContain('查找失败：offline')
    // Leaving the box keeps what was typed.
    await input.trigger('blur')
    await vi.advanceTimersByTimeAsync(200)
    expect(wrapper.props('modelValue')).toEqual(['albert', 'alice', 'bob', 'x'])
  })

  it('says while it looks up and when nothing matches', async () => {
    vi.useFakeTimers()
    let answer: (found: string[]) => void = () => undefined
    const wrapper = render([], () => new Promise(resolve => { answer = resolve }))
    const input = wrapper.get('input')
    await input.setValue('zz')
    await vi.advanceTimersByTimeAsync(250)
    expect(wrapper.get('[role="status"]').attributes('data-suggest')).toBe('loading')
    answer([])
    await flushPromises()
    expect(wrapper.get('[role="status"]').attributes('data-suggest')).toBe('empty')
    expect(wrapper.get('[role="status"]').text()).toContain('没有匹配的值')
    await input.trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('never lets an older lookup replace a newer one, whether it succeeds or fails', async () => {
    vi.useFakeTimers()
    const answers: { text: string; signal?: AbortSignal; settle: (found: string[] | Error) => void }[] = []
    const suggest = (text: string, signal?: AbortSignal) => new Promise<string[]>((resolve, reject) => {
      answers.push({ text, signal, settle: found => found instanceof Error ? reject(found) : resolve(found) })
    })
    const wrapper = render([], suggest)
    const input = wrapper.get('input')
    await input.setValue('a')
    await vi.advanceTimersByTimeAsync(250)
    await input.setValue('al')
    await vi.advanceTimersByTimeAsync(250)
    expect(answers.map(answer => answer.text)).toEqual(['a', 'al'])
    // The older lookup was cancelled when the newer one started.
    expect(answers[0]?.signal?.aborted).toBe(true)
    answers[1]?.settle(['alice'])
    await flushPromises()
    answers[0]?.settle(new Error('late failure'))
    await flushPromises()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.findAll('[role="option"]').map(option => option.text())).toEqual(['alice'])

    await input.setValue('b')
    await vi.advanceTimersByTimeAsync(250)
    await input.setValue('bo')
    await vi.advanceTimersByTimeAsync(250)
    answers[3]?.settle(new Error('newer failed'))
    await flushPromises()
    answers[2]?.settle(['bob'])
    await flushPromises()
    expect(wrapper.get('[role="status"]').text()).toContain('newer failed')
    expect(wrapper.findAll('[role="option"]').filter(option => option.isVisible())).toHaveLength(0)
  })
})
