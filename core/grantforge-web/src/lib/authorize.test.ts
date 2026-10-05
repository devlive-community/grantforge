// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { authorizeTarget, federatedSignIn } from './authorize'

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

describe('federated sign-in', () => {
  it('starts at the source and comes back to the authorization or the page asked for', () => {
    expect(federatedSignIn('okta', null, null)).toBe('/api/v1/auth/federated/okta')
    expect(federatedSignIn('okta', '/oauth2/authorize?client_id=a', '/admin/users'))
      .toBe('/api/v1/auth/federated/okta?authorize=%2Foauth2%2Fauthorize%3Fclient_id%3Da')
    expect(federatedSignIn('a b', null, '/admin/users')).toBe('/api/v1/auth/federated/a%20b?redirect=%2Fadmin%2Fusers')
  })
})
