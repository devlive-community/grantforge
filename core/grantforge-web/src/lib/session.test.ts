// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { beforeEach, describe, expect, it } from 'vitest'
import { forgetLegacyToken, readUsername, rememberUsername } from './session'
beforeEach(() => localStorage.clear())
describe('remembered sign-in name', () => {
  it('remembers the last login name and drops the legacy token', () => {
    localStorage.setItem('AuthXToken', 'legacy')
    expect(readUsername()).toBe('')
    rememberUsername('alex')
    expect(readUsername()).toBe('alex')
    expect(localStorage.getItem('AuthXToken')).toBeNull()
  })
  it('removes a legacy bearer token on request', () => {
    localStorage.setItem('AuthXToken', 'legacy')
    forgetLegacyToken()
    expect(localStorage.getItem('AuthXToken')).toBeNull()
  })
})
