// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  handler: undefined as (() => void) | undefined,
  onUnauthorized: vi.fn(),
  authenticate: vi.fn(),
  request: vi.fn(),
}))
vi.mock('./lib/api', () => ({
  onUnauthorized: (handler: () => void) => { api.handler = handler },
  authenticate: api.authenticate,
  request: api.request,
}))

describe('application bootstrap', () => {
  it('mounts the app and sends the user to login when the server rejects the session', async () => {
    document.body.innerHTML = '<div id="app"></div>'

    await import('./main')
    const { default: router } = await import('./router')
    await router.push('/common/404')
    await flushPromises()
    localStorage.setItem('AuthXToken', 'token')

    expect(document.querySelector('#app')?.childElementCount).toBeGreaterThan(0)
    expect(api.handler).toBeTypeOf('function')

    api.handler?.()
    // The login view is loaded lazily, so wait for the navigation to settle.
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('login'))

    expect(localStorage.getItem('AuthXToken')).toBeNull()
    expect(router.currentRoute.value.query.redirect).toBe('/common/404')

    api.handler?.()
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
  })
})
