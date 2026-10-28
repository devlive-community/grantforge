// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mountView } from '../../tests/unit/mountView'
import UiSelect from '@/components/UiSelect.vue'
import { useToast } from '@/stores/toast'
import { setLocale } from '@/i18n'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))
const { default: DataSourcesView } = await import('./DataSourcesView.vue')
const source = { id: '108141042090696705', code: 'corp', name: 'Corporate directory', type: 'DINGTALK', identitySourceId: '108141042090696704',
  identitySourceName: 'Internal SSO', enabled: true, loginUpdates: true, secretSet: true, initialized: true, syncIntervalMinutes: 30,
  settings: { corpId: 'corp-id', appKey: 'app-key', rootDepartmentId: '1' } }
const report = { employees: 5, departmentsCreated: 3, departmentsUpdated: 1, membershipsAdded: 2, membershipsRemoved: 0, linked: 2, unmatched: 3, retainedDepartments: 0, problems: [] }
const dialog = () => document.querySelector('dialog[open]') as HTMLDialogElement
function field(label: string) {
  const target = [...dialog().querySelectorAll('label')].find(item => item.textContent?.replace('*', '').trim() === label)
  return dialog().querySelector<HTMLInputElement>(`[id="${target?.getAttribute('for')}"]`) as HTMLInputElement
}
function fill(label: string, value: string) { const input = field(label); input.value = value; input.dispatchEvent(new Event('input')) }

describe('organization data sources', () => {
  beforeEach(() => {
    api.request.mockReset()
    api.request.mockImplementation((path: string, options?: { method?: string }) => {
      if (path === '/api/v1/data-sources' && !options?.method) return Promise.resolve([source])
      if (path === '/api/v1/identity-sources') return Promise.resolve([{ id: source.identitySourceId, name: 'Internal SSO', type: 'OIDC' }])
      if (path === '/api/v1/org-units') return Promise.resolve([])
      if (path.endsWith('/sync')) return Promise.resolve(report)
      return Promise.resolve(null)
    })
  })
  afterEach(() => { document.body.innerHTML = ''; setLocale('zh-CN') })

  it('localizes the save, delete and loading labels', async () => {
    api.request.mockImplementation((path: string) => {
      if (path === '/api/v1/data-sources') return Promise.resolve([source])
      return new Promise(() => {})
    })
    const { wrapper } = await mountView(DataSourcesView, {}, '/admin/data-sources')
    await flushPromises()
    await wrapper.get('[aria-label="编辑 Corporate directory"]').trigger('click')
    await flushPromises()
    expect(dialog().textContent).toContain('正在查找…')
    expect(dialog().querySelector('button[form="data-source"]')?.textContent).toContain('保存')
    setLocale('en-US')
    await flushPromises()
    expect(dialog().textContent).toContain('Looking up…')
    expect(dialog().querySelector('button[form="data-source"]')?.textContent).toContain('Save')
    wrapper.unmount()

    setLocale('zh-CN')
    const second = await mountView(DataSourcesView, {}, '/admin/data-sources')
    await flushPromises()
    await second.wrapper.get('[aria-label="删除 Corporate directory"]').trigger('click')
    await flushPromises()
    expect(dialog().textContent).toContain('删除数据源')
    expect(dialog().textContent).not.toContain('shared.delete')
    second.wrapper.unmount()
  })

  it('shows independent directory controls and synchronization counts', async () => {
    const { wrapper } = await mountView(DataSourcesView, {}, '/admin/data-sources')
    await flushPromises()
    expect(wrapper.text()).toContain('Internal SSO')
    const sync = wrapper.findAll('button').find(button => button.text() === '立即同步')
    await sync?.trigger('click'); await flushPromises()
    expect(api.request).toHaveBeenCalledWith(`/api/v1/data-sources/${source.id}/sync`, { method: 'POST' })
    expect(wrapper.get('[data-report]').text()).toContain('已关联 2 / 未关联 3')
    expect(useToast().items.map(item => item.message)).toContain('组织架构同步完成')
    wrapper.unmount()
  })

  it('keeps initialized scope immutable and never sends the stored credential back', async () => {
    const { wrapper } = await mountView(DataSourcesView, {}, '/admin/data-sources')
    await flushPromises()
    await wrapper.get('[aria-label="编辑 Corporate directory"]').trigger('click'); await flushPromises()
    expect(field('企业 ID（CorpId）').disabled).toBe(true)
    expect(field('起始部门 ID').disabled).toBe(true)
    expect(field('AppSecret').value).toBe('')
    fill('名称', 'Renamed'); fill('同步间隔（分钟）', '60')
    dialog().querySelector('form')?.dispatchEvent(new Event('submit')); await flushPromises()
    expect(api.request).toHaveBeenCalledWith(`/api/v1/data-sources/${source.id}`, { method: 'PUT', body: expect.objectContaining({
      name: 'Renamed', identitySourceId: source.identitySourceId, secret: undefined, syncIntervalMinutes: 60,
      settings: { corpId: 'corp-id', appKey: 'app-key', rootDepartmentId: '1' } }) })
    expect(api.request).not.toHaveBeenCalledWith(expect.stringContaining('/api/v1/identity-sources/'), expect.anything())
    wrapper.unmount()
  })

  it('creates a data source with string IDs and separate SDK credentials', async () => {
    api.request.mockResolvedValueOnce([])
    const { wrapper } = await mountView(DataSourcesView, {}, '/admin/data-sources')
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '添加数据源')?.trigger('click'); await flushPromises()
    fill('编码', 'new-corp'); fill('名称', 'Directory'); fill('企业 ID（CorpId）', 'corp'); fill('AppKey', 'app'); fill('AppSecret', 'write-only')
    const identity = wrapper.findAllComponents(UiSelect).find(select => select.props('label') === '关联 OIDC 身份源')
    identity?.vm.$emit('update:modelValue', source.identitySourceId); await flushPromises()
    dialog().querySelector('form')?.dispatchEvent(new Event('submit')); await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/data-sources', { method: 'POST', body: expect.objectContaining({
      code: 'new-corp', identitySourceId: source.identitySourceId, type: 'DINGTALK', secret: 'write-only', loginUpdates: true, syncIntervalMinutes: 30 }) })
    wrapper.unmount()
  })
})
