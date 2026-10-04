// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { LocationQueryValue } from 'vue-router'

const ENDPOINT = '/oauth2/authorize?'

/**
 * The authorization request an application's sign-in should continue with, from the sign-in page's `authorize` query.
 * Only the authorization server's own endpoint qualifies, so the parameter cannot send users elsewhere.
 */
export function authorizeTarget(value: LocationQueryValue | LocationQueryValue[] | undefined): string | null {
  if (typeof value !== 'string' || !value.startsWith(ENDPOINT) || value.includes('\\') || /[\r\n]/.test(value)) return null
  return value
}

/** Continues an application's sign-in at the authorization server, which is not a page of the console. */
export function continueAuthorization(target: string): void {
  window.location.assign(target)
}
