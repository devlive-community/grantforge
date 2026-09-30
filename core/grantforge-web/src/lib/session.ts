// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

export const TOKEN_KEY = 'AuthXToken'
const USERNAME_KEY = 'GrantForgeUserName'

export function readToken(): string { return localStorage.getItem(TOKEN_KEY) || '' }
export function tokenUsername(token: string): string {
  try {
    const part = token.split('.')[1]
    if (!part) return ''
    const decoded = atob(part.replace(/-/g, '+').replace(/_/g, '/'))
    const json: unknown = JSON.parse(new TextDecoder().decode(Uint8Array.from(decoded, c => c.charCodeAt(0))))
    if (!json || typeof json !== 'object') return ''
    const claims = json as Record<string, unknown>
    const name = claims.user_name ?? claims.sub
    return typeof name === 'string' ? name : ''
  } catch { return '' }
}
export function readUsername(): string { return localStorage.getItem(USERNAME_KEY) || tokenUsername(readToken()) }
export function saveSession(token: string, username: string): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USERNAME_KEY, username)
}
export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
}
