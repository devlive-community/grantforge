// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'
import { i18n } from '@/i18n'
import { useAuth } from '@/stores/auth'
import { vPermission } from './permission'
import { authorization } from '../../tests/unit/authorization'

const Buttons = defineComponent({
  directives: { permission: vPermission },
  props: { code: { type: String, required: true } },
  setup() { return { busy: ref(false) } },
  template: `<div>
    <button id="hidden" v-permission="code" style="display: inline-flex">a</button>
    <button id="disabled" v-permission:disable="code" :disabled="busy">b</button>
  </div>`,
})

describe('v-permission', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows everything until the authorization is loaded', () => {
    const wrapper = mount(Buttons, { props: { code: 'system.user.btn.edit' }, global: { plugins: [i18n] } })
    expect((wrapper.get('#hidden').element as HTMLElement).style.display).toBe('inline-flex')
    expect(wrapper.get<HTMLButtonElement>('#disabled').element.disabled).toBe(false)
  })

  it('hides or disables elements without the resource and follows changes', async () => {
    const auth = useAuth()
    auth.authorization = authorization(['system.user.btn.edit'])
    const wrapper = mount(Buttons, { props: { code: 'system.user.btn.delete' }, global: { plugins: [i18n] } })
    const hidden = wrapper.get('#hidden').element as HTMLElement, disabled = wrapper.get<HTMLButtonElement>('#disabled').element
    expect(hidden.style.display).toBe('none')
    expect(disabled.disabled).toBe(true)
    expect(disabled.getAttribute('aria-disabled')).toBe('')
    expect(disabled.title).toBe('你没有执行此操作的权限')

    // A re-render that resets `disabled` keeps the button disabled.
    ;(wrapper.vm as unknown as { busy: boolean }).busy = true
    await nextTick()
    ;(wrapper.vm as unknown as { busy: boolean }).busy = false
    await nextTick()
    expect(disabled.disabled).toBe(true)

    auth.authorization = authorization(['system.user.btn.delete'])
    await nextTick()
    expect(hidden.style.display).toBe('inline-flex')
    expect(disabled.hasAttribute('aria-disabled')).toBe(false)
    expect(disabled.title).toBe('')

    await wrapper.setProps({ code: 'system.user.btn.create' })
    expect(hidden.style.display).toBe('none')
    wrapper.unmount()
  })
})
