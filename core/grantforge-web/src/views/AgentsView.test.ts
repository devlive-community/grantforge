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

const { default: AgentsView } = await import('./AgentsView.vue')

const dw = { id: '7', name: 'dw', label: 'Data warehouse', serviceType: 'hive', enabled: true, available: true, values: {}, secretsSet: [] }
const lake = { ...dw, id: '8', name: 'lake', label: 'Lake' }
const key = { keyId: '0123456789abcdef', algorithm: 'Ed25519', publicKey: 'MCowBQYDK2VwAyEA' }
const agents = [
  { id: '1', instance: 'hs2-1', host: '10.0.0.5', agentVersion: '1.0.0', appliedPolicyVersion: 4, lastSeenAt: '2026-10-02T12:00:00Z', status: 'CURRENT' },
  { id: '2', instance: 'hs2-2', lastSeenAt: '2026-10-02T11:00:00Z', status: 'OUTDATED' },
  { id: '3', instance: 'hs2-3', lastSeenAt: '2026-10-01T11:00:00Z', status: 'SILENT' },
]
const tokens = [
  { id: '11', serviceId: '7', name: 'cluster-a', hint: 'gfa_AbCdEf', createdAt: '2026-10-01T00:00:00Z', usable: true, lastUsedAt: '2026-10-02T12:00:00Z' },
  { id: '12', serviceId: '7', name: 'old', hint: 'gfa_ZzZzZz', createdAt: '2026-09-01T00:00:00Z', usable: false, revokedAt: '2026-09-02T00:00:00Z' },
  { id: '13', serviceId: '7', name: 'short', hint: 'gfa_YyYyYy', createdAt: '2026-09-01T00:00:00Z', usable: false, expiresAt: '2026-09-02T00:00:00Z' },
]
type Call = [string, { method?: string, body?: Record<string, unknown> }?]
const calls = (method: string) => (api.request.mock.calls as Call[]).filter(([, options]) => options?.method === method)
function button(text: string) {
  const found = Array.from(document.querySelectorAll('button')).find(element => element.textContent?.trim() === text)
  if (!found) throw new Error(`no button ${text}`)
  return found
}

describe('agents view', () => {
  const writeText = vi.fn(() => Promise.resolve())
  beforeEach(() => {
    api.request.mockReset()
    writeText.mockReset()
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (options?.method === 'POST' && path.endsWith('/agent-tokens')) return Promise.resolve({ token: tokens[0], secret: 'gfa_AbCdEfSECRET' })
      if (options?.method) return Promise.resolve(null)
      if (path === '/api/v1/services') return Promise.resolve([dw, lake])
      if (path === '/api/v1/policy-signing-key') return Promise.resolve(key)
      if (path.endsWith('/agents')) return Promise.resolve(path.includes('/7/') ? agents : [])
      return Promise.resolve(path.includes('/7/') ? tokens : [])
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows how the agents of a service are doing, its tokens and the signing key', async () => {
    const { wrapper, router } = await mountView(AgentsView, {}, '/data/agents')
    await flushPromises()
    expect(wrapper.get('[data-agent="hs2-1"]').text()).toContain('已同步')
    expect(wrapper.get('[data-agent="hs2-1"]').text()).toContain('10.0.0.5 · 1.0.0')
    expect(wrapper.get('[data-agent="hs2-1"]').text()).toContain('策略版本 4')
    expect(wrapper.get('[data-agent="hs2-2"]').text()).toContain('待更新')
    expect(wrapper.get('[data-agent="hs2-3"]').text()).toContain('失联')
    expect(wrapper.get('[data-token="cluster-a"]').text()).toContain('gfa_AbCdEf…')
    expect(wrapper.get('[data-token="old"]').text()).toContain('已吊销')
    expect(wrapper.find('[data-token="old"] button').exists()).toBe(false)
    expect(wrapper.get('[data-token="short"]').text()).toContain('已过期')
    expect(wrapper.text()).toContain('0123456789abcdef · Ed25519')
    expect(wrapper.text()).toContain('/api/v1/agent/')
    await wrapper.get('[aria-label="复制公钥"]').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith('MCowBQYDK2VwAyEA')
    expect(useToast().items.map(item => item.message)).toContain('已复制')

    await router.replace('/data/agents?service=8')
    await flushPromises()
    expect(wrapper.text()).toContain('还没有代理上报心跳')
    expect(wrapper.text()).toContain('还没有令牌')
    wrapper.unmount()
  })

  it('issues a token and shows its secret once', async () => {
    const { wrapper } = await mountView(AgentsView, {}, '/data/agents?service=7')
    await flushPromises()
    button('签发令牌').click()
    await flushPromises()
    api.request.mockImplementationOnce(() => Promise.reject(new ApiError('请求不正确', 400, 0, null,
      { title: 'bad', status: 400, errors: [{ field: 'name', message: '请为令牌起名' }] })))
    document.querySelector<HTMLFormElement>('#token-form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(document.body.textContent).toContain('请为令牌起名')

    const [name, expiry] = Array.from(document.querySelectorAll<HTMLInputElement>('#token-form input'))
    if (!name || !expiry) throw new Error('missing inputs')
    name.value = ' cluster-b '; name.dispatchEvent(new Event('input'))
    expiry.value = '2027-01-01T08:00'; expiry.dispatchEvent(new Event('input'))
    document.querySelector<HTMLFormElement>('#token-form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    const [path, options] = calls('POST')[1] ?? []
    expect(path).toBe('/api/v1/services/7/agent-tokens')
    expect(options?.body).toEqual({ name: 'cluster-b', expiresAt: new Date('2027-01-01T08:00').toISOString() })
    expect(document.querySelector('[data-issued]')?.textContent).toContain('gfa_AbCdEfSECRET')
    writeText.mockRejectedValueOnce(new Error('denied'))
    document.querySelector<HTMLButtonElement>('[aria-label="复制令牌"]')?.click()
    await flushPromises()
    expect(useToast().items.map(item => item.message)).toContain('复制失败，请手动选择复制')
    button('完成').click()
    await flushPromises()
    expect(document.querySelector('[data-issued]')).toBeNull()
    wrapper.unmount()
  })

  it('revokes tokens and forgets agents', async () => {
    const { wrapper } = await mountView(AgentsView, {}, '/data/agents?service=7')
    await flushPromises()
    await wrapper.get('[aria-label="吊销 cluster-a"]').trigger('click')
    expect(document.body.textContent).toContain('吊销令牌“cluster-a”？')
    button('吊销').click()
    await flushPromises()
    expect(calls('POST')[0]?.[0]).toBe('/api/v1/agent-tokens/11/revoke')
    await wrapper.get('[aria-label="移除代理 hs2-3"]').trigger('click')
    await flushPromises()
    expect(calls('DELETE')[0]?.[0]).toBe('/api/v1/services/7/agents/3')
    expect(useToast().items.map(item => item.message)).toEqual(expect.arrayContaining(['令牌已吊销', '代理已移除，再次上报时会重新登记']))

    api.request.mockRejectedValue(new ApiError('无权操作', 403))
    await wrapper.get('[aria-label="移除代理 hs2-3"]').trigger('click')
    await wrapper.get('[aria-label="吊销 cluster-a"]').trigger('click')
    button('吊销').click()
    await flushPromises()
    expect(useToast().items.filter(item => item.message === '无权操作')).toHaveLength(2)
    wrapper.unmount()
  })

  it('explains what is missing', async () => {
    api.request.mockImplementation((path: string) => Promise.resolve(path === '/api/v1/policy-signing-key' ? key : []))
    const empty = await mountView(AgentsView, {}, '/data/agents')
    await flushPromises()
    expect(empty.wrapper.text()).toContain('还没有数据服务')
    empty.wrapper.unmount()
    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = await mountView(AgentsView, {}, '/data/agents')
    await flushPromises()
    expect(failed.wrapper.get('[role="alert"]').text()).toBe('无权访问')
    failed.wrapper.unmount()
  })
})
