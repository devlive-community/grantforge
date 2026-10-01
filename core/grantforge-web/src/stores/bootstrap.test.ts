// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', () => api)

const { useBootstrap } = await import('./bootstrap')

describe('bootstrap store', () => {
  beforeEach(() => {
    api.request.mockReset()
    setActivePinia(createPinia())
  })

  it('loads the server state once, anonymously', async () => {
    api.request.mockResolvedValue({ setupRequired: true, registrationEnabled: true })
    const store = useBootstrap()

    await Promise.all([store.load(), store.load()])
    await store.load()

    expect(api.request).toHaveBeenCalledTimes(1)
    expect(api.request).toHaveBeenCalledWith('/api/v1/bootstrap', { anonymous: true })
    expect(store.loaded).toBe(true)
    expect(store.setupRequired).toBe(true)
    expect(store.registrationEnabled).toBe(true)

    store.setupCompleted()
    expect(store.setupRequired).toBe(false)
  })

  it('treats a missing body as nothing pending', async () => {
    api.request.mockResolvedValue(null)
    const store = useBootstrap()

    await store.load()

    expect(store.loaded).toBe(true)
    expect(store.setupRequired).toBe(false)
    expect(store.registrationEnabled).toBe(false)
  })

  it('assumes nothing when the server is unreachable and retries later', async () => {
    api.request.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ setupRequired: true, registrationEnabled: false })
    const store = useBootstrap()

    await store.load()
    expect(store.loaded).toBe(false)
    expect(store.setupRequired).toBe(false)

    await store.load()
    expect(store.setupRequired).toBe(true)
  })
})
