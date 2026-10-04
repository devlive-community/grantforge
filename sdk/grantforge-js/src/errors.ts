// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/** Why GrantForge could not, or would not, answer. */
export type GrantForgeReason = 'unauthenticated' | 'forbidden' | 'unavailable' | 'invalid'

/** A refusal or failure of GrantForge, with the HTTP status when there was one. */
export class GrantForgeError extends Error {
  constructor(readonly reason: GrantForgeReason, message: string, readonly status = 0) {
    super(message)
    this.name = 'GrantForgeError'
  }
}

/** Turns an unsuccessful response into an error: 401 needs a new sign-in, 403 a permission, anything else waits. */
export function errorOf(response: Response, what: string): GrantForgeError {
  const reason: GrantForgeReason = response.status === 401 ? 'unauthenticated' : response.status === 403 ? 'forbidden'
    : response.status === 400 ? 'invalid' : 'unavailable'
  return new GrantForgeError(reason, `${what} answered ${response.status}`, response.status)
}
