// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from './lib/api'

const api = vi.hoisted(() => ({
  handler: undefined as (() => void) | undefined,
  onUnauthorized: vi.fn(),
  request: vi.fn(),
}))
vi.mock('./lib/api', async importOriginal => ({
  ...await importOriginal<typeof import('./lib/api')>(),
  onUnauthorized: (handler: () => void) => { api.handler = handler },
  request: api.request,
}))

describe('application bootstrap', () => {
  it('mounts the app and sends the user to login when the server rejects the session', async () => {
    document.body.innerHTML = '<div id="app"></div>'

    await import('./main')
    const { default: router } = await import('./router')
    // The initial navigation waits for the bootstrap request; let it finish before navigating again.
    await router.isReady()
    await router.push('/common/404')
    await flushPromises()
    const { useAuth } = await import('./stores/auth')
    useAuth().me = { username: 'admin', tenantCode: 'default', tenantName: 'Default', systemAccount: true, passwordChangeRequired: false }

    expect(document.querySelector('#app')?.childElementCount).toBeGreaterThan(0)
    expect(api.handler).toBeTypeOf('function')

    // The server now answers that the session is gone.
    api.request.mockImplementation((path: string) => path === '/api/v1/me'
      ? Promise.reject(new ApiError('signed out', 401)) : Promise.resolve(null))
    api.handler?.()
    // The login view is loaded lazily, so wait for the navigation to settle.
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('login'))

    expect(useAuth().authenticated).toBe(false)
    expect(router.currentRoute.value.query.redirect).toBe('/common/404')

    api.handler?.()
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
  })
})
