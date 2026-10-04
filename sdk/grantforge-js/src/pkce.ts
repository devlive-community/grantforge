// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/** Random URL-safe text of 32 bytes, for PKCE verifiers, states and nonces. */
export function randomToken(): string {
  const bytes = new Uint8Array(32)
  crypto.getRandomValues(bytes)
  return base64Url(bytes)
}

/** The S256 challenge of a PKCE verifier. */
export async function challengeOf(verifier: string): Promise<string> {
  // Verifiers are ASCII (RFC 7636); bytes of this realm, as some environments mix realms of typed arrays.
  const digest = await crypto.subtle.digest('SHA-256', Uint8Array.from(verifier, character => character.charCodeAt(0)))
  return base64Url(new Uint8Array(digest))
}

/** Base64url without padding, as OAuth uses it. */
export function base64Url(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** The claims of a JWT, read without checking its signature: the token came from GrantForge over TLS. */
export function claimsOf(jwt: string): Record<string, unknown> {
  const payload = jwt.split('.')[1]
  if (!payload) throw new Error('not a JWT')
  const padded = payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(payload.length / 4) * 4, '=')
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, character => character.charCodeAt(0))
  return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>
}
