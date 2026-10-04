// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: OAuthView } = await import('./OAuthView.vue')

const server = {
  issuer: 'https://id.example', discoveryUrl: 'https://id.example/.well-known/openid-configuration',
  keys: [
    { keyId: 'new-key', algorithm: 'RS256', activatedAt: '2026-10-04T00:00:00Z', active: true },
    { keyId: 'old-key', algorithm: 'RS256', activatedAt: '2026-07-01T00:00:00Z', retiredAt: '2026-10-04T00:00:00Z', publishedUntil: '2026-10-06T00:00:00Z', active: false },
  ],
}
const toasts = () => useToast().items.map(item => item.message)

describe('oauth view', () => {
  const writeText = vi.fn(() => Promise.resolve())
  beforeEach(() => {
    api.request.mockReset(); writeText.mockReset()
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    api.request.mockImplementation((_path: string, options?: { method?: string }) => Promise.resolve(options?.method ? server.keys[0] : server))
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows the issuer, endpoints and keys', async () => {
    const { wrapper } = await mountView(OAuthView, {}, '/platform/oauth')
    await flushPromises()
    expect(wrapper.get('[data-issuer]').text()).toBe('https://id.example')
    expect(wrapper.get('[data-discovery]').text()).toBe('https://id.example/.well-known/openid-configuration')
    expect(wrapper.text()).toContain('https://id.example/oauth2/token')
    expect(wrapper.get('[data-key="new-key"]').text()).toContain('签名中')
    expect(wrapper.get('[data-key="old-key"]').text()).toContain('已停用，公开至')
    await wrapper.get('[aria-label="复制发现文档地址"]').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith('https://id.example/.well-known/openid-configuration')
    wrapper.unmount()
  })

  it('rotates the signing key after confirmation and shows failures', async () => {
    const { wrapper } = await mountView(OAuthView, {}, '/platform/oauth')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '轮换密钥')?.trigger('click')
    await flushPromises()
    expect(document.querySelector('dialog[open]')?.textContent).toContain('当前密钥会在公钥集中保留两天')
    ;[...document.querySelectorAll<HTMLButtonElement>('dialog[open] button')].find(button => button.textContent?.trim() === '轮换密钥')?.click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/oauth/signing-keys/rotate', { method: 'POST' })
    expect(toasts()).toContain('已启用新的签名密钥')
    wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = await mountView(OAuthView, {}, '/platform/oauth')
    await flushPromises()
    expect(failed.wrapper.get('[role="alert"]').text()).toBe('无权访问')
    failed.wrapper.unmount()
  })
})
