// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { vi } from 'vitest'
import { base64Url } from './pkce.js'

/** The discovery document of a GrantForge at https://gf.example. */
export const DISCOVERY = {
  issuer: 'https://gf.example', authorization_endpoint: 'https://gf.example/oauth2/authorize', token_endpoint: 'https://gf.example/oauth2/token',
  revocation_endpoint: 'https://gf.example/oauth2/revoke', userinfo_endpoint: 'https://gf.example/userinfo',
}

/** A JWT with the given claims and no real signature. */
export function jwt(claims: Record<string, unknown>): string {
  const encode = (value: unknown) => base64Url(new TextEncoder().encode(JSON.stringify(value)))
  return `${encode({ alg: 'none' })}.${encode(claims)}.`
}

/** A JSON response. */
export function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(status === 304 ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
}

/** A fetch answering by URL, recording every call. */
export function fakeFetch(answer: (url: string, init: RequestInit | undefined) => Response | Promise<Response>) {
  return vi.fn<typeof fetch>(async (input, init) => answer(String(input), init))
}
