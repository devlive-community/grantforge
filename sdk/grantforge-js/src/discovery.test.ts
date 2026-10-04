// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { discover } from './discovery.js'
import { DISCOVERY, fakeFetch, json } from './testing.js'

describe('discovery', () => {
  it('reads the endpoints of the issuer', async () => {
    const fetcher = fakeFetch(() => json(DISCOVERY))
    const endpoints = await discover('https://gf.example/', fetcher)
    expect(fetcher).toHaveBeenCalledWith('https://gf.example/.well-known/openid-configuration', expect.anything())
    expect(endpoints).toEqual({ issuer: 'https://gf.example', authorization: DISCOVERY.authorization_endpoint, token: DISCOVERY.token_endpoint,
      revocation: DISCOVERY.revocation_endpoint, userInfo: DISCOVERY.userinfo_endpoint, endSession: null })
  })

  it('fails when GrantForge does not answer or answers without endpoints', async () => {
    await expect(discover('https://gf.example', fakeFetch(() => { throw new TypeError('offline') }))).rejects.toMatchObject({ reason: 'unavailable' })
    await expect(discover('https://gf.example', fakeFetch(() => json({}, 500)))).rejects.toMatchObject({ reason: 'unavailable', status: 500 })
    await expect(discover('https://gf.example', fakeFetch(() => json({ issuer: 'x' })))).rejects.toMatchObject({ reason: 'unavailable' })
  })
})
