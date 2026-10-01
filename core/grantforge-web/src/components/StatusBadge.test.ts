// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatusBadge from './StatusBadge.vue'

describe('status badge', () => {
  it('shows active by default', () => { expect(mount(StatusBadge).text()).toBe('正常') })
  it('shows inactive accounts', () => { expect(mount(StatusBadge, { props: { active: false } }).text()).toBe('已停用') })
  it('gives locking precedence over the active flag', () => {
    expect(mount(StatusBadge, { props: { active: true, locked: true } }).text()).toBe('已锁定')
  })
})
