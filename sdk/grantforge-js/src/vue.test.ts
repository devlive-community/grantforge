// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, h, withDirectives, resolveDirective, ref } from 'vue'
import { GrantForgeClient, type UserAuthorization } from './client.js'
import { createGrantForge, useGrantForge } from './vue.js'
import { fakeFetch, json } from './testing.js'

const ADA: UserAuthorization = { application: 'shop', accountId: '42', tenantId: '3', username: 'ada', version: 7, roles: [],
  resources: ['shop.orders.btn.export'], permissions: ['orders.read'], computedAt: '2026-10-04T00:00:00Z' }

function client(answer: () => Response) {
  return new GrantForgeClient({ baseUrl: 'https://gf.example', accessToken: async () => 't', fetch: fakeFetch(answer), ttl: 0 })
}

const Page = defineComponent({
  setup() {
    const grantForge = useGrantForge()
    const code = ref('orders.delete')
    return { grantForge, code }
  },
  template: `
    <div>
      <button id="read" v-permission="'orders.read'" style="display: inline-flex">Read</button>
      <button id="delete" v-permission="code">Delete</button>
      <button id="both" v-permission="['orders.read', 'orders.delete']">Both</button>
      <button id="export" v-resource="'shop.orders.btn.export'">Export</button>
      <button id="greyed" v-permission.disable="'orders.delete'">Greyed</button>
      <span id="can">{{ grantForge.can('orders.read') }}</span>
    </div>`,
})

describe('vue plugin', () => {
  it('hides or disables elements the user may not use, and follows changes', async () => {
    const plugin = createGrantForge(client(() => json(ADA)))
    const wrapper = mount(Page, { global: { plugins: [plugin] } })
    await flushPromises()

    expect((wrapper.get('#read').element as HTMLElement).style.display).toBe('inline-flex')
    expect((wrapper.get('#delete').element as HTMLElement).style.display).toBe('none')
    expect((wrapper.get('#both').element as HTMLElement).style.display).toBe('none')
    expect((wrapper.get('#export').element as HTMLElement).style.display).toBe('')
    expect(wrapper.get('#greyed').attributes()).toMatchObject({ disabled: '', 'aria-disabled': 'true' })
    expect(wrapper.get('#can').text()).toBe('true')

    // The binding changes: the directive follows.
    wrapper.vm.code = 'orders.read'
    await flushPromises()
    expect((wrapper.get('#delete').element as HTMLElement).style.display).toBe('')

    // The permissions change: every guarded element follows.
    plugin.state.authorization.value = { ...ADA, permissions: ['orders.read', 'orders.delete'] }
    await flushPromises()
    expect((wrapper.get('#both').element as HTMLElement).style.display).toBe('')
    expect(wrapper.get('#greyed').attributes('disabled')).toBeUndefined()
    expect(plugin.state.hasResource('shop.orders.btn.export')).toBe(true)
    wrapper.unmount()
  })

  it('shows nothing guarded when GrantForge refuses, and says why', async () => {
    const plugin = createGrantForge(client(() => json({}, 401)))
    const wrapper = mount(Page, { global: { plugins: [plugin] } })
    await flushPromises()

    expect((wrapper.get('#read').element as HTMLElement).style.display).toBe('none')
    expect(plugin.state.error.value?.reason).toBe('unauthenticated')
    expect(plugin.state.authorization.value).toBeNull()
    wrapper.unmount()
  })

  it('needs the plugin for useGrantForge', () => {
    const Lonely = defineComponent({ setup() { useGrantForge(); return () => h('div') } })
    expect(() => mount(Lonely)).toThrow('createGrantForge')
    const Bare = defineComponent({ render: () => withDirectives(h('div'), [[resolveDirective('permission') ?? {}, undefined]]) })
    const plugin = createGrantForge(client(() => json(ADA)))
    expect(() => mount(Bare, { global: { plugins: [plugin] } }).unmount()).not.toThrow()
  })
})
