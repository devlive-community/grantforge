// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { errorOf, GrantForgeError } from './errors.js'

/** The endpoints of GrantForge's authorization server, from its OpenID Connect discovery document. */
export interface Endpoints {
  issuer: string
  authorization: string
  token: string
  revocation: string | null
  userInfo: string | null
  endSession: string | null
}

/** Reads the discovery document of an issuer. */
export async function discover(issuer: string, fetcher: typeof fetch = fetch): Promise<Endpoints> {
  const base = issuer.replace(/\/+$/, '')
  let response: Response
  try {
    response = await fetcher(`${base}/.well-known/openid-configuration`, { headers: { Accept: 'application/json' } })
  } catch (failure) {
    throw new GrantForgeError('unavailable', `GrantForge at ${base} did not answer: ${String(failure)}`)
  }
  if (!response.ok) throw errorOf(response, 'The discovery document')
  const document = await response.json() as Record<string, unknown>
  const text = (name: string) => typeof document[name] === 'string' ? document[name] as string : null
  const authorization = text('authorization_endpoint'), token = text('token_endpoint')
  if (!authorization || !token) throw new GrantForgeError('unavailable', 'The discovery document lacks the authorization or token endpoint')
  return { issuer: text('issuer') ?? base, authorization, token, revocation: text('revocation_endpoint'), userInfo: text('userinfo_endpoint'),
    endSession: text('end_session_endpoint') }
}
