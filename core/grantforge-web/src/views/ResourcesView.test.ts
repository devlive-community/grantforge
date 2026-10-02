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

const { default: ResourcesView } = await import('./ResourcesView.vue')

const applications = [
  { id: '1', code: 'grantforge-console', name: 'GrantForge Console', description: 'The console', builtin: true, resources: 5 },
  { id: '9', code: 'crm', name: 'CRM', builtin: false, resources: 0 },
]
const base = { applicationId: '1', visible: true, enabled: true, denyMode: 'HIDE', builtin: false }
const resources = [
  { ...base, id: '10', type: 'MODULE', code: 'system', name: '系统管理', sortOrder: 0, depth: 0 },
  { ...base, id: '30', type: 'MODULE', code: 'audit', name: '审计', sortOrder: 1, depth: 0 },
  { ...base, id: '11', parentId: '10', type: 'PAGE', code: 'system.user.list', name: '用户管理', route: '/admin/users', sortOrder: 0, depth: 1, builtin: true },
  { ...base, id: '12', parentId: '10', type: 'PAGE', code: 'system.group.list', name: '用户组', sortOrder: 1, depth: 1, visible: false, enabled: false },
  { ...base, id: '13', parentId: '11', type: 'ACTION', code: 'system.user.btn.export', name: '导出', sortOrder: 0, depth: 2, denyMode: 'DISABLE', description: '导出用户' },
]

function answer(path: string, options?: { method?: string }) {
  if (path === '/api/v1/applications' && !options?.method) return Promise.resolve(applications)
  if (path === '/api/v1/applications/1/resources' && !options?.method) return Promise.resolve(resources)
  if (path === '/api/v1/applications/9/resources' && !options?.method) return Promise.resolve([])
  if (path.endsWith('/dependencies') && !options?.method) return Promise.resolve(path.startsWith('/api/v1/applications') ? [] : { requires: [], requiredBy: [] })
  if (options?.method === 'DELETE') return Promise.resolve(null)
  if (path === '/api/v1/applications') return Promise.resolve(applications[1])
  return Promise.resolve(resources[3])
}

async function mountCatalog(platform = true) {
  const mounted = await mountView(ResourcesView, {}, '/platform/resources')
  useAuth().authorization = { version: 1, unrestricted: platform, roles: [], resources: [], permissions: [] }
  await flushPromises()
  return mounted
}
type Wrapper = Awaited<ReturnType<typeof mountCatalog>>['wrapper']

async function select(wrapper: Wrapper, id: string) {
  await wrapper.get(`[role="treeitem"][data-id="${id}"]`).trigger('click')
  await flushPromises()
}
const button = (wrapper: Wrapper, label: string) => {
  const found = wrapper.findAll('button').find(item => item.text().trim() === label)
  if (!found) throw new Error('missing button ' + label)
  return found
}
function dialogButton(label: string) {
  const found = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!found) throw new Error('missing dialog button ' + label)
  return found as HTMLButtonElement
}
function field(label: string) {
  const owner = [...document.querySelectorAll<HTMLLabelElement>('dialog[open] label')]
    .find(item => item.textContent?.replace('*', '').trim() === label)
  const input = owner ? document.getElementById(owner.htmlFor) as HTMLInputElement | null : null
  if (!input) throw new Error('missing field ' + label)
  return input
}
async function fill(label: string, value: string) {
  const input = field(label)
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}
const calls = (method: string) => api.request.mock.calls.filter(call => call[1]?.method === method)
const toasts = () => useToast().items.map(item => item.message)

describe('resource catalog view', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows the first application, its tree with type badges and the selected resource', async () => {
    const { wrapper } = await mountCatalog()
    expect(wrapper.findAll('[role="treeitem"]').map(item => item.attributes('data-id'))).toEqual(['10', '11', '13', '12', '30'])
    expect(wrapper.text()).toContain('5 个资源')
    expect(wrapper.text()).toContain('模块系统管理')
    expect(wrapper.text()).toContain('在左侧选择一个资源')
    await select(wrapper, '13')
    expect(wrapper.text()).toContain('系统管理 / 用户管理')
    expect(wrapper.text()).toContain('显示为禁用')
    expect(wrapper.text()).toContain('导出用户')
    expect(wrapper.findAll('button').map(item => item.text().trim())).not.toContain('添加下级资源')
    await select(wrapper, '12')
    expect(wrapper.text()).toContain('已停用 · 导航隐藏')
    await select(wrapper, '11')
    expect(wrapper.text()).toContain('/admin/users')
    expect(button(wrapper, '删除').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('creates a child resource with the types its parent allows', async () => {
    const { wrapper } = await mountCatalog()
    await select(wrapper, '11')
    await button(wrapper, '添加下级资源').trigger('click')
    await flushPromises()
    expect(document.querySelector('dialog[open]')?.textContent).toContain('上级资源：用户管理')
    dialogButton('新建资源').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请输入资源名称')
    await fill('资源名称', '编辑')
    dialogButton('新建资源').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请输入资源编码')
    await fill('资源编码', 'system.user.btn.edit')
    // A page holds tabs and buttons; tabs have a route, so the route field shows for the default type.
    expect(field('前端路由')).toBeTruthy()
    dialogButton('新建资源').click()
    await flushPromises()
    expect(calls('POST')[0]).toEqual(['/api/v1/applications/1/resources', { method: 'POST', body: {
      parentId: '11', type: 'TAB', code: 'system.user.btn.edit', name: '编辑', description: '', route: '', visible: true, enabled: true, denyMode: 'HIDE',
    } }])
    expect(toasts()).toContain('资源已创建')
    wrapper.unmount()
  })

  it('creates top-level APIs without route or deny mode', async () => {
    const { wrapper } = await mountCatalog()
    await button(wrapper, '新建顶级资源').trigger('click')
    await flushPromises()
    expect(document.querySelector('dialog[open]')?.textContent).toContain('资源将放在应用的顶级')
    await fill('资源名称', '查询用户')
    await fill('资源编码', 'api:GET:/api/v1/users')
    document.querySelector<HTMLButtonElement>('dialog[open] [role="combobox"]')?.click()
    await flushPromises()
    const option = [...document.querySelectorAll<HTMLElement>('[role="option"]')].find(item => item.textContent?.trim() === '接口')
    option?.click()
    await flushPromises()
    expect(() => field('前端路由')).toThrow()
    dialogButton('新建资源').click()
    await flushPromises()
    expect(calls('POST')[0]?.[1].body).toMatchObject({ parentId: null, type: 'API', route: null, visible: true, denyMode: 'HIDE' })
    wrapper.unmount()
  })

  it('edits a resource; built-in resources keep their code', async () => {
    const { wrapper } = await mountCatalog()
    await select(wrapper, '11')
    await button(wrapper, '编辑').trigger('click')
    await flushPromises()
    expect(field('资源编码').disabled).toBe(true)
    expect(field('前端路由').value).toBe('/admin/users')
    await fill('资源名称', '用户')
    dialogButton('保存').click()
    await flushPromises()
    expect(calls('PUT')[0]).toEqual(['/api/v1/resources/11', { method: 'PUT', body: {
      code: 'system.user.list', name: '用户', description: '', route: '/admin/users', visible: true, enabled: true, denyMode: 'HIDE',
    } }])
    expect(toasts()).toContain('资源已更新')
    wrapper.unmount()
  })

  it('moves resources with the buttons, the dialog and by dragging', async () => {
    const { wrapper } = await mountCatalog()
    await select(wrapper, '12')
    await button(wrapper, '上移').trigger('click')
    await flushPromises()
    expect(calls('POST')[0]).toEqual(['/api/v1/resources/12/move', { method: 'POST', body: { parentId: '10', position: 0 } }])

    await select(wrapper, '13')
    await button(wrapper, '移动到…').trigger('click')
    await flushPromises()
    // A button only fits below pages.
    document.querySelector<HTMLButtonElement>('dialog[open] [role="combobox"]')?.click()
    await flushPromises()
    expect([...document.querySelectorAll('[role="option"]')].map(item => item.textContent?.trim())).toEqual(['— 用户管理', '— 用户组'])
    ;[...document.querySelectorAll<HTMLElement>('[role="option"]')].find(item => item.textContent?.includes('用户组'))?.click()
    await flushPromises()
    dialogButton('移动').click()
    await flushPromises()
    expect(calls('POST')[1]).toEqual(['/api/v1/resources/13/move', { method: 'POST', body: { parentId: '12', position: 0 } }])

    const row = (id: string) => {
      const item = wrapper.get(`[role="treeitem"][data-id="${id}"]`)
      item.element.getBoundingClientRect = () => ({ top: 0, height: 40 }) as DOMRect
      return item
    }
    // Drag the audit module before the system module.
    await row('30').trigger('dragstart')
    await row('10').trigger('dragover', { clientY: 2 })
    await row('10').trigger('drop')
    await flushPromises()
    expect(calls('POST')[2]).toEqual(['/api/v1/resources/30/move', { method: 'POST', body: { parentId: null, position: 0 } }])
    // Dropping a page inside the audit module puts it last there; dropping it after its sibling keeps the parent.
    await row('12').trigger('dragstart')
    await row('30').trigger('dragover', { clientY: 20 })
    await row('30').trigger('drop')
    await flushPromises()
    expect(calls('POST')[3]).toEqual(['/api/v1/resources/12/move', { method: 'POST', body: { parentId: '30', position: 0 } }])
    await row('11').trigger('dragstart')
    await row('12').trigger('dragover', { clientY: 38 })
    await row('12').trigger('drop')
    await flushPromises()
    expect(calls('POST')[4]).toEqual(['/api/v1/resources/11/move', { method: 'POST', body: { parentId: '10', position: 1 } }])
    // A button cannot go to the top level, and a module cannot go below its own page.
    await row('13').trigger('dragstart')
    await row('10').trigger('dragover', { clientY: 2 })
    expect(row('10').attributes('data-drop')).toBeUndefined()
    await row('10').trigger('dragstart')
    await row('11').trigger('dragover', { clientY: 20 })
    expect(row('11').attributes('data-drop')).toBeUndefined()
    expect(calls('POST')).toHaveLength(5)
    wrapper.unmount()
  })

  it('deletes resources and reports refusals', async () => {
    const { wrapper } = await mountCatalog()
    await select(wrapper, '13')
    await button(wrapper, '删除').trigger('click')
    await flushPromises()
    dialogButton('删除').click()
    await flushPromises()
    expect(calls('DELETE')[0]?.[0]).toBe('/api/v1/resources/13')
    expect(toasts()).toContain('资源已删除')

    api.request.mockImplementation((path: string, options?: { method?: string }) => options?.method === 'DELETE'
      ? Promise.reject(new ApiError('请先删除或移走它下面的资源。', 409)) : answer(path, options))
    await select(wrapper, '10')
    await button(wrapper, '删除').trigger('click')
    await flushPromises()
    dialogButton('删除').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请先删除或移走它下面的资源。')
    wrapper.unmount()
  })

  it('manages applications', async () => {
    const { wrapper } = await mountCatalog()
    expect(button(wrapper, '删除应用').attributes('disabled')).toBeDefined()
    await button(wrapper, '新建应用').trigger('click')
    await flushPromises()
    dialogButton('新建应用').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请输入应用名称')
    await fill('应用名称', 'CRM')
    dialogButton('新建应用').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('请输入应用编码')
    await fill('应用编码', 'crm')
    dialogButton('新建应用').click()
    await flushPromises()
    expect(calls('POST')[0]).toEqual(['/api/v1/applications', { method: 'POST', body: { code: 'crm', name: 'CRM', description: '' } }])
    expect(wrapper.text()).toContain('这个应用还没有资源')

    await button(wrapper, '编辑应用').trigger('click')
    await flushPromises()
    await fill('应用名称', 'Customers')
    dialogButton('保存').click()
    await flushPromises()
    expect(calls('PUT')[0]).toEqual(['/api/v1/applications/9', { method: 'PUT', body: { name: 'Customers', description: '' } }])

    await button(wrapper, '删除应用').trigger('click')
    await flushPromises()
    dialogButton('删除应用').click()
    await flushPromises()
    expect(calls('DELETE')[0]?.[0]).toBe('/api/v1/applications/9')
    expect(toasts()).toEqual(expect.arrayContaining(['应用已创建', '应用已更新', '应用已删除']))
    wrapper.unmount()
  })

  it('is read-only for tenant administrators and shows load errors', async () => {
    const { wrapper } = await mountCatalog(false)
    await select(wrapper, '11')
    expect(wrapper.findAll('button').map(item => item.text().trim())).not.toContain('编辑')
    expect(wrapper.text()).not.toContain('新建应用')
    expect(wrapper.get('[role="treeitem"]').attributes('draggable')).toBe('false')
    wrapper.unmount()

    api.request.mockImplementation((path: string) => path === '/api/v1/applications' ? Promise.reject(new ApiError('无权访问', 403))
      : answer(path))
    const failed = await mountCatalog()
    expect(failed.wrapper.get('[role="alert"]').text()).toBe('无权访问')
    failed.wrapper.unmount()
  })
})
