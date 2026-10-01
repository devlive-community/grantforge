// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { mountView } from '../../tests/unit/mountView'
import ErrorView from './ErrorView.vue'

describe('error view', () => {
  it.each([
    ['403', 'ERROR 403', '这扇门，暂时没有为你打开'],
    ['404', 'ERROR 404', '这个页面似乎走丢了'],
    ['network', 'CONNECTION LOST', '连接暂时中断'],
  ] as const)('explains the %s state', async (status, code, title) => {
    const { wrapper } = await mountView(ErrorView, { props: { status } })
    expect(wrapper.text()).toContain(code)
    expect(wrapper.get('h1').text()).toBe(title)
    wrapper.unmount()
  })

  it('goes back or to the dashboard', async () => {
    const { wrapper, router } = await mountView(ErrorView, { props: { status: '404' } }, '/admin/users')
    await router.push('/common/404')
    const [back, home] = wrapper.findAll('button')
    await back?.trigger('click')
    await flushPromises()
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(router.currentRoute.value.path).toBe('/admin/users')
    await home?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/dashboard')
    wrapper.unmount()
  })
})
