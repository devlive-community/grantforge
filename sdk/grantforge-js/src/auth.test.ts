// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { beforeEach, describe, expect, it } from 'vitest'
import { GrantForgeAuth } from './auth.js'
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
  const auth = () => new GrantForgeAuth({ issuer: 'https://gf.example', clientId: 'gf_spa', redirectUri: 'https://shop.example/cb',
    fetch: fetcher, navigate: url => { navigated = url }, now: () => now })

  beforeEach(() => {
    sessionStorage.clear(); tokens.length = 0; navigated = ''; now = 1_000_000; fetcher.mockClear()
  })

  async function signIn(signer: GrantForgeAuth, idClaims: Record<string, unknown> = {}) {
    await signer.login('/orders')
    const sent = new URL(navigated).searchParams
    tokens.push({ access_token: 'a1', refresh_token: 'r1', expires_in: 900, id_token: jwt({ sub: '42', nonce: sent.get('nonce'), ...idClaims }) })
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

    sessionStorage.setItem('grantforge.tokens', JSON.stringify({ accessToken: 'x', refreshToken: null, idToken: null, expiresAt: 0 }))
    expect(await signer.accessToken()).toBeNull()
    sessionStorage.setItem('grantforge.tokens', '{broken')
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
})
