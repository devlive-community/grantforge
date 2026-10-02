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

describe('plugins view', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((_path: string, options?: { method?: string }) => Promise.resolve(options?.method ? hdfs : plugins))
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

  it('switches plugins and looks them up again', async () => {
    const { wrapper } = await mountView(PluginsView, {}, '/platform/plugins')
    await flushPromises()
    await wrapper.get('[aria-label="停用 HDFS"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/plugins/hdfs/disable', { method: 'POST' })
    await wrapper.get('[aria-label="启用 Demo"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/plugins/builtin-demo/enable', { method: 'POST' })
    await wrapper.findAll('button').find(button => button.text().includes('重新扫描'))?.trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/plugins/rescan', { method: 'POST' })
    expect(useToast().items.map(item => item.message)).toEqual(expect.arrayContaining(['插件已停用', '插件已启用', '插件已重新扫描']))
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
