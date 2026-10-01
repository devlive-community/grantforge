// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: SessionsView } = await import('./SessionsView.vue')

const chrome = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/128.0 Safari/537.36'
const mine = { id: '9007199254740993', username: 'admin', clientIp: '10.0.0.1', userAgent: chrome,
  signedInAt: '2026-10-01T08:00:00Z', lastSeenAt: '2026-10-01T09:00:00Z', current: true }
const bob = { id: '42', username: 'bob', displayName: 'Bob B', signedInAt: '2026-10-01T07:00:00Z',
  lastSeenAt: '2026-10-01T08:30:00Z', current: false }

function dialogButton(label: string) {
  const button = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!button) throw new Error('missing dialog button ' + label)
  return button as HTMLButtonElement
}

async function mountSessions() {
  const mounted = await mountView(SessionsView, {}, '/admin/sessions')
  await flushPromises()
  return mounted
}

describe('sessions view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string) => path === '/api/v1/sessions'
      ? Promise.resolve({ items: [mine, bob], page: 1, size: 20, total: 2 }) : Promise.resolve(null))
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists sessions with their device and marks the current one', async () => {
    const { wrapper } = await mountSessions()
    expect(api.request).toHaveBeenCalledWith('/api/v1/sessions', expect.objectContaining({ query: { page: 1, size: 20 } }))
    expect(wrapper.text()).toContain('2 个会话')
    expect(wrapper.text()).toContain('Chrome · macOS')
    expect(wrapper.text()).toContain('10.0.0.1')
    expect(wrapper.text()).toContain('Bob B')
    expect(wrapper.findAll('.badge').map(badge => badge.text())).toContain('当前会话')
    wrapper.unmount()
  })

  it('ends another session after confirmation and reloads', async () => {
    const { wrapper } = await mountSessions()
    await wrapper.get('[aria-label="结束 Bob B 的会话"]').trigger('click')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('该浏览器下次操作时需要重新登录')
    dialogButton('结束会话').click()
    await flushPromises()

    expect(api.request).toHaveBeenCalledWith('/api/v1/sessions/42', { method: 'DELETE' })
    expect(useToast().items.map(item => item.message)).toContain('会话已结束')
    expect(api.request.mock.calls.filter(([path]) => path === '/api/v1/sessions')).toHaveLength(2)
    wrapper.unmount()
  })

  it('signs out when the current session is ended', async () => {
    const { wrapper, router } = await mountSessions()
    const reset = vi.spyOn(useAuth(), 'reset')
    await wrapper.get('[aria-label="结束 admin 的会话"]').trigger('click')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('结束后你将立即退出登录')
    dialogButton('结束会话').click()
    await flushPromises()

    expect(api.request).toHaveBeenCalledWith('/api/v1/sessions/9007199254740993', { method: 'DELETE' })
    expect(reset).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/auth/login')
    wrapper.unmount()
  })

  it('keeps the dialog open with the reason when ending fails', async () => {
    const { wrapper } = await mountSessions()
    api.request.mockRejectedValueOnce(new ApiError('无权执行此操作。', 403))
    await wrapper.get('[aria-label="结束 Bob B 的会话"]').trigger('click')
    dialogButton('结束会话').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('无权执行此操作。')
    wrapper.unmount()
  })

  it('shows why the list failed', async () => {
    api.request.mockRejectedValueOnce(new ApiError('无权执行此操作。', 403))
    const { wrapper } = await mountSessions()
    expect(wrapper.text()).toContain('无权执行此操作。')
    wrapper.unmount()
  })

  it('steps back when ending sessions empties the last page', async () => {
    let total = 21
    api.request.mockImplementation((path: string, options: { query?: { page: number } }) => path === '/api/v1/sessions'
      ? Promise.resolve({ items: options.query?.page === 1 ? [mine] : [bob], page: options.query?.page, size: 20, total })
      : Promise.resolve(null))
    const { wrapper } = await mountSessions()
    await wrapper.get('[aria-label="下一页"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Bob B')

    total = 20
    await wrapper.get('[aria-label="结束 Bob B 的会话"]').trigger('click')
    dialogButton('结束会话').click()
    await flushPromises()

    const pages = api.request.mock.calls.filter(([path]) => path === '/api/v1/sessions').map(([, options]) => options.query.page)
    expect(pages).toEqual([1, 2, 2, 1])
    expect(wrapper.text()).toContain('admin')
    wrapper.unmount()
  })
})
