// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

const api = vi.hoisted(() => ({ request: vi.fn(), onStepUp: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: StepUpDialog } = await import('./StepUpDialog.vue')

function dialog() {
  const element = document.body.querySelector('dialog')
  if (!element) throw new Error('dialog not rendered')
  return element
}

describe('step-up dialog', () => {
  beforeEach(() => { api.request.mockReset(); api.onStepUp.mockReset() })

  it('asks once for concurrent calls and confirms with the code', async () => {
    const wrapper = mount(StepUpDialog, { attachTo: document.body })
    const ask = api.onStepUp.mock.calls[0]?.[0] as () => Promise<boolean>
    const first = ask(), second = ask()
    await nextTick()
    expect(dialog().hasAttribute('open')).toBe(true)
    expect(dialog().textContent).toContain('确认身份')

    dialog().querySelector('form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(dialog().querySelector('[role="alert"]')?.textContent).toContain('请输入验证码')

    api.request.mockRejectedValueOnce(new Error('验证码错误')).mockResolvedValueOnce(null)
    const input = dialog().querySelector('input') as HTMLInputElement
    input.value = ' 123456 '; input.dispatchEvent(new Event('input'))
    dialog().querySelector('form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(dialog().querySelector('[role="alert"]')?.textContent).toContain('验证码错误')
    dialog().querySelector('form')?.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(api.request).toHaveBeenLastCalledWith('/api/v1/auth/step-up', { method: 'POST', body: { code: '123456' }, stepUp: false })
    expect(await first).toBe(true)
    expect(await second).toBe(true)
    wrapper.unmount()
  })

  it('answers no when the user cancels', async () => {
    const wrapper = mount(StepUpDialog, { attachTo: document.body })
    const ask = api.onStepUp.mock.calls[0]?.[0] as () => Promise<boolean>
    const asked = ask()
    await nextTick()
    const cancel = [...dialog().querySelectorAll('footer button')].find(button => button.textContent?.includes('取消')) as HTMLButtonElement
    cancel.click()
    expect(await asked).toBe(false)
    expect(api.request).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
