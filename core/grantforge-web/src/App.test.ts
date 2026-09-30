// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useToast } from '@/stores/toast'
import App from './App.vue'

describe('app shell', () => {
  beforeEach(() => { setActivePinia(createPinia()) })

  it('renders the routed view next to the toast hub', () => {
    useToast().show('欢迎')
    const wrapper = mount(App, { global: { stubs: { RouterView: { template: '<main>routed</main>' } } } })
    expect(wrapper.get('main').text()).toBe('routed')
    expect(wrapper.get('[aria-live]').text()).toContain('欢迎')
  })
})
