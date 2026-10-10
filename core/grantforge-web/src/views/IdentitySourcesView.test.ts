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

const { default: IdentitySourcesView } = await import('./IdentitySourcesView.vue')

const ldap = {
  id: '1', code: 'corp', name: 'Corporate LDAP', type: 'LDAP', enabled: true, provisioning: true, secretSet: true, accounts: 3,
  syncIntervalMinutes: 60, lastSyncedAt: '2026-10-04T08:00:00Z', lastSyncSummary: 'found 3, created 1, updated 0, disabled 0',
  ldap: { url: 'ldap://ldap.example.com', baseDn: 'ou=people,dc=example,dc=com', bindDn: 'cn=reader', userFilter: '(uid={0})', usernameAttribute: 'uid',
    displayNameAttribute: 'cn', emailAttribute: 'mail', idAttribute: 'entryUUID', disableMissing: false },
}
const oidc = {
  id: '2', code: 'okta', name: 'Okta', type: 'OIDC', enabled: false, provisioning: false, secretSet: false, accounts: 0, callbackPath: '/api/v1/auth/federated/callback/okta',
  oidc: { issuer: 'https://login.example.com', clientId: 'grantforge', scopes: 'openid', usernameClaim: 'preferred_username', displayNameClaim: 'name', emailClaim: 'email' },
}
const toasts = () => useToast().items.map(item => item.message)
const dialog = () => document.querySelector('dialog[open]') as HTMLDialogElement
function field(label: string) {
  const found = [...dialog().querySelectorAll('label')].find(item => item.textContent?.replace('*', '').trim() === label)
  return dialog().querySelector<HTMLInputElement>(`[id="${found?.getAttribute('for') ?? 'missing'}"]`) as HTMLInputElement
}
/** The message on a field's own control, empty while it has none. */
function tipOf(label: string) { return document.getElementById(field(label).getAttribute('aria-describedby') || '')?.textContent ?? '' }
function fill(label: string, value: string) { const input = field(label); input.value = value; input.dispatchEvent(new Event('input')) }
function dialogButton(label: string) {
  return [...dialog().querySelectorAll<HTMLButtonElement>('button')].find(button => button.textContent?.trim() === label) as HTMLButtonElement
}
/** A field's message floats out of the dialog, so an element's words are read from the document as well. */
function textOf(root: Element | null | undefined) {
  const tips = Array.from(root?.querySelectorAll('[aria-describedby]') ?? [])
    .map(control => document.getElementById(control.getAttribute('aria-describedby') || '')?.textContent ?? '')
  return [root?.textContent ?? '', ...tips].join('')
}

describe('identity sources view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (path === '/api/v1/identity-sources' && !options?.method) return Promise.resolve([ldap, oidc])
      if (path.endsWith('/sync')) return Promise.resolve({ found: 3, created: 1, updated: 2, disabled: 0, problems: [], summary: '' })
      return Promise.resolve(null)
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows directories and providers with what can be done with them', async () => {
    const { wrapper } = await mountView(IdentitySourcesView, {}, '/admin/identity-sources')
    await flushPromises()
    const corp = wrapper.get('[data-source="corp"]')
    expect(corp.text()).toContain('LDAP 目录')
    expect(corp.text()).toContain('3 个账号 · 自动创建账号')
    expect(corp.get('[data-last-sync]').text()).toContain('found 3, created 1')
    expect(corp.text()).toContain('每 60 分钟')
    const okta = wrapper.get('[data-source="okta"]')
    expect(okta.get('[data-callback]').text()).toBe(`${window.location.origin}/api/v1/auth/federated/callback/okta`)
    expect(okta.find('[aria-label="同步 Okta"]').exists()).toBe(false)

    await corp.get('[aria-label="测试 Corporate LDAP"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/identity-sources/1/test', { method: 'POST' })
    expect(toasts()).toContain('目录连接正常')
    await corp.get('[aria-label="同步 Corporate LDAP"]').trigger('click')
    await flushPromises()
    expect(toasts()).toContain('同步完成：新建 1，更新 2，停用 0')
    api.request.mockRejectedValueOnce(new ApiError('无法连接身份源', 503))
    await corp.get('[aria-label="测试 Corporate LDAP"]').trigger('click')
    await flushPromises()
    expect(useToast().items.at(-1)).toMatchObject({ message: '无法连接身份源', kind: 'error' })
    wrapper.unmount()
  })

  it('adds a directory after checking the form', async () => {
    const { wrapper } = await mountView(IdentitySourcesView, {}, '/admin/identity-sources')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '添加身份源')?.trigger('click')
    await flushPromises()
    dialog().querySelector('form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(textOf(dialog())).toContain('请输入编码')
    expect(textOf(dialog())).toContain('请输入名称')
    fill('编码', 'corp2'); fill('名称', 'Branch')
    // Filling the code and the name is enough: their messages go without waiting for another submit.
    await flushPromises()
    expect(textOf(dialog())).not.toContain('请输入编码')
    expect(textOf(dialog())).not.toContain('请输入名称')
    // Each missing directory setting is marked on its own field.
    expect(tipOf('目录地址')).toBe('请输入目录地址')
    expect(tipOf('用户所在 Base DN')).toBe('请输入用户所在 Base DN')
    dialog().querySelector('form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    // The code and the name are filled in now, so only the directory is still missing.
    expect(textOf(dialog())).not.toContain('请输入编码')
    expect(textOf(dialog())).not.toContain('请输入名称')
    expect(tipOf('目录地址')).toBe('请输入目录地址')
    fill('目录地址', 'ldap://branch')
    await flushPromises()
    // The address is filled in, so only the Base DN is still marked.
    expect(tipOf('目录地址')).toBe('')
    expect(tipOf('用户所在 Base DN')).toBe('请输入用户所在 Base DN')
    fill('用户所在 Base DN', 'dc=branch'); fill('查询账号密码', 's3cret'); fill('同步间隔（分钟）', 'often')
    dialog().querySelector('form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    // The directory is filled in now, so only the interval is still wrong.
    expect(tipOf('用户所在 Base DN')).toBe('')
    expect(textOf(dialog())).toContain('请以整数分钟填写同步间隔')
    fill('同步间隔（分钟）', '30')
    dialog().querySelector('form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/identity-sources', { method: 'POST', body: expect.objectContaining({
      code: 'corp2', name: 'Branch', type: 'LDAP', secret: 's3cret', syncIntervalMinutes: 30, oidc: undefined,
      ldap: expect.objectContaining({ url: 'ldap://branch', baseDn: 'dc=branch' }) }) })
    expect(toasts()).toContain('身份源已添加')
    wrapper.unmount()
  })

  it('edits a provider keeping its secret and deletes sources', async () => {
    const { wrapper } = await mountView(IdentitySourcesView, {}, '/admin/identity-sources')
    await flushPromises()
    await wrapper.get('[aria-label="编辑 Okta"]').trigger('click')
    await flushPromises()
    expect(field('编码').disabled).toBe(true)
    fill('Issuer 地址', ''); fill('客户端 ID', '')
    dialog().querySelector('form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(tipOf('Issuer 地址')).toBe('请输入 Issuer 地址')
    expect(tipOf('客户端 ID')).toBe('请输入客户端 ID')
    fill('客户端 ID', 'console')
    fill('Issuer 地址', 'https://login.example.com/new')
    dialog().querySelector('form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/identity-sources/2', { method: 'PUT', body: expect.objectContaining({
      secret: undefined, ldap: undefined, syncIntervalMinutes: undefined, oidc: expect.objectContaining({ issuer: 'https://login.example.com/new' }) }) })
    expect(toasts()).toContain('身份源已保存')

    await wrapper.get('[aria-label="删除 Corporate LDAP"]').trigger('click')
    await flushPromises()
    api.request.mockRejectedValueOnce(new ApiError('有 3 个账号通过该身份源登录，请改为停用。', 409))
    dialogButton('删除').click()
    await flushPromises()
    expect(dialog().querySelector('[role="alert"]')?.textContent).toContain('请改为停用')
    dialogButton('删除').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/identity-sources/1', { method: 'DELETE' })
    expect(toasts()).toContain('身份源已删除')
    wrapper.unmount()
  })

  it('says when there are none or they failed to load', async () => {
    api.request.mockResolvedValueOnce([])
    const empty = await mountView(IdentitySourcesView, {}, '/admin/identity-sources')
    await flushPromises()
    expect(empty.wrapper.text()).toContain('还没有身份源')
    empty.wrapper.unmount()
    api.request.mockRejectedValueOnce(new ApiError('服务暂时不可用。', 503))
    const failed = await mountView(IdentitySourcesView, {}, '/admin/identity-sources')
    await flushPromises()
    expect(failed.wrapper.get('[role="alert"]').text()).toBe('服务暂时不可用。')
    failed.wrapper.unmount()
  })
})
