import { beforeEach, describe, expect, it } from 'vitest'
import { clearSession, readToken, readUsername, saveSession, TOKEN_KEY, tokenUsername } from './session'
beforeEach(() => localStorage.clear())
describe('session compatibility', () => {
  it('persists the username separately from the token', () => {
    saveSession('opaque-token', 'alex')
    expect(readToken()).toBe('opaque-token'); expect(readUsername()).toBe('alex')
    clearSession(); expect(readToken()).toBe(''); expect(readUsername()).toBe('')
  })
  it('recovers the legacy Spring OAuth user_name for display', () => {
    const payload = btoa(JSON.stringify({ user_name: 'admin' })).replace(/=/g, '')
    localStorage.setItem(TOKEN_KEY, `header.${payload}.signature`)
    expect(readUsername()).toBe('admin')
  })
  it('rejects malformed claims rather than using a token as a username', () => {
    expect(tokenUsername('not.a.jwt')).toBe('')
    saveSession('opaque-token', ''); expect(readUsername()).toBe('')
  })
})
