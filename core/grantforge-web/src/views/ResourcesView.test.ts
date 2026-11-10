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
import { authorization, everything } from '../../tests/unit/authorization'

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
  if (path === '/api/v1/applications/9/clients' && !options?.method) return Promise.resolve([])
  if (path.endsWith('/dependencies') && !options?.method) return Promise.resolve(path.startsWith('/api/v1/applications') ? [] : { requires: [], requiredBy: [] })
  if (path.endsWith('/impact')) return Promise.resolve({ roles: [{ roleId: '7', tenantCode: 'acme', code: 'auditors', name: '审计员',
    gained: 0, lost: 3 }], accounts: 4, gained: [], lost: ['system.user.list'] })
  if (options?.method === 'DELETE') return Promise.resolve(null)
  if (path === '/api/v1/applications') return Promise.resolve(applications[1])
  return Promise.resolve(resources[3])
}

async function mountCatalog(platform = true, expand = true) {
  const mounted = await mountView(ResourcesView, {}, '/platform/resources')
  useAuth().authorization = authorization(platform ? everything() : [])
  await flushPromises()
  if (expand) {
    if (mounted.wrapper.find('[data-id="10"] button').exists()) await mounted.wrapper.get('[data-id="10"] button').trigger('click')
    if (mounted.wrapper.find('[data-id="11"] button').exists()) await mounted.wrapper.get('[data-id="11"] button').trigger('click')
  }
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
/** A field's message floats out of the dialog, so an element's words are read from the document as well. */
function textOf(root: Element | null | undefined) {
  const tips = Array.from(root?.querySelectorAll('[aria-describedby]') ?? [])
    .map(control => document.getElementById(control.getAttribute('aria-describedby') || '')?.textContent ?? '')
  return [root?.textContent ?? '', ...tips].join('')
}
/** Every validation message the open dialog shows, whether inline or floating beside a control. */
const dialogText = () => textOf(document.querySelector('dialog[open]'))

describe('resource catalog view', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = '' })

  it('starts collapsed, expands on demand and temporarily shows search matches', async () => {
    const { wrapper } = await mountCatalog(true, false)
    expect(wrapper.findAll('[role="treeitem"]').map(item => item.attributes('data-id'))).toEqual(['10', '30'])
    expect(wrapper.get('[data-id="10"]').attributes('aria-expanded')).toBe('false')
    await wrapper.get('input[type="search"]').setValue('export')
    expect(wrapper.findAll('[role="treeitem"]').map(item => item.attributes('data-id'))).toEqual(['10', '11', '13'])
    await wrapper.get('input[type="search"]').setValue('')
    expect(wrapper.findAll('[role="treeitem"]').map(item => item.attributes('data-id'))).toEqual(['10', '30'])
    await wrapper.get('[data-id="10"] button').trigger('click')
    expect(wrapper.findAll('[role="treeitem"]').map(item => item.attributes('data-id'))).toEqual(['10', '11', '12', '30'])
    expect(wrapper.get('[data-id="11"]').attributes('aria-expanded')).toBe('false')
    wrapper.unmount()
  })

  it('requires a long press and confirmation before clearing only the chosen root descendants', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) =>
      path === '/api/v1/applications' && !options?.method
        ? Promise.resolve([{ ...applications[0], builtin: false }, applications[1]]) : answer(path, options))
    const { wrapper } = await mountCatalog()
    useAuth().authorization = { ...authorization(everything()), unrestricted: true }
    await flushPromises()
    vi.useFakeTimers()
    await wrapper.get('[role="treeitem"][data-id="10"]').trigger('mousedown', { button: 0 })
    vi.advanceTimersByTime(2999)
    expect(document.querySelector('dialog[open]')).toBeNull()
    vi.advanceTimersByTime(1)
    await wrapper.vm.$nextTick()
    expect(dialogText()).toContain('系统管理')
    expect(dialogText()).toContain('保留该顶级资源')
    dialogButton('清空下级资源').click()
    await wrapper.vm.$nextTick()
    expect(dialogText()).toContain('无法撤销')
    expect(calls('DELETE')).toHaveLength(0)
    expect(dialogText()).toContain('3 个资源')
    dialogButton('确认').click()
    vi.useRealTimers()
    await flushPromises()
    expect(calls('DELETE')).toEqual([['/api/v1/applications/1/resource-tree/10/children', { method: 'DELETE' }]])
    wrapper.unmount()
  })

  it('offers a separate application-wide clear button to platform administrators only', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) =>
      path === '/api/v1/applications' && !options?.method
        ? Promise.resolve([{ ...applications[0], builtin: false }, applications[1]]) : answer(path, options))
    const { wrapper } = await mountCatalog()
    expect(wrapper.findAll('button').some(item => item.text().trim() === '清空')).toBe(false)
    useAuth().authorization = { ...authorization(everything()), unrestricted: true }
    await flushPromises()
    await button(wrapper, '清空').trigger('click')
    expect(dialogText()).toContain('全部 5 个资源')
    dialogButton('确认').click()
    await flushPromises()
    expect(calls('DELETE')).toEqual([['/api/v1/applications/1/resource-tree', { method: 'DELETE' }]])
    wrapper.unmount()
  })

  it('allows a platform administrator role to open the clear actions without a system account', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) =>
      path === '/api/v1/applications' && !options?.method
        ? Promise.resolve([{ ...applications[0], builtin: false }, applications[1]]) : answer(path, options))
    const { wrapper } = await mountCatalog()
    useAuth().authorization = { ...authorization(everything()), roles: ['platform-admin'] }
    await flushPromises()
    expect(button(wrapper, '清空').exists()).toBe(true)
    vi.useFakeTimers()
    await wrapper.get('[role="treeitem"][data-id="10"]').trigger('mousedown', { button: 0 })
    vi.advanceTimersByTime(3000)
    await wrapper.vm.$nextTick()
    expect(dialogText()).toContain('清空下级资源')
    vi.useRealTimers()
    wrapper.unmount()
  })

  it('ignores long presses on child resources and for non-platform administrators', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) =>
      path === '/api/v1/applications' && !options?.method
        ? Promise.resolve([{ ...applications[0], builtin: false }, applications[1]]) : answer(path, options))
    const { wrapper } = await mountCatalog()
    vi.useFakeTimers()
    await wrapper.get('[role="treeitem"][data-id="10"]').trigger('mousedown', { button: 0 })
    vi.advanceTimersByTime(3000)
    await wrapper.vm.$nextTick()
    expect(document.querySelector('dialog[open]')).toBeNull()
    useAuth().authorization = { ...authorization(everything()), unrestricted: true }
    await wrapper.vm.$nextTick()
    await wrapper.get('[role="treeitem"][data-id="11"]').trigger('mousedown', { button: 0 })
    vi.advanceTimersByTime(3000)
    await wrapper.vm.$nextTick()
    expect(document.querySelector('dialog[open]')).toBeNull()
    vi.useRealTimers()
    wrapper.unmount()
  })

  it('exports the entire resource tree and dependencies regardless of the current filter', async () => {
    const dependency = { id: '100', resourceId: '11', dependsOnId: '13', kind: 'REQUIRED', source: 'MANUAL' }
    api.request.mockImplementation((path: string, options?: { method?: string }) =>
      path === '/api/v1/applications/1/dependencies' ? Promise.resolve([dependency]) : answer(path, options))
    URL.createObjectURL = vi.fn(() => 'blob:export')
    URL.revokeObjectURL = vi.fn()
    const download = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const { wrapper } = await mountCatalog()
    await wrapper.get('input[type="search"]').setValue('export')
    await button(wrapper, '导出').trigger('click')
    await flushPromises()
    const blob = vi.mocked(URL.createObjectURL).mock.calls[0]?.[0] as Blob
    const json = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.onerror = () => reject(reader.error)
      reader.readAsText(blob)
    })
    const result = JSON.parse(json)
    expect(result.formatVersion).toBe(1)
    expect(result.application.code).toBe('grantforge-console')
    expect(result.resources.map((resource: { code: string }) => resource.code)).toEqual(['system', 'audit'])
    expect(result.resources[0].children[0]).toMatchObject({ code: 'system.user.list', route: '/admin/users',
      children: [{ code: 'system.user.btn.export', description: '导出用户' }] })
    expect(result.resources[0].children[1]).toMatchObject({ code: 'system.group.list', enabled: false })
    expect(result.dependencies).toEqual([{ ...dependency, resourceCode: 'system.user.list', dependsOnCode: 'system.user.btn.export' }])
    expect(download).toHaveBeenCalledOnce()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:export')
    wrapper.unmount()
    download.mockRestore()
  })

  it('reports an export failure instead of downloading incomplete data', async () => {
    const { wrapper } = await mountCatalog()
    api.request.mockImplementation((path: string, options?: { method?: string }) =>
      path === '/api/v1/applications/1/dependencies' ? Promise.reject(new ApiError('无权访问。', 403)) : answer(path, options))
    const download = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    await button(wrapper, '导出').trigger('click')
    await flushPromises()
    expect(download).not.toHaveBeenCalled()
    expect(toasts()).toContain('无权访问。')
    wrapper.unmount(); download.mockRestore()
  })

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

  it('searches the tree by name or code inside a panel of its own height', async () => {
    const { wrapper } = await mountCatalog()
    // The tree scrolls in its panel, so a large catalog does not stretch the page.
    expect(wrapper.get('[data-tree-scroller]').classes()).toContain('overflow-y-auto')
    const search = wrapper.get('input[type="search"]')
    expect(search.attributes('aria-label')).toBe('搜索资源')
    await search.setValue('export')
    // The match comes with the resources that lead to it.
    expect(wrapper.findAll('[role="treeitem"]').map(item => item.attributes('data-id'))).toEqual(['10', '11', '13'])
    await search.setValue('审计')
    expect(wrapper.findAll('[role="treeitem"]').map(item => item.attributes('data-id'))).toEqual(['30'])
    await search.trigger('keydown', { key: 'Escape' })
    expect(wrapper.findAll('[role="treeitem"]')).toHaveLength(5)
    await search.setValue('nowhere')
    expect(wrapper.text()).toContain('没有与“nowhere”匹配的节点')
    await wrapper.get('button[aria-label="清除"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[role="treeitem"]')).toHaveLength(5)
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
    expect(dialogText()).toContain('请输入资源名称')
    expect(dialogText()).toContain('请输入资源编码')
    await fill('资源名称', '编辑')
    // Filling the name is enough: its message goes without waiting for another submit.
    await flushPromises()
    expect(dialogText()).not.toContain('请输入资源名称')
    expect(dialogText()).toContain('请输入资源编码')
    dialogButton('新建资源').click()
    await flushPromises()
    // The name is filled in now, so only the code is still missing.
    expect(dialogText()).not.toContain('请输入资源名称')
    expect(dialogText()).toContain('请输入资源编码')
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
    document.querySelector<HTMLButtonElement>('dialog[open] [role="combobox"]')?.click()
    await flushPromises()
    const option = [...document.querySelectorAll<HTMLElement>('[role="option"]')].find(item => item.textContent?.trim() === '接口')
    option?.click()
    await flushPromises()
    await fill('资源编码', 'api:GET:/api/v1/users')
    expect(() => field('前端路由')).toThrow()
    dialogButton('新建资源').click()
    await flushPromises()
    expect(calls('POST')[0]?.[1].body).toMatchObject({ parentId: null, type: 'API', route: null, visible: true, denyMode: 'HIDE' })
    wrapper.unmount()
  })

  it('generates editable random resource codes for the selected type', async () => {
    const { wrapper } = await mountCatalog()
    await button(wrapper, '新建顶级资源').trigger('click')
    await flushPromises()
    dialogButton('生成').click()
    await flushPromises()
    const first = field('资源编码').value
    expect(first).toMatch(/^module_[0-9a-f]{16}$/)
    dialogButton('生成').click()
    await flushPromises()
    expect(field('资源编码').value).not.toBe(first)
    document.querySelector<HTMLButtonElement>('dialog[open] [role="combobox"]')?.click()
    await flushPromises()
    ;[...document.querySelectorAll<HTMLElement>('[role="option"]')].find(item => item.textContent?.trim() === '页面')?.click()
    await flushPromises()
    expect(field('资源编码').value).toBe('')
    dialogButton('生成').click()
    await flushPromises()
    expect(field('资源编码').value).toMatch(/^page_[0-9a-f]{16}$/)
    await fill('资源编码', 'page.manual')
    expect(field('资源编码').value).toBe('page.manual')
    document.querySelector<HTMLButtonElement>('dialog[open] [role="combobox"]')?.click()
    await flushPromises()
    ;[...document.querySelectorAll<HTMLElement>('[role="option"]')].find(item => item.textContent?.trim() === '数据实体')?.click()
    await flushPromises()
    expect(field('资源编码').value).toBe('')
    dialogButton('生成').click()
    await flushPromises()
    expect(field('资源编码').value).toMatch(/^data_entity_[0-9a-f]{16}$/)
    expect(field('资源编码').value).toHaveLength(28)
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

  it('shows what disabling a resource would do before saving it', async () => {
    const { wrapper } = await mountCatalog()
    await select(wrapper, '11')
    await button(wrapper, '编辑').trigger('click')
    await flushPromises()
    document.querySelector<HTMLElement>('dialog[open] [role="switch"]')?.click()
    await flushPromises()
    dialogButton('保存').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/resources/11/impact', { query: { enabled: false } })
    expect(calls('PUT')).toHaveLength(0)
    const shown = document.querySelector('dialog[open] [data-impact]')?.textContent
    expect(shown).toContain('acme')
    expect(shown).toContain('system.user.list')
    dialogButton('确认').click()
    await flushPromises()
    expect(calls('PUT')[0]?.[1]).toMatchObject({ method: 'PUT', body: { enabled: false } })
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
    // The console signs users in itself and has no OAuth clients.
    expect(() => button(wrapper, 'OAuth 客户端')).toThrow()
    await button(wrapper, '新建应用').trigger('click')
    await flushPromises()
    dialogButton('新建应用').click()
    await flushPromises()
    expect(dialogText()).toContain('请输入应用名称')
    expect(dialogText()).toContain('请输入应用编码')
    await fill('应用名称', 'CRM')
    // Filling the name is enough: its message goes without waiting for another submit.
    await flushPromises()
    expect(dialogText()).not.toContain('请输入应用名称')
    expect(dialogText()).toContain('请输入应用编码')
    dialogButton('新建应用').click()
    await flushPromises()
    // The name is filled in now, so only the code is still missing.
    expect(dialogText()).not.toContain('请输入应用名称')
    expect(dialogText()).toContain('请输入应用编码')
    await fill('应用编码', 'crm')
    dialogButton('新建应用').click()
    await flushPromises()
    expect(calls('POST')[0]).toEqual(['/api/v1/applications', { method: 'POST', body: { code: 'crm', name: 'CRM', description: '', administratorId: null } }])
    expect(wrapper.text()).toContain('这个应用还没有资源')
    expect(wrapper.findAll('button').map(item => item.text().trim())).not.toContain('导出资源树')

    await button(wrapper, '编辑应用').trigger('click')
    await flushPromises()
    await fill('应用名称', 'Customers')
    dialogButton('保存').click()
    await flushPromises()
    expect(calls('PUT')[0]).toEqual(['/api/v1/applications/9', { method: 'PUT', body: { name: 'Customers', description: '', administratorId: '' } }])

    await button(wrapper, 'OAuth 客户端').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/applications/9/clients')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('该应用还没有客户端。')
    dialogButton('关闭').click()
    await flushPromises()

    await button(wrapper, '删除应用').trigger('click')
    await flushPromises()
    dialogButton('删除应用').click()
    await flushPromises()
    expect(calls('DELETE')[0]?.[0]).toBe('/api/v1/applications/9')
    expect(toasts()).toEqual(expect.arrayContaining(['应用已创建', '应用已更新', '应用已删除']))
    wrapper.unmount()
  })

  it('assigns a selected user as application administrator', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string }) =>
      path === '/api/v1/users' ? Promise.resolve({ items: [{ id: '42', username: 'alice', displayName: 'Alice' }] }) : answer(path, options))
    const { wrapper } = await mountCatalog()
    await button(wrapper, '新建应用').trigger('click')
    await flushPromises()
    await fill('应用名称', 'CRM')
    await fill('应用编码', 'crm')
    const selector = [...document.querySelectorAll<HTMLButtonElement>('dialog[open] button[role="combobox"]')]
      .find(item => item.getAttribute('aria-label') === '应用管理员' || item.textContent?.includes('不指定管理员'))
    expect(selector).toBeTruthy()
    selector?.click()
    await flushPromises()
    const option = [...document.querySelectorAll<HTMLElement>('[role="option"]')].find(item => item.textContent?.includes('Alice (alice)'))
    expect(option).toBeTruthy()
    option?.click()
    await flushPromises()
    dialogButton('新建应用').click()
    await flushPromises()
    expect(calls('POST')[0]?.[1]?.body).toMatchObject({ administratorId: '42' })
    wrapper.unmount()
  })

  it('shows matching administrators while searching and selects a result directly', async () => {
    api.request.mockImplementation((path: string, options?: { method?: string; query?: { q?: string } }) =>
      path === '/api/v1/users'
        ? Promise.resolve({ items: options?.query?.q === 'alice' ? [{ id: '42', username: 'alice', displayName: 'Alice' }] : [] })
        : answer(path, options))
    const { wrapper } = await mountCatalog()
    await button(wrapper, '新建应用').trigger('click')
    await flushPromises()
    const selector = document.querySelector<HTMLButtonElement>('dialog[open] button[role="combobox"]')
    selector?.click()
    await flushPromises()
    const input = document.querySelector<HTMLInputElement>('dialog[open] input[type="search"]')
    if (!input) throw new Error('Missing administrator search')
    vi.useFakeTimers()
    input.value = 'alice'
    input.dispatchEvent(new Event('input'))
    await vi.advanceTimersByTimeAsync(300)
    vi.useRealTimers()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/users', { query: { q: 'alice', page: 1, size: 50 } })
    const match = [...document.querySelectorAll<HTMLElement>('[role="option"]')].find(option => option.textContent?.includes('Alice (alice)'))
    expect(match).toBeTruthy()
    match?.click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="combobox"]')?.textContent).toContain('Alice (alice)')
    expect(document.querySelector('dialog[open] input[type="search"]')).toBeNull()
    wrapper.unmount()
  })

  it('keeps the latest search results when an earlier request finishes later', async () => {
    let resolveOld!: (value: unknown) => void
    api.request.mockImplementation((path: string, options?: { method?: string; query?: { q?: string } }) => {
      if (path !== '/api/v1/users') return answer(path, options)
      if (options?.query?.q === 'old') return new Promise(resolve => { resolveOld = resolve })
      return Promise.resolve({ items: options?.query?.q === 'new' ? [{ id: '2', username: 'new' }] : [] })
    })
    const { wrapper } = await mountCatalog()
    await button(wrapper, '新建应用').trigger('click')
    await flushPromises()
    const selector = document.querySelector<HTMLButtonElement>('dialog[open] button[role="combobox"]')
    selector?.click()
    await flushPromises()
    const input = document.querySelector<HTMLInputElement>('dialog[open] input[type="search"]')
    if (!input) throw new Error('Missing administrator search')
    vi.useFakeTimers()
    input.value = 'old'; input.dispatchEvent(new Event('input'))
    await vi.advanceTimersByTimeAsync(300)
    input.value = 'new'; input.dispatchEvent(new Event('input'))
    await vi.advanceTimersByTimeAsync(300)
    resolveOld({ items: [{ id: '1', username: 'old' }] })
    await Promise.resolve()
    vi.useRealTimers()
    await flushPromises()
    expect(dialogText()).toContain('new')
    expect(dialogText()).not.toContain('old')
    vi.useFakeTimers()
    input.value = 'missing'; input.dispatchEvent(new Event('input'))
    await vi.advanceTimersByTimeAsync(300)
    vi.useRealTimers()
    await flushPromises()
    expect(dialogText()).toContain('没有找到匹配的用户')
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
