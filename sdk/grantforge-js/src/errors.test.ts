// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { errorOf, GrantForgeError } from './errors.js'

describe('errors', () => {
  it('names why GrantForge refused', () => {
    expect(errorOf(new Response(null, { status: 401 }), 'X').reason).toBe('unauthenticated')
    expect(errorOf(new Response(null, { status: 403 }), 'X').reason).toBe('forbidden')
    expect(errorOf(new Response(null, { status: 400 }), 'X').reason).toBe('invalid')
    const down = errorOf(new Response(null, { status: 503 }), 'The open API')
    expect(down).toBeInstanceOf(GrantForgeError)
    expect(down.reason).toBe('unavailable')
    expect(down.status).toBe(503)
    expect(down.message).toBe('The open API answered 503')
  })
})
