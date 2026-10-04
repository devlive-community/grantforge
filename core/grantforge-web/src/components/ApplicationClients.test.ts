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

const { default: ApplicationClients } = await import('./ApplicationClients.vue')

const web = {
  id: '11', applicationId: '3', clientId: 'gf_web', name: 'CRM web', type: 'CONFIDENTIAL', redirectUris: ['https://crm.example/cb'],
  scopes: ['openid'], grants: ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'], accessTokenMinutes: 15, refreshTokenHours: 720, enabled: true,
  secretRotatedAt: '2026-10-01T00:00:00Z', previousSecretExpiresAt: '2026-10-02T00:00:00Z', createdAt: '2026-10-01T00:00:00Z',
}
const spa = { ...web, id: '12', clientId: 'gf_spa', name: 'CRM app', type: 'PUBLIC', enabled: false, secretRotatedAt: null, previousSecretExpiresAt: null }

function answer(path: string, options?: { method?: string }) {
  if (options?.method === 'POST' && path.endsWith('/clients')) return Promise.resolve({ client: web, secret: 'S3CRET' })
  if (options?.method === 'POST') return Promise.resolve({ client: web, secret: 'N3W' })
  if (options?.method) return Promise.resolve(web)
  return Promise.resolve([web, spa])
}
const dialog = () => document.querySelector('dialog[open]')
function button(label: string) {
  const found = [...document.querySelectorAll<HTMLButtonElement>('dialog[open] button')].find(item => item.textContent?.trim() === label || item.getAttribute('aria-label') === label)
  if (!found) throw new Error('missing button ' + label)
  return found
}
function input(label: string) {
  const owner = [...document.querySelectorAll<HTMLLabelElement>('dialog[open] label')].find(item => item.textContent?.replace('*', '').trim() === label)
  const found = owner ? document.getElementById(owner.htmlFor) as HTMLInputElement | null : null
  if (!found) throw new Error('missing field ' + label)
  return found
}
async function type(label: string, value: string) {
  const field = input(label)
  field.value = value
  field.dispatchEvent(new Event('input'))
  await flushPromises()
}
async function click(label: string) {
  button(label).click()
  await flushPromises()
}
async function check(label: string) {
  document.querySelector<HTMLInputElement>(`dialog[open] input[aria-label="${label}"]`)?.click()
  await flushPromises()
}
async function pick(option: string) {
  document.querySelector<HTMLElement>('dialog[open] button[role="combobox"]')?.click()
  await flushPromises()
  Array.from(document.querySelectorAll<HTMLElement>('[role="option"]')).find(element => element.textContent?.includes(option))?.click()
  await flushPromises()
}
async function submit(id: string) {
  document.querySelector(`form#${id}`)?.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}
async function mountDialog() {
  const result = await mountView(ApplicationClients, { props: { modelValue: true, applicationId: '3', applicationName: 'CRM' } })
  await flushPromises()
  return result
}

describe('application clients', () => {
  const writeText = vi.fn(() => Promise.resolve())
  beforeEach(() => {
    api.request.mockReset(); api.request.mockImplementation(answer); writeText.mockReset()
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists the clients with their type, state and grants', async () => {
    const { wrapper } = await mountDialog()
    expect(api.request).toHaveBeenCalledWith('/api/v1/applications/3/clients')
    expect(dialog()?.textContent).toContain('CRM 的 OAuth 客户端')
    const first = document.querySelector('[data-client="CRM web"]')?.textContent ?? ''
    expect(first).toContain('gf_web')
    expect(first).toContain('机密')
    expect(first).toContain('授权码 · 刷新令牌')
    expect(first).toContain('旧密钥有效至')
    expect(document.querySelector('[data-client="CRM app"]')?.textContent).toContain('已停用')
    // Public clients have no secret to rotate.
    expect(() => button('为 CRM app 更换密钥')).toThrow()
    await click('复制 CRM web 的客户端 ID')
    expect(writeText).toHaveBeenCalledWith('gf_web')
    wrapper.unmount()
  })

  it('registers a client and shows its secret once', async () => {
    const { wrapper } = await mountDialog()
    await click('新建客户端')
    await type('名称', ' CRM web ')
    const redirect = input('回调地址')
    redirect.value = 'https://crm.example/cb'
    redirect.dispatchEvent(new Event('input'))
    redirect.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    await flushPromises()
    await check('profile')
    await submit('client-form')
    expect(api.request).toHaveBeenCalledWith('/api/v1/applications/3/clients', { method: 'POST', body: { type: 'CONFIDENTIAL', settings: {
      name: 'CRM web', redirectUris: ['https://crm.example/cb'], scopes: ['openid', 'profile'], grants: ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'],
      accessTokenMinutes: 15, refreshTokenHours: 720, enabled: true } } })
    expect(document.querySelector('[data-issued]')?.textContent).toContain('S3CRET')
    await click('复制密钥')
    expect(writeText).toHaveBeenCalledWith('S3CRET')
    await click('完成')
    expect(document.querySelector('[data-issued]')).toBeNull()
    expect(useToast().items.map(item => item.message)).toContain('客户端已注册')
    wrapper.unmount()
  })

  it('edits, rotates and deletes clients', async () => {
    const { wrapper } = await mountDialog()
    await click('编辑 CRM app')
    expect(input('名称').value).toBe('CRM app')
    await check('刷新令牌')
    await submit('client-form')
    expect(api.request).toHaveBeenCalledWith('/api/v1/clients/12', { method: 'PUT', body: expect.objectContaining({ grants: ['AUTHORIZATION_CODE'], enabled: false }) })

    await click('为 CRM web 更换密钥')
    await type('宽限期（小时，最多 168）', '2')
    await submit('rotate-form')
    expect(api.request).toHaveBeenCalledWith('/api/v1/clients/11/rotate-secret', { method: 'POST', body: { graceHours: 2 } })
    expect(document.querySelector('[data-issued]')?.textContent).toContain('N3W')
    await click('完成')

    await click('删除 CRM app')
    expect(dialog()?.textContent).toContain('删除 CRM app？')
    await click('删除')
    expect(api.request).toHaveBeenCalledWith('/api/v1/clients/12', { method: 'DELETE' })
    expect(useToast().items.map(item => item.message)).toEqual(expect.arrayContaining(['客户端已更新', '已生成新密钥', '客户端已删除']))
    wrapper.unmount()
  })

  it('shows refusals next to the fields they concern', async () => {
    api.request.mockImplementation((_path: string, options?: { method?: string }) => options?.method
      ? Promise.reject(new ApiError('客户端的部分设置不正确。', 400, 0, null, { errors: [{ field: 'settings.redirectUris[0]', message: '地址不对' },
        { field: 'grants', message: '授权方式不对' }] } as never))
      : Promise.resolve([web]))
    const { wrapper } = await mountDialog()
    await click('新建客户端')
    await pick('公开')
    await submit('client-form')
    expect(dialog()?.textContent).toContain('地址不对')
    expect(dialog()?.textContent).toContain('授权方式不对')
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('客户端的部分设置不正确。')
    await click('取消')
    expect(document.querySelector('[data-client="CRM web"]')).not.toBeNull()
    wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = await mountDialog()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('无权访问')
    failed.wrapper.unmount()
  })
})
