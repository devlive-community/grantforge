// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api'
import { useToast } from '@/stores/toast'
import { mountView } from '../../tests/unit/mountView'

const api = vi.hoisted(() => ({ request: vi.fn(), download: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { default: TransferView } = await import('./TransferView.vue')

const saved: string[] = []

async function mountTransfer() {
  const mounted = await mountView(TransferView, {}, '/admin/transfer')
  await flushPromises()
  return mounted
}

function section(wrapper: VueWrapper, title: string) {
  const found = wrapper.findAll('section').find(item => item.find('h2').text() === title)
  if (!found) throw new Error('missing section ' + title)
  return found
}

async function pick(wrapper: VueWrapper, label: string, name: string) {
  const input = wrapper.get(`input[aria-label="${label}"]`)
  Object.defineProperty(input.element, 'files', { value: [new File(['code,name\n'], name, { type: 'text/csv' })], configurable: true })
  await input.trigger('change')
}

const button = (scope: { findAll: VueWrapper['findAll'] }, text: string) => {
  const found = scope.findAll('button').find(item => item.text().trim() === text)
  if (!found) throw new Error('missing button ' + text)
  return found
}

describe('transfer view', () => {
  beforeEach(() => {
    api.request.mockReset(); api.download.mockReset(); saved.length = 0
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { saved.push(this.download) })
  })
  afterEach(() => { document.body.innerHTML = ''; vi.restoreAllMocks() })

  it('exports data and hands out templates', async () => {
    api.download.mockResolvedValueOnce({ blob: new Blob(['a']), filename: 'users-2026-10-01.csv' })
    const { wrapper } = await mountTransfer()
    await button(section(wrapper, '用户'), '导出').trigger('click')
    await flushPromises()
    expect(api.download).toHaveBeenCalledWith('/api/v1/users/export')
    await button(section(wrapper, '组织架构'), '下载模板').trigger('click')
    expect(saved).toEqual(['users-2026-10-01.csv', 'units-template.csv'])
    expect(useToast().items.map(item => item.message)).toContain('已开始下载')

    api.download.mockRejectedValueOnce(new ApiError('无权执行此操作。', 403))
    await button(section(wrapper, '组织架构'), '导出').trigger('click')
    await flushPromises()
    expect(useToast().items.at(-1)).toMatchObject({ message: '无权执行此操作。', kind: 'error' })
    wrapper.unmount()
  })

  it('checks a file, lists its problems and imports it once it passes', async () => {
    const { wrapper } = await mountTransfer()
    const units = section(wrapper, '组织架构')
    expect(button(units, '预检').attributes('disabled')).toBeDefined()
    await pick(wrapper, '选择组织架构导入文件', 'tree.csv')
    expect(units.text()).toContain('tree.csv')

    api.request.mockResolvedValueOnce({ rows: 2, created: 0, applied: false,
      problems: [{ row: 3, column: 'parentCode', code: 'GF-IDENTITY-095', message: '找不到部门“nowhere”。' }] })
    await button(units, '预检').trigger('click')
    await flushPromises()
    const call = api.request.mock.calls.at(-1)
    expect(call?.[0]).toBe('/api/v1/org-units/import')
    expect(call?.[1]).toMatchObject({ method: 'POST', query: { apply: 'false' } })
    expect((call?.[1] as { body: FormData }).body.get('file')).toBeInstanceOf(File)
    expect(units.text()).toContain('发现 1 个问题')
    expect(units.find('tbody').text()).toContain('找不到部门“nowhere”。')

    api.request.mockResolvedValueOnce({ rows: 2, created: 0, applied: false, problems: [] })
    await button(units, '预检').trigger('click')
    await flushPromises()
    expect(units.text()).toContain('预检通过：共 2 行')
    api.request.mockResolvedValueOnce({ rows: 2, created: 2, applied: true, problems: [] })
    await button(units, '确认导入 2 行').trigger('click')
    await flushPromises()
    expect(api.request.mock.calls.at(-1)?.[1]).toMatchObject({ query: { apply: 'true' } })
    expect(units.text()).toContain('已导入 2 条记录')
    expect(units.findAll('[aria-current="step"]')).toHaveLength(0)
    wrapper.unmount()
  })

  it('shows file-level failures and resets when another file is chosen', async () => {
    const { wrapper } = await mountTransfer()
    const users = section(wrapper, '用户')
    await pick(wrapper, '选择用户导入文件', 'users.csv')
    api.request.mockRejectedValueOnce(new ApiError('文件缺少必需的列“password”。', 400))
    await button(users, '预检').trigger('click')
    await flushPromises()
    expect(users.get('[role="alert"]').text()).toBe('文件缺少必需的列“password”。')

    await pick(wrapper, '选择用户导入文件', 'fixed.csv')
    expect(users.find('[role="alert"]').exists()).toBe(false)
    expect(users.find('[aria-current="step"]').text()).toContain('预检')
    await button(users, '选择 CSV 文件').trigger('click')
    wrapper.unmount()
  })
})
