// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))
const { default: UserResourceGrants } = await import('./UserResourceGrants.vue')

afterEach(() => { document.body.innerHTML = ''; api.request.mockReset() })

it('allows an administrator to select resources and confirm a direct grant', async () => {
  api.request.mockImplementation((path: string, options?: { method?: string }) => {
    if (path.endsWith('/role-resources')) return Promise.resolve([])
    if (path === '/api/v1/applications') return Promise.resolve([{ id: '1', name: 'Shop', code: 'shop', builtin: false }])
    if (path.endsWith('/resources')) return Promise.resolve([{ id: '2', code: 'orders.read', name: 'Read orders', type: 'API', depth: 0, sortOrder: 0 }])
    if (options?.method === 'PUT') return Promise.resolve([{ resourceId: '2', expiresAt: null }])
    return Promise.resolve([])
  })
  const { wrapper } = await mountView(UserResourceGrants, { props: { modelValue: true, accountId: '42', accountName: 'Alice' } })
  await flushPromises()
  const box = document.querySelector<HTMLInputElement>('input[aria-label="允许 Read orders"]')
  expect(box).not.toBeNull()
  if (!box) throw new Error('grant checkbox missing')
  box.click()
  await flushPromises()
  const button = (text: string) => [...document.querySelectorAll('dialog[open] button')].find(el => el.textContent?.trim() === text) as HTMLButtonElement
  button('保存授权').click()
  await flushPromises()
  expect(api.request.mock.calls.some(call => call[1]?.method === 'PUT')).toBe(false)
  button('确认保存').click()
  await flushPromises()
  expect(api.request).toHaveBeenCalledWith('/api/v1/users/42/resource-grants', { method: 'PUT', body: {
    applicationId: '1', changes: [{ resourceId: '2', effect: 'ALLOW', expiresAt: null }],
  } })
  expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
  wrapper.unmount()
})


it('folds top-level resources and applies batch actions to all descendants while preserving expiry', async () => {
  api.request.mockImplementation((path: string, options?: { method?: string }) => {
    if (path.endsWith('/role-resources')) return Promise.resolve([])
    if (path === '/api/v1/applications') return Promise.resolve([{ id: '1', name: 'Shop', code: 'shop', builtin: false }])
    if (path.endsWith('/resources')) return Promise.resolve([
      { id: '10', code: 'orders', name: 'Orders', type: 'MODULE', depth: 0, sortOrder: 0 },
      { id: '11', parentId: '10', code: 'orders.page', name: 'Orders page', type: 'PAGE', depth: 1, sortOrder: 0 },
      { id: '12', parentId: '11', code: 'orders.edit', name: 'Edit', type: 'ACTION', depth: 2, sortOrder: 0 },
      { id: '20', code: 'other', name: 'Other', type: 'MODULE', depth: 0, sortOrder: 1 },
      { id: '21', parentId: '20', code: 'other.read', name: 'Other read', type: 'API', depth: 1, sortOrder: 0 },
    ])
    if (options?.method === 'PUT') return Promise.resolve([])
    return Promise.resolve([{ resourceId: '11', expiresAt: '2030-01-01T00:00:00Z' }])
  })
  const { wrapper } = await mountView(UserResourceGrants, { props: { modelValue: true, accountId: '42', accountName: 'Alice' } })
  await flushPromises()
  const row = (code: string) => document.querySelector<HTMLElement>(`[data-resource="${code}"]`)
  const root = row('orders')
  if (!root) throw new Error('missing root')
  expect(root.className).toContain('bg-sky-50')
  const expiry = row('orders.page')?.querySelector<HTMLInputElement>('input[type="datetime-local"]')
  const originalExpiry = expiry?.value
  root.querySelector<HTMLButtonElement>('button[aria-label="批量清除 Orders 下的授权"]')?.click()
  await flushPromises()
  expect(row('orders.page')?.querySelector<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(false)
  root.querySelector<HTMLButtonElement>('[aria-expanded]')?.click()
  await flushPromises()
  expect(row('orders.page')).toBeNull()
  root.querySelector<HTMLButtonElement>('button[aria-label="批量允许 Orders 下的资源"]')?.click()
  await flushPromises()
  expect(row('other.read')?.querySelector<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(false)
  root.querySelector<HTMLButtonElement>('[aria-expanded]')?.click()
  await flushPromises()
  expect(row('orders.edit')?.querySelector<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(true)
  expect(row('orders.page')?.querySelector<HTMLInputElement>('input[type="datetime-local"]')?.value).toBe(originalExpiry)
  const button = (text: string) => [...document.querySelectorAll('dialog[open] button')].find(el => el.textContent?.trim() === text) as HTMLButtonElement
  button('保存授权').click(); await flushPromises(); button('确认保存').click(); await flushPromises()
  expect(api.request).toHaveBeenCalledWith('/api/v1/users/42/resource-grants', { method: 'PUT', body: {
    applicationId: '1', changes: [{ resourceId: '12', effect: 'ALLOW', expiresAt: null }],
  } })
  expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
  wrapper.unmount()
})

it('keeps pending changes visible when saving direct grants fails', async () => {
  api.request.mockImplementation((path: string, options?: { method?: string }) => {
    if (path.endsWith('/role-resources')) return Promise.resolve([])
    if (path === '/api/v1/applications') return Promise.resolve([{ id: '1', name: 'Shop', code: 'shop', builtin: false }])
    if (path.endsWith('/resources')) return Promise.resolve([{ id: '2', code: 'read', name: 'Read', type: 'API', depth: 0, sortOrder: 0 }])
    if (options?.method === 'PUT') return Promise.reject(new Error('failed'))
    return Promise.resolve([])
  })
  const { wrapper } = await mountView(UserResourceGrants, { props: { modelValue: true, accountId: '42', accountName: 'Alice' } })
  await flushPromises()
  document.querySelector<HTMLInputElement>('input[aria-label="允许 Read"]')?.click()
  await flushPromises()
  const button = (text: string) => [...document.querySelectorAll('dialog[open] button')].find(el => el.textContent?.trim() === text) as HTMLButtonElement
  button('保存授权').click(); await flushPromises(); button('确认保存').click(); await flushPromises()
  expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  expect(document.querySelector('dialog[open]')?.textContent).toContain('1 项未保存的修改')
  expect(document.querySelector('dialog[open] [role="alert"]')).not.toBeNull()
  wrapper.unmount()
})


it('checks and locks role-derived resources, and batch actions leave their direct grants intact', async () => {
  api.request.mockImplementation((path: string, options?: { method?: string }) => {
    if (path.endsWith('/role-resources')) return Promise.resolve(['11', '12'])
    if (path === '/api/v1/applications') return Promise.resolve([{ id: '1', name: 'Shop', code: 'shop', builtin: false }])
    if (path.endsWith('/resources')) return Promise.resolve([
      { id: '10', code: 'orders', name: 'Orders', type: 'MODULE', depth: 0, sortOrder: 0 },
      { id: '11', parentId: '10', code: 'orders.menu', name: 'Menu', type: 'MENU', depth: 1, sortOrder: 0 },
      { id: '12', parentId: '10', code: 'orders.page', name: 'Page', type: 'PAGE', depth: 1, sortOrder: 1 },
      { id: '13', parentId: '10', code: 'orders.edit', name: 'Edit', type: 'ACTION', depth: 1, sortOrder: 2 },
    ])
    if (options?.method === 'PUT') return Promise.resolve([])
    return Promise.resolve([{ resourceId: '12', expiresAt: null }])
  })
  const { wrapper } = await mountView(UserResourceGrants, { props: { modelValue: true, accountId: '42', accountName: 'Alice' } })
  await flushPromises()
  const row = (code: string) => document.querySelector<HTMLElement>(`[data-resource="${code}"]`)
  const menu = row('orders.menu')?.querySelector<HTMLInputElement>('input[type="checkbox"]')
  expect(menu?.checked).toBe(true)
  expect(menu?.disabled).toBe(true)
  expect(row('orders.menu')?.textContent).toContain('角色权限')
  expect(row('orders.page')?.textContent).toContain('角色权限及直接授权')
  const root = row('orders')
  root?.querySelector<HTMLButtonElement>('button[aria-label="批量清除 Orders 下的授权"]')?.click()
  await flushPromises()
  expect(document.querySelector('dialog[open]')?.textContent).toContain('0 项未保存的修改')
  root?.querySelector<HTMLButtonElement>('button[aria-label="批量允许 Orders 下的资源"]')?.click()
  await flushPromises()
  const button = (text: string) => [...document.querySelectorAll('dialog[open] button')].find(el => el.textContent?.trim() === text) as HTMLButtonElement
  button('保存授权').click(); await flushPromises(); button('确认保存').click(); await flushPromises()
  expect(api.request).toHaveBeenCalledWith('/api/v1/users/42/resource-grants', { method: 'PUT', body: {
    applicationId: '1', changes: [{ resourceId: '13', effect: 'ALLOW', expiresAt: null }],
  } })
  wrapper.unmount()
})
