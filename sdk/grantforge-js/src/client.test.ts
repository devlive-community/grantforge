// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { GrantForgeClient, type UserAuthorization } from './client.js'
import { fakeFetch, json } from './testing.js'

const ADA: UserAuthorization = { application: 'shop', accountId: '42', tenantId: '3', username: 'ada', version: 7, roles: ['sellers'],
  resources: ['shop', 'shop.orders'], permissions: ['orders.read'], computedAt: '2026-10-04T00:00:00Z' }

describe('open API client', () => {
  it('keeps the answer for the TTL and revalidates it with its ETag', async () => {
    let now = 0
    const answers = [json(ADA, 200, { ETag: '"7"' }), json(null, 304), json({ ...ADA, permissions: ['orders.read', 'orders.delete'] })]
    const fetcher = fakeFetch(() => answers.shift() ?? json({}, 500))
    const client = new GrantForgeClient({ baseUrl: 'https://gf.example/', accessToken: async () => 't1', fetch: fetcher, now: () => now })

    expect(await Promise.all([client.authorization(), client.can('orders.read')])).toEqual([ADA, true])
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(fetcher.mock.calls[0]?.[0]).toBe('https://gf.example/api/v1/open/me/authorization')
    expect(await client.hasResource('shop.orders')).toBe(true)
    now += 31_000
    expect(await client.can('orders.delete')).toBe(false)
    expect((fetcher.mock.calls[1]?.[1]?.headers as Record<string, string>)['If-None-Match']).toBe('"7"')
    now += 31_000
    expect(await client.can('orders.delete')).toBe(true)
    expect(fetcher).toHaveBeenCalledTimes(3)
  })

  it('asks again for another token and forgets refused answers', async () => {
    let token: string | null = 't1'
    const answers = [json(ADA), json(ADA), json({}, 401), json({}, 503)]
    const fetcher = fakeFetch(() => answers.shift() ?? json({}, 500))
    const client = new GrantForgeClient({ baseUrl: 'https://gf.example', accessToken: async () => token, fetch: fetcher })

    await client.authorization()
    token = 't2'
    await client.authorization()
    expect((fetcher.mock.calls[1]?.[1]?.headers as Record<string, string>)['If-None-Match']).toBeUndefined()
    client.forget()
    await expect(client.authorization()).rejects.toMatchObject({ reason: 'unauthenticated', status: 401 })
    await expect(client.authorization()).rejects.toMatchObject({ reason: 'unavailable' })
    token = null
    await expect(client.authorization()).rejects.toMatchObject({ reason: 'unauthenticated' })
    const offline = new GrantForgeClient({ baseUrl: 'https://gf.example', accessToken: async () => 't', fetch: fakeFetch(() => { throw new TypeError('x') }) })
    await expect(offline.authorization()).rejects.toMatchObject({ reason: 'unavailable' })
  })
})
