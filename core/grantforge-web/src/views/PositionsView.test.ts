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

const { default: PositionsView } = await import('./PositionsView.vue')

const cfo = { id: '1', code: 'cfo', name: '财务总监', description: '管钱', sortOrder: 1, holders: 1 }
const dev = { id: '2', code: 'dev', name: 'Developer', sortOrder: 2, holders: 0 }

function answer(path: string, options?: { method?: string }) {
  if (path === '/api/v1/positions' && !options?.method) return Promise.resolve({ items: [cfo, dev], page: 1, size: 20, total: 2 })
  if (path === '/api/v1/positions/1/holders') return Promise.resolve({ items: [{ accountId: '9', username: 'gina', displayName: '吉娜', addedAt: '2026-10-01T08:00:00Z' }], page: 1, size: 50, total: 70 })
  if (path === '/api/v1/positions/2/holders') return Promise.resolve({ items: [], page: 1, size: 50, total: 0 })
  return Promise.resolve(options?.method === 'DELETE' ? null : cfo)
}

async function mountPositions() {
  const mounted = await mountView(PositionsView, {}, '/admin/positions')
  await flushPromises()
  return mounted
}

async function fill(label: string, value: string) {
  const owner = [...document.querySelectorAll<HTMLLabelElement>('dialog[open] label')]
    .find(item => item.textContent?.replace('*', '').trim() === label)
  const input = owner ? document.getElementById(owner.htmlFor) as HTMLInputElement | null : null
  if (!input) throw new Error('missing field ' + label)
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function submit() {
  document.querySelector('form#position-form')?.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

const alert = () => document.querySelector('form#position-form [role="alert"]')?.textContent
const toasts = () => useToast().items.map(item => item.message)
/** A field's message floats out of the dialog, so an element's words are read from the document as well. */
function textOf(root: Element | null | undefined) {
  const tips = Array.from(root?.querySelectorAll('[aria-describedby]') ?? [])
    .map(control => document.getElementById(control.getAttribute('aria-describedby') || '')?.textContent ?? '')
  return [root?.textContent ?? '', ...tips].join('')
}

describe('positions view', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = ''; vi.useRealTimers() })

  it('lists positions and searches after a pause', async () => {
    vi.useFakeTimers()
    const { wrapper } = await mountPositions()
    expect(wrapper.text()).toContain('2 个岗位')
    expect(wrapper.text()).toContain('管钱')
    await wrapper.get('[aria-label="搜索岗位"]').setValue(' 财务 ')
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(api.request).toHaveBeenLastCalledWith('/api/v1/positions', expect.objectContaining({ query: { q: '财务', page: 1, size: 20 } }))
    wrapper.unmount()
  })

  it('checks, creates, edits and deletes positions', async () => {
    const { wrapper } = await mountPositions()
    await wrapper.findAll('button').find(button => button.text().includes('创建岗位'))?.trigger('click')
    await submit()
    expect(textOf(document.getElementById('position-form'))).toContain('请输入岗位编码')
    expect(textOf(document.getElementById('position-form'))).toContain('请输入岗位名称')
    await fill('岗位编码', 'coo')
    await submit()
    // The code is filled in now, so only the name is still missing.
    expect(textOf(document.getElementById('position-form'))).not.toContain('请输入岗位编码')
    expect(textOf(document.getElementById('position-form'))).toContain('请输入岗位名称')
    await fill('岗位名称', '运营总监')
    await fill('排序值', '-1')
    await submit()
    // The name is filled in now, so only the sort order is still wrong.
    expect(textOf(document.getElementById('position-form'))).not.toContain('请输入岗位名称')
    expect(textOf(document.getElementById('position-form'))).toContain('排序值必须是大于或等于 0 的整数')
    await fill('排序值', '3')
    api.request.mockRejectedValueOnce(new ApiError('岗位编码“coo”已被使用。', 409))
    await submit()
    expect(alert()).toBe('岗位编码“coo”已被使用。')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/positions', { method: 'POST', body: { code: 'coo', name: '运营总监', description: '', sortOrder: 3 } })
    expect(toasts()).toContain('岗位已创建')

    await wrapper.get('[aria-label="编辑 财务总监"]').trigger('click')
    await fill('岗位名称', 'CFO')
    await submit()
    expect(api.request).toHaveBeenCalledWith('/api/v1/positions/1', { method: 'PUT', body: { code: 'cfo', name: 'CFO', description: '管钱', sortOrder: 1 } })

    await wrapper.get('[aria-label="删除 财务总监"]').trigger('click')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('1 位任职人员将不再担任该岗位')
    ;([...document.querySelectorAll<HTMLButtonElement>('dialog[open] button')].find(button => button.textContent?.trim() === '删除岗位'))?.click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/positions/1', { method: 'DELETE' })
    expect(toasts()).toContain('岗位已删除')
    wrapper.unmount()
  })

  it('shows the holders of a position', async () => {
    const { wrapper } = await mountPositions()
    await wrapper.get('[aria-label="查看 财务总监 的任职人员"]').trigger('click')
    await flushPromises()
    const dialog = document.querySelector('dialog[open]')
    expect(dialog?.textContent).toContain('吉娜')
    expect(dialog?.textContent).toContain('共 70 人，仅显示前 1 位')
    wrapper.unmount()

    const again = await mountPositions()
    await again.wrapper.get('[aria-label="查看 Developer 的任职人员"]').trigger('click')
    await flushPromises()
    expect(document.querySelector('dialog[open]')?.textContent).toContain('暂无人担任该岗位')
    api.request.mockRejectedValueOnce(new ApiError('未找到。', 404))
    await again.wrapper.get('[aria-label="查看 Developer 的任职人员"]').trigger('click')
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toBe('未找到。')
    again.wrapper.unmount()
  })

  it('shows why the list failed', async () => {
    api.request.mockRejectedValueOnce(new ApiError('无权执行此操作。', 403))
    const { wrapper } = await mountPositions()
    expect(wrapper.text()).toContain('无权执行此操作。')
    wrapper.unmount()
  })
})
