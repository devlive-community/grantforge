// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { statusLabel } from './accessRequests'

describe('access request labels', () => {
  it('names every status', () => {
    expect(statusLabel('PENDING')).toBe('待审批')
    expect(statusLabel('APPROVED')).toBe('已授予')
    expect(statusLabel('REJECTED')).toBe('已驳回')
    expect(statusLabel('CANCELLED')).toBe('已撤回')
    expect(statusLabel('EXPIRED')).toBe('已到期')
    expect(statusLabel('REVOKED')).toBe('已撤销')
  })
})
