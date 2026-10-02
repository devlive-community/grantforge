// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuth } from '@/stores/auth'
import PermissionGuard from './PermissionGuard.vue'
import { authorization } from '../../tests/unit/authorization'

describe('permission guard', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows its content with the resource and the denied slot without it', async () => {
    useAuth().authorization = authorization([])
    const wrapper = mount(PermissionGuard, { props: { code: 'system.role.btn.grant' },
      slots: { default: '<p>allowed</p>', denied: '<p>denied</p>' } })
    expect(wrapper.text()).toBe('denied')
    useAuth().authorization = authorization(['system.role.btn.grant'])
    await nextTick()
    expect(wrapper.text()).toBe('allowed')
  })
})
