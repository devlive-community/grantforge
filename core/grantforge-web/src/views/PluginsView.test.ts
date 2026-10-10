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

const { default: PluginsView } = await import('./PluginsView.vue')

const hdfs = { id: 'hdfs', version: '1.2.0', name: 'HDFS', description: 'Hadoop file system', apiVersion: '1.0.0', source: 'EXTERNAL',
  location: 'hdfs.jar', status: 'ACTIVE', serviceTypes: [{ name: 'hdfs', label: 'HDFS', version: 3, resources: ['path'],
    accessTypes: ['read', 'write'], dataMask: false, rowFilter: true }] }
const plugins = [
  { ...hdfs, id: 'builtin-demo', name: 'Demo', source: 'BUILTIN', location: 'org.example.Demo', status: 'DISABLED', serviceTypes: [] },
  hdfs,
  { id: 'old', version: '0.1.0', name: 'Old', apiVersion: '0.9.0', source: 'EXTERNAL', location: 'old.zip', status: 'INCOMPATIBLE',
    problem: 'built for plugin API 0.9.0, this server provides 1.0.0', serviceTypes: [] },
]

const impact = { pluginId: 'hdfs', pluginName: 'HDFS', serviceTypes: ['hdfs'], services: [
  { tenantCode: 'acme', tenantName: 'Acme', id: '7', name: 'lake', label: 'Data lake', serviceType: 'hdfs', enabled: true, policies: 12, agents: 3 },
  { tenantCode: 'globex', tenantName: 'Globex', id: '9', name: 'archive', label: 'Archive', serviceType: 'hdfs', enabled: false, policies: 1, agents: 0 },
] }
const dialog = () => document.querySelector<HTMLDialogElement>('dialog[open]')
function dialogButton(label: string) {
  const found = [...document.querySelectorAll<HTMLButtonElement>('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!found) throw new Error('missing dialog button ' + label)
  return found
}
function typeId(value: string) {
  const input = document.querySelector<HTMLInputElement>('dialog[open] input')
  if (!input) throw new Error('missing confirmation input')
  input.value = value
  input.dispatchEvent(new Event('input'))
}

describe('plugins view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (path.endsWith('/impact')) return Promise.resolve(impact)
      return Promise.resolve(options?.method ? hdfs : plugins)
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists the plugins with their state, problems and service types', async () => {
    const { wrapper } = await mountView(PluginsView, {}, '/platform/plugins')
    await flushPromises()
    expect(wrapper.text()).toContain('3 个插件')
    const active = wrapper.get('[data-plugin="hdfs"]')
    expect(active.text()).toContain('运行中')
    expect(active.text()).toContain('版本 1.2.0 · 插件 API 1.0.0 · hdfs.jar')
    expect(active.get('[data-service-type="hdfs"]').text()).toContain('read、write')
    expect(active.text()).toContain('行过滤')
    expect(wrapper.get('[data-plugin="builtin-demo"]').text()).toContain('内置')
    const old = wrapper.get('[data-plugin="old"]')
    expect(old.text()).toContain('版本不兼容')
    expect(old.get('[role="note"]').text()).toBe('built for plugin API 0.9.0, this server provides 1.0.0')
    expect(old.findAll('button')).toHaveLength(0)
    wrapper.unmount()
  })

  it('shows what disabling a plugin affects and disables it only once its id is typed', async () => {
    const { wrapper } = await mountView(PluginsView, {}, '/platform/plugins')
    await flushPromises()
    await wrapper.get('[aria-label="停用 HDFS"]').trigger('click')
    await flushPromises()
    // Nothing is switched off yet: the console asks what it would affect first.
    expect(api.request).toHaveBeenCalledWith('/api/v1/plugins/hdfs/impact')
    expect(api.request).not.toHaveBeenCalledWith('/api/v1/plugins/hdfs/disable', { method: 'POST' })
    const shown = dialog()?.textContent ?? ''
    expect(shown).toContain('停用 HDFS 后，这些服务类型将不可用')
    expect(shown).toContain('2 个租户的 2 个服务受影响，共 13 条策略、3 个代理。')
    expect(shown).toContain('代理拿不到新的策略')
    expect(document.querySelector('[data-affected="lake"]')?.textContent).toContain('Data lake')
    expect(document.querySelector('[data-affected="archive"]')?.textContent).toContain('已停用')

    // The button stays off until the plugin's id is typed exactly.
    expect(dialogButton('停用插件').disabled).toBe(true)
    typeId('HDFS')
    await flushPromises()
    expect(dialogButton('停用插件').disabled).toBe(true)
    typeId('hdfs')
    await flushPromises()
    expect(dialogButton('停用插件').disabled).toBe(false)
    dialogButton('停用插件').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/plugins/hdfs/disable', { method: 'POST' })
    expect(useToast().items.map(item => item.message)).toContain('插件已停用')
    wrapper.unmount()
  })

  it('says when disabling affects no service, and offers another try when the impact cannot be worked out', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (path.endsWith('/impact')) return Promise.reject(new ApiError('服务暂时不可用', 503))
      return Promise.resolve(options?.method ? hdfs : plugins)
    })
    const { wrapper } = await mountView(PluginsView, {}, '/platform/plugins')
    await flushPromises()
    await wrapper.get('[aria-label="停用 HDFS"]').trigger('click')
    await flushPromises()
    expect(dialog()?.querySelector('[role="alert"]')?.textContent).toContain('无法分析影响：服务暂时不可用')
    // Without the impact there is nothing to confirm against.
    expect(dialogButton('停用插件').disabled).toBe(true)
    api.request.mockImplementation((path: string) => Promise.resolve(path.endsWith('/impact') ? { ...impact, services: [] } : plugins))
    dialogButton('重试').click()
    await flushPromises()
    expect(dialog()?.textContent).toContain('目前没有租户使用这些服务类型')
    dialogButton('取消').click()
    await flushPromises()
    expect(api.request).not.toHaveBeenCalledWith('/api/v1/plugins/hdfs/disable', { method: 'POST' })
    wrapper.unmount()
  })

  it('switches plugins on and looks them up again', async () => {
    const { wrapper } = await mountView(PluginsView, {}, '/platform/plugins')
    await flushPromises()
    await wrapper.get('[aria-label="启用 Demo"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/plugins/builtin-demo/enable', { method: 'POST' })
    await wrapper.findAll('button').find(button => button.text().includes('重新扫描'))?.trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/plugins/rescan', { method: 'POST' })
    expect(useToast().items.map(item => item.message)).toEqual(expect.arrayContaining(['插件已启用', '插件已重新扫描']))
    wrapper.unmount()
  })

  it('shows the empty state, refusals and load failures', async () => {
    api.request.mockImplementation((_path: string, options?: { method?: string }) => options?.method
      ? Promise.reject(new ApiError('你没有执行此操作的权限。', 403)) : Promise.resolve([]))
    const { wrapper } = await mountView(PluginsView, {}, '/platform/plugins')
    await flushPromises()
    expect(wrapper.text()).toContain('还没有安装插件')
    await wrapper.findAll('button').find(button => button.text().includes('重新扫描'))?.trigger('click')
    await flushPromises()
    expect(useToast().items.map(item => item.message)).toContain('你没有执行此操作的权限。')
    wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = await mountView(PluginsView, {}, '/platform/plugins')
    await flushPromises()
    expect(failed.wrapper.get('[role="alert"]').text()).toBe('无权访问')
    failed.wrapper.unmount()
  })
})
