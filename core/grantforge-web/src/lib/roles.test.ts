// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { roleLabel } from './roles'

describe('role labels', () => {
  it('translates system roles and keeps custom names', () => {
    expect(roleLabel({ type: 'SYSTEM', code: 'tenant-admin', name: 'Tenant administrator' })).toBe('租户管理员')
    expect(roleLabel({ type: 'SYSTEM', code: 'platform-admin', name: 'x' })).toBe('平台管理员')
    expect(roleLabel({ type: 'SYSTEM', code: 'future-role', name: 'Future' })).toBe('Future')
    expect(roleLabel({ type: 'CUSTOM', code: 'tenant-admin', name: '自定义' })).toBe('自定义')
  })
})
