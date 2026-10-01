// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

// The session itself is an HttpOnly cookie the console never sees; only the last login name is remembered
// so the sign-in form can prefill it.
const USERNAME_KEY = 'GrantForgeUserName'
// Bearer token of the pre-rebuild console; removed on sight so no stale credential lingers in storage.
const LEGACY_TOKEN_KEY = 'AuthXToken'

export function readUsername(): string { return localStorage.getItem(USERNAME_KEY) || '' }
export function rememberUsername(username: string): void {
  localStorage.setItem(USERNAME_KEY, username)
  localStorage.removeItem(LEGACY_TOKEN_KEY)
}
export function forgetLegacyToken(): void { localStorage.removeItem(LEGACY_TOKEN_KEY) }
