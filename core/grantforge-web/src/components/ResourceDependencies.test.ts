// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { i18n } from '@/i18n'
import { ApiError } from '@/lib/api'
import type { Resource } from '@/lib/catalog'
import { useToast } from '@/stores/toast'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: ResourceDependencies } = await import('./ResourceDependencies.vue')

const resource = (id: string, type: string, name: string) => ({ id, applicationId: '1', type, code: `code.${id}`, name, sortOrder: 0, depth: 0,
  visible: true, enabled: true, denyMode: 'HIDE', builtin: false }) as Resource
const resources = [resource('page', 'PAGE', '用户'), resource('edit', 'ACTION', '编辑'), resource('view', 'ACTION', '查看'),
  resource('read', 'API', 'api:system.user.read'), resource('update', 'API', '修改用户'), resource('system', 'MODULE', '系统')]
const around = {
  requires: [{ id: 'd1', resourceId: 'edit', dependsOnId: 'read', kind: 'REQUIRED', source: 'DECLARED' },
    { id: 'd2', resourceId: 'edit', dependsOnId: 'view', kind: 'OPTIONAL', source: 'MANUAL' }],
  requiredBy: [{ id: 'd3', resourceId: 'page', dependsOnId: 'edit', kind: 'REQUIRED', source: 'MANUAL' }],
}

function answer(path: string, options?: { method?: string }) {
  if (options?.method) return Promise.resolve(null)
  if (path === '/api/v1/applications/1/dependencies') return Promise.resolve([...around.requires, ...around.requiredBy])
  return Promise.resolve(path.includes('/read/') ? { requires: [], requiredBy: [] } : around)
}
function mountPanel(id = 'edit', canEdit = true) {
  const target = resources.find(item => item.id === id)
  return mount(ResourceDependencies, { props: { resource: target as Resource, resources, canEdit }, attachTo: document.body,
    global: { plugins: [i18n] } })
}
function dialogButton(label: string) {
  const found = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!found) throw new Error('missing dialog button ' + label)
  return found as HTMLButtonElement
}
const toasts = () => useToast().items.map(item => item.message)

describe('resource dependencies', () => {
  beforeEach(() => { setActivePinia(createPinia()); api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = '' })

  it('lists what the resource needs and what needs it', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/resources/edit/dependencies')
    const needs = wrapper.findAll('[data-dependency]')
    expect(needs[0]?.text()).toContain('接口')
    expect(needs[0]?.text()).toContain('api:system.user.read')
    expect(needs[0]?.text()).toContain('必需')
    expect(needs[0]?.text()).toContain('内置声明')
    // Declared dependencies cannot be removed; manual ones can.
    expect(needs[0]?.find('[aria-label^="移除"]').exists()).toBe(false)
    expect(needs[1]?.text()).toContain('可选')
    expect(wrapper.text()).toContain('被以下资源需要')
    expect(wrapper.text()).toContain('用户')
    wrapper.unmount()
  })

  it('adds, toggles and removes dependencies', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('button').find(item => item.text() === '添加依赖')?.trigger('click')
    await flushPromises()
    dialogButton('添加依赖').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请选择依赖的资源')
    document.querySelector<HTMLButtonElement>('dialog[open] [role="combobox"]')?.click()
    await flushPromises()
    // Only APIs, pages, tabs and buttons that are not the resource, already needed or needing it.
    expect([...document.querySelectorAll('[role="option"]')].map(item => item.textContent?.trim())).toEqual(['接口 · 修改用户 (code.update)'])
    document.querySelector<HTMLElement>('[role="option"]')?.click()
    await flushPromises()
    dialogButton('添加依赖').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/resources/edit/dependencies', { method: 'POST', body: { dependsOnId: 'update', kind: 'REQUIRED' } })

    const row = (id: string) => wrapper.get(`[data-dependency="${id}"]`)
    await row('d2').findAll('button').find(item => item.text() === '改为必需')?.trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/resource-dependencies/d2', { method: 'PUT', body: { kind: 'REQUIRED' } })
    await row('d1').findAll('button').find(item => item.text() === '改为可选')?.trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/resource-dependencies/d1', { method: 'PUT', body: { kind: 'OPTIONAL' } })
    await row('d2').get('[aria-label="移除对 查看 的依赖"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/resource-dependencies/d2', { method: 'DELETE' })
    expect(toasts()).toEqual(expect.arrayContaining(['依赖已添加', '依赖已更新', '依赖已移除']))
    wrapper.unmount()
  })

  it('shows refusals in the dialog or as a toast', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) => options?.method
      ? Promise.reject(new ApiError('这条依赖会形成循环', 409)) : answer(path))
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('button').find(item => item.text() === '添加依赖')?.trigger('click')
    await flushPromises()
    document.querySelector<HTMLButtonElement>('dialog[open] [role="combobox"]')?.click()
    await flushPromises()
    document.querySelector<HTMLElement>('[role="option"]')?.click()
    await flushPromises()
    dialogButton('添加依赖').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('这条依赖会形成循环')
    dialogButton('取消').click()
    await flushPromises()
    await wrapper.findAll('[data-dependency]')[1]?.get('[aria-label^="移除"]').trigger('click')
    await flushPromises()
    expect(toasts()).toContain('这条依赖会形成循环')
    wrapper.unmount()
  })

  it('opens the graph, is read-only without edit rights and only lists dependents of APIs', async () => {
    const wrapper = mountPanel('read', false)
    await flushPromises()
    expect(wrapper.text()).not.toContain('添加依赖')
    expect(wrapper.text()).not.toContain('需要\n')
    expect(wrapper.text()).toContain('没有资源需要它')
    await wrapper.findAll('button').find(item => item.text() === '依赖关系图')?.trigger('click')
    await flushPromises()
    expect(document.querySelector('dialog[open]')?.textContent).toContain('api:system.user.read 的依赖关系')
    wrapper.unmount()

    api.request.mockRejectedValue(new ApiError('无权访问', 403))
    const failed = mountPanel()
    await flushPromises()
    expect(failed.get('[role="alert"]').text()).toBe('无权访问')
    await failed.findAll('button').find(item => item.text() === '依赖关系图')?.trigger('click')
    await flushPromises()
    expect(toasts()).toContain('无权访问')
    failed.unmount()
  })
})
