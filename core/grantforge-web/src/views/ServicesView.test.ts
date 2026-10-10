// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'

/** A validation tip floats out of its control, so an element's words are read from the document as well. */
function textOf(root: Element | null | undefined) {
  const tips = Array.from(root?.querySelectorAll('[aria-describedby]') ?? [])
    .map(control => document.getElementById(control.getAttribute('aria-describedby') || '')?.textContent ?? '')
  return [root?.textContent ?? '', ...tips].join('')
}
const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: ServicesView } = await import('./ServicesView.vue')

const hive = { name: 'hive', label: 'Hive', resources: [], configFields: [
  { name: 'url', label: 'JDBC URL', type: 'STRING', mandatory: true, options: [], description: 'Where Hive listens' },
  { name: 'timeout', label: 'Timeout', type: 'INTEGER', mandatory: false, options: [], defaultValue: '30' },
  { name: 'ssl', label: 'SSL', type: 'BOOLEAN', mandatory: false, options: [], defaultValue: 'true' },
  { name: 'mode', label: 'Mode', type: 'ENUM', mandatory: false, options: ['fast', 'safe'] },
  { name: 'notes', label: 'Notes', type: 'TEXT', mandatory: false, options: [] },
  { name: 'password', label: 'Password', type: 'SECRET', mandatory: true, options: [] },
] }
const prod = { id: '7', name: 'hive-prod', label: 'Hive production', serviceType: 'hive', serviceTypeLabel: 'Hive', enabled: true, available: true,
  values: { url: 'jdbc:hive2://prod', ssl: 'false' }, secretsSet: ['password'] }
const gone = { id: '8', name: 'old', label: 'Old', serviceType: 'hdfs', enabled: false, available: false, values: {}, secretsSet: [] }

type Call = [string, { method?: string, body?: Record<string, unknown> }?]
const calls = (method: string) => (api.request.mock.calls as Call[]).filter(([, options]) => options?.method === method)

describe('services view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (path === '/api/v1/service-types') return Promise.resolve([hive])
      if (path === '/api/v1/services/test') return Promise.resolve({ status: 'SUCCEEDED' })
      return Promise.resolve(options?.method ? prod : [prod, gone])
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists the services with their type and state', async () => {
    const { wrapper } = await mountView(ServicesView, {}, '/data/services')
    await flushPromises()
    expect(wrapper.text()).toContain('2 个服务')
    const row = wrapper.get('[data-service="hive-prod"]')
    expect(row.text()).toContain('Hive production')
    expect(row.text()).toContain('jdbc:hive2://prod')
    expect(row.text()).toContain('已启用')
    expect(row.get('[aria-label="管理 Hive production 的策略"]').attributes('href')).toBe('/data/policies?service=7')
    const old = wrapper.get('[data-service="old"]')
    expect(old.text()).toContain('类型 hdfs 不可用')
    expect(old.get('[aria-label="编辑 Old"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('creates a service from the settings its type asks for', async () => {
    const { wrapper } = await mountView(ServicesView, {}, '/data/services')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '添加服务')?.trigger('click')
    await flushPromises()
    const dialog = document.body
    const input = (selector: string) => {
      const found = dialog.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)
      if (!found) throw new Error(`no ${selector}`)
      return found
    }
    const type = (selector: string, value: string) => {
      const field = input(selector)
      field.value = value
      field.dispatchEvent(new Event('input'))
    }
    type('input[placeholder^="小写字母开头"]', 'hive-dev')
    type('[data-field="url"] input', ' jdbc:hive2://dev ')
    type('[data-field="notes"] textarea', 'for testing')
    type('[data-field="password"] input', 's3cret')
    expect(input('[data-field="password"] input').type).toBe('password')
    expect(input('[data-field="timeout"] input').placeholder).toBe('30')
    expect(dialog.querySelector('[data-field="ssl"] [role="switch"]')?.getAttribute('aria-checked')).toBe('true')
    expect(dialog.textContent).toContain('Where Hive listens')

    ;[...dialog.querySelectorAll('button')].find(button => button.textContent?.includes('测试连接'))?.click()
    await flushPromises()
    expect(calls('POST')[0]).toEqual(['/api/v1/services/test', { method: 'POST', body: { serviceType: 'hive', serviceId: undefined, name: 'hive-dev',
      values: { url: 'jdbc:hive2://dev', ssl: 'true', notes: 'for testing', password: 's3cret' } } }])
    expect(dialog.querySelector('[role="status"]')?.textContent).toBe('连接成功')

    input('#service-form').dispatchEvent(new Event('submit'))
    await flushPromises()
    const [path, options] = calls('POST')[1] ?? []
    expect(path).toBe('/api/v1/services')
    expect(options?.body).toMatchObject({ serviceType: 'hive', name: 'hive-dev', enabled: true,
      values: { url: 'jdbc:hive2://dev', ssl: 'true', notes: 'for testing', password: 's3cret' } })
    expect(useToast().items.map(item => item.message)).toContain('服务已添加')
    wrapper.unmount()
  })

  it('edits a service, keeping its secret unless a new one is typed, and shows field problems', async () => {
    const { wrapper } = await mountView(ServicesView, {}, '/data/services')
    await flushPromises()
    await wrapper.get('[aria-label="编辑 Hive production"]').trigger('click')
    await flushPromises()
    const password = document.querySelector<HTMLInputElement>('[data-field="password"] input')
    expect(password?.placeholder).toBe('已设置，留空则保持不变')
    expect(password?.required).toBe(false)
    expect(document.querySelector('[data-field="ssl"] [role="switch"]')?.getAttribute('aria-checked')).toBe('false')

    api.request.mockImplementation((_path: string, options?: { method?: string }) => options?.method === 'PUT'
      ? Promise.reject(new ApiError('配置无效', 400, 0, null, { title: 'invalid', status: 400, errors: [{ field: 'url', message: '必须填写' }] }))
      : Promise.resolve([prod]))
    document.querySelector<HTMLFormElement>('#service-form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    const [path, options] = calls('PUT')[0] ?? []
    expect(path).toBe('/api/v1/services/7')
    expect(options?.body?.values).toEqual({ url: 'jdbc:hive2://prod', ssl: 'false' })
    expect(textOf(document.querySelector('[data-field="url"]'))).toContain('必须填写')
    expect(document.querySelector('#service-form [role="alert"]')?.textContent).toBe('配置无效')
    wrapper.unmount()
  })

  it('marks a mandatory plugin property on its own field before it asks the server', async () => {
    const { wrapper } = await mountView(ServicesView, {}, '/data/services')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '添加服务')?.trigger('click')
    await flushPromises()
    const dialog = document.body
    const input = (selector: string) => {
      const found = dialog.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)
      if (!found) throw new Error(`no ${selector}`)
      return found
    }
    const type = (selector: string, value: string) => {
      const field = input(selector)
      field.value = value
      field.dispatchEvent(new Event('input'))
    }
    type('input[placeholder^="小写字母开头"]', 'hive-dev')
    dialog.querySelector<HTMLFormElement>('#service-form')?.dispatchEvent(new Event('submit'))
    await flushPromises()
    // The settings the plugin insists on are answered here, so the server is never asked about a form that cannot work.
    expect(calls('POST')).toEqual([])
    expect(textOf(dialog.querySelector('[data-field="url"]'))).toContain('请输入JDBC URL')
    expect(textOf(dialog.querySelector('[data-field="password"]'))).toContain('请输入Password')
    expect(dialog.querySelector('#service-form [role="alert"]')).toBeNull()

    type('[data-field="url"] input', 'jdbc:hive2://dev')
    await flushPromises()
    expect(textOf(dialog.querySelector('[data-field="url"]'))).not.toContain('请输入JDBC URL')
    expect(textOf(dialog.querySelector('[data-field="password"]'))).toContain('请输入Password')
    wrapper.unmount()
  })

  it('tests and deletes listed services', async () => {
    const { wrapper } = await mountView(ServicesView, {}, '/data/services')
    await flushPromises()
    api.request.mockImplementation((path: string) => path === '/api/v1/services/test'
      ? Promise.resolve({ status: 'FAILED', message: 'refused' }) : Promise.resolve([prod]))
    await wrapper.get('[aria-label="测试 Hive production 的连接"]').trigger('click')
    await flushPromises()
    await wrapper.get('[aria-label="删除 Hive production"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('删除“Hive production”？')
    ;[...document.querySelectorAll('dialog[open] button')].find(button => button.textContent?.trim() === '删除')
      ?.dispatchEvent(new MouseEvent('click'))
    await flushPromises()
    expect(calls('DELETE')[0]?.[0]).toBe('/api/v1/services/7')
    expect(useToast().items.map(item => item.message)).toEqual(expect.arrayContaining(['连接失败：refused', '服务已删除']))
    wrapper.unmount()
  })

  it('explains an empty list and load failures', async () => {
    api.request.mockResolvedValue([])
    const { wrapper } = await mountView(ServicesView, {}, '/data/services')
    await flushPromises()
    expect(wrapper.text()).toContain('还没有数据服务')
    expect(wrapper.text()).toContain('请先在“平台管理 → 插件”中安装插件')
    wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = await mountView(ServicesView, {}, '/data/services')
    await flushPromises()
    expect(failed.wrapper.get('[role="alert"]').text()).toBe('无权访问')
    failed.wrapper.unmount()
  })
})
