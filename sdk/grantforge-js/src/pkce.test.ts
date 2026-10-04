// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { base64Url, challengeOf, claimsOf, randomToken } from './pkce.js'
import { jwt } from './testing.js'

describe('pkce', () => {
  it('makes random URL-safe tokens and S256 challenges', async () => {
    const token = randomToken()
    expect(token).toMatch(/^[A-Za-z0-9_-]{43}$/)
    expect(randomToken()).not.toBe(token)
    // The SHA-256 of the verifier in base64url, as Node's crypto computes it.
    expect(await challengeOf('dBjftJeZ4CVP-mJ92K9gEcPjXpUbbw6uJIQ0kQ3oA6Wk')).toBe('MKMmiOzSQGdn11-BI4_FBrfRTsl8D4FvdNycHRaQmZE')
    expect(base64Url(new Uint8Array([251, 255]))).toBe('-_8')
  })

  it('reads the claims of a JWT', () => {
    expect(claimsOf(jwt({ sub: '42', name: 'Ada Lovelace — 数学' }))).toEqual({ sub: '42', name: 'Ada Lovelace — 数学' })
    expect(() => claimsOf('nope')).toThrow('not a JWT')
  })
})
