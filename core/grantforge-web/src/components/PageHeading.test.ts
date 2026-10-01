// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PageHeading from './PageHeading.vue'

describe('page heading', () => {
  it('renders the title as the page heading with its description and actions', () => {
    const wrapper = mount(PageHeading, { props: { title: '用户管理', description: '管理账号' }, slots: { default: '<button>新建</button>' } })
    expect(wrapper.get('h1').text()).toBe('用户管理')
    expect(wrapper.text()).toContain('管理账号')
    expect(wrapper.get('button').text()).toBe('新建')
    expect(wrapper.find('.badge').exists()).toBe(false)
  })

  it('shows an optional badge', () => {
    expect(mount(PageHeading, { props: { title: 't', description: 'd', badge: '3' } }).get('.badge').text()).toBe('3')
  })
})
