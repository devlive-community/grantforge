// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { pageResource } from './manifest'

describe('page resources', () => {
  it('names the resource that grants a managed page', () => expect(pageResource('/admin/users')).toBe('system.user'))
  it('leaves open pages and unknown paths without a resource', () => {
    expect(pageResource('/dashboard')).toBeUndefined()
    expect(pageResource('toString')).toBeUndefined()
  })
})
