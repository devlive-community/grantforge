// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { authorizeTarget } from './authorize'

describe('authorize target', () => {
  it('follows only the authorization endpoint of this server', () => {
    expect(authorizeTarget('/oauth2/authorize?response_type=code&client_id=gf_a')).toBe('/oauth2/authorize?response_type=code&client_id=gf_a')
    expect(authorizeTarget('https://evil.example/oauth2/authorize?x')).toBeNull()
    expect(authorizeTarget('//evil.example/oauth2/authorize?x')).toBeNull()
    expect(authorizeTarget('/oauth2/authorize')).toBeNull()
    expect(authorizeTarget('/oauth2/authorize?\\evil')).toBeNull()
    expect(authorizeTarget('/oauth2/authorize?a\nb')).toBeNull()
    expect(authorizeTarget(['/oauth2/authorize?x'])).toBeNull()
    expect(authorizeTarget(undefined)).toBeNull()
  })
})
