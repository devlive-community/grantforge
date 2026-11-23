// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { beforeEach, describe, expect, it } from 'vitest'
import { GrantForgeAuth } from './auth.js'
import { GrantForgeClient } from './client.js'
import { challengeOf } from './pkce.js'
import { DISCOVERY, fakeFetch, json, jwt } from './testing.js'

describe('sign-in', () => {
  let now = 1_000_000
  let navigated = ''
  const tokens: Record<string, unknown>[] = []
  const fetcher = fakeFetch((url, init) => {
    if (url.endsWith('/.well-known/openid-configuration')) return json(DISCOVERY)
    if (url === DISCOVERY.revocation_endpoint) return json({})
    const next = tokens.shift()
    return next ? json(next, typeof next.status === 'number' ? next.status : 200) : json({ error: 'invalid_grant' }, 400) && Promise.reject(new TypeError(String(init)))
  })
  const auth = (clientId = 'gf_spa', issuer = 'https://gf.example') => new GrantForgeAuth({ issuer, clientId, redirectUri: 'https://shop.example/cb',
    fetch: fetcher, navigate: url => { navigated = url }, now: () => now })

  beforeEach(() => {
    sessionStorage.clear(); tokens.length = 0; navigated = ''; now = 1_000_000; fetcher.mockClear()
  })

  async function signIn(signer: GrantForgeAuth, idClaims: Record<string, unknown> = {}, accessToken = 'a1') {
    await signer.login('/orders')
    const sent = new URL(navigated).searchParams
    tokens.push({ access_token: accessToken, refresh_token: 'r1', expires_in: 900, id_token: jwt({ sub: '42', nonce: sent.get('nonce'), ...idClaims }) })
    return { sent, finished: await signer.handleRedirect(`https://shop.example/cb?code=c1&state=${sent.get('state')}`) }
  }

  it('signs in with the authorization code and PKCE', async () => {
    const signer = auth()
    const { sent, finished } = await signIn(signer, { preferred_username: 'ada' })

    expect(navigated.startsWith(DISCOVERY.authorization_endpoint + '?')).toBe(true)
    expect(Object.fromEntries(sent)).toMatchObject({ response_type: 'code', client_id: 'gf_spa', redirect_uri: 'https://shop.example/cb',
      scope: 'openid permissions', code_challenge_method: 'S256' })
    const exchange = fetcher.mock.calls.find(([url]) => url === DISCOVERY.token_endpoint)
    const form = new URLSearchParams(String(exchange?.[1]?.body))
    expect(Object.fromEntries(form)).toMatchObject({ grant_type: 'authorization_code', code: 'c1', client_id: 'gf_spa',
      redirect_uri: 'https://shop.example/cb' })
    expect(await challengeOf(form.get('code_verifier') ?? '')).toBe(sent.get('code_challenge'))
    expect(finished).toEqual({ returnTo: '/orders' })
    expect(signer.signedIn).toBe(true)
    expect(await signer.accessToken()).toBe('a1')
    expect(signer.user()).toMatchObject({ sub: '42', preferred_username: 'ada' })
    // The pending sign-in is used up.
    await expect(signer.handleRedirect(`https://shop.example/cb?code=c1&state=${sent.get('state')}`)).rejects.toMatchObject({ reason: 'invalid' })
  })

  it('refreshes once however many calls want a token, and signs out when the refresh is refused', async () => {
    const signer = auth()
    await signIn(signer)
    now += 900_000
    tokens.push({ access_token: 'a2', refresh_token: 'r2', expires_in: 900 })

    expect(await Promise.all([signer.accessToken(), signer.accessToken(), signer.accessToken()])).toEqual(['a2', 'a2', 'a2'])
    const refreshes = fetcher.mock.calls.filter(([, init]) => String(init?.body).includes('grant_type=refresh_token'))
    expect(refreshes).toHaveLength(1)
    expect(String(refreshes[0]?.[1]?.body)).toContain('refresh_token=r1')
    // The ID token stays when a refresh brings none.
    expect(signer.user()).toMatchObject({ sub: '42' })

    now += 900_000
    tokens.push({ error: 'invalid_grant', status: 400 })
    expect(await signer.accessToken()).toBeNull()
    expect(signer.signedIn).toBe(false)
  })

  it('keeps the tokens while GrantForge is down and gives up on tokens that cannot be refreshed', async () => {
    const signer = auth()
    await signIn(signer)
    now += 900_000
    await expect(signer.accessToken()).rejects.toMatchObject({ reason: 'unavailable' })
    expect(signer.signedIn).toBe(true)

    const key = 'grantforge.tokens.["https://gf.example","gf_spa"]'
    sessionStorage.setItem(key, JSON.stringify({ accessToken: 'x', refreshToken: null, idToken: null, expiresAt: 0 }))
    expect(await signer.accessToken()).toBeNull()
    sessionStorage.setItem(key, '{broken')
    expect(await signer.accessToken()).toBeNull()
    expect(signer.user()).toBeNull()
  })

  it('refuses answers that do not belong to a sign-in of this browser', async () => {
    const signer = auth()
    await expect(signer.handleRedirect('https://shop.example/cb?error=access_denied')).rejects.toMatchObject({ reason: 'forbidden' })
    await expect(signer.handleRedirect('https://shop.example/cb?error=server_error')).rejects.toMatchObject({ reason: 'invalid' })
    await expect(signer.handleRedirect('https://shop.example/cb?code=c&state=unknown')).rejects.toMatchObject({ reason: 'invalid' })
    await signer.login()
    const state = new URL(navigated).searchParams.get('state')
    tokens.push({ access_token: 'a1', id_token: jwt({ sub: '42', nonce: 'someone else' }) })
    await expect(signer.handleRedirect(`https://shop.example/cb?code=c1&state=${state}`)).rejects.toThrow('does not answer this sign-in')
    expect(signer.signedIn).toBe(false)
    await signer.login()
    const other = new URL(navigated).searchParams.get('state')
    tokens.push({ token_type: 'Bearer' })
    await expect(signer.handleRedirect(`https://shop.example/cb?code=c1&state=${other}`)).rejects.toMatchObject({ reason: 'unavailable' })
  })

  it('revokes the refresh token when signing out', async () => {
    const signer = auth()
    await signIn(signer)
    await signer.logout()

    expect(signer.signedIn).toBe(false)
    const revoked = fetcher.mock.calls.find(([url]) => url === DISCOVERY.revocation_endpoint)
    expect(String(revoked?.[1]?.body)).toContain('token=r1')
    await signer.logout()
    signer.forget()
  })

  it('keeps test and production authorization separate in the same tab', async () => {
    const test = auth('gf_test')
    const production = auth('gf_prod')
    const applications = new Map([['Bearer test-access', 'test'], ['Bearer prod-access', 'production']])
    const permissions = fakeFetch((_url, init) => {
      const application = applications.get(new Headers(init?.headers).get('Authorization') ?? '')
      return application ? json({ application }) : json({}, 401)
    })
    const client = (signer: GrantForgeAuth) => new GrantForgeClient({
      baseUrl: 'https://gf.example', accessToken: () => signer.accessToken(), fetch: permissions,
    })
    const testClient = client(test)

    await signIn(test, { name: 'Test user' }, 'test-access')
    expect((await testClient.authorization()).application).toBe('test')
    await signIn(production, { name: 'Production user' }, 'prod-access')

    expect((await client(production).authorization()).application).toBe('production')
    expect((await testClient.authorization()).application).toBe('test')
    expect(test.user()).toMatchObject({ name: 'Test user' })
    await production.logout()
    expect(production.signedIn).toBe(false)
    expect(await test.accessToken()).toBe('test-access')
  })

  it('isolates tokens and pending callbacks by issuer and client', async () => {
    const signer = auth()
    const otherIssuer = auth('gf_spa', 'https://other.example')
    const otherClient = auth('gf_other')
    await signer.login()
    const query = new URL(navigated).searchParams
    const callback = `https://shop.example/cb?code=c1&state=${query.get('state')}`

    await expect(otherClient.handleRedirect(callback)).rejects.toMatchObject({ reason: 'invalid' })
    await expect(otherIssuer.handleRedirect(callback)).rejects.toMatchObject({ reason: 'invalid' })
    tokens.push({ access_token: 'own-access', id_token: jwt({ nonce: query.get('nonce') }) })
    await signer.handleRedirect(callback)

    expect(await otherIssuer.accessToken()).toBeNull()
    expect(await otherClient.accessToken()).toBeNull()
    expect(await auth('gf_spa', 'https://gf.example/').accessToken()).toBe('own-access')
  })

  it('requires a fresh sign-in for legacy tokens and pending callbacks', async () => {
    sessionStorage.setItem('grantforge.tokens', JSON.stringify({
      accessToken: 'legacy-access', refreshToken: 'legacy-refresh', idToken: jwt({ sub: '42' }), expiresAt: now + 900_000,
    }))
    sessionStorage.setItem('grantforge.pending.legacy-state', JSON.stringify({ verifier: 'legacy-verifier', nonce: 'legacy', returnTo: null }))
    const signer = auth()

    expect(signer.signedIn).toBe(false)
    expect(signer.user()).toBeNull()
    expect(await signer.accessToken()).toBeNull()
    await expect(signer.handleRedirect('https://shop.example/cb?code=c1&state=legacy-state')).rejects.toMatchObject({ reason: 'invalid' })
    expect(fetcher).not.toHaveBeenCalled()
    await signIn(signer)
    expect(await signer.accessToken()).toBe('a1')
  })
})
