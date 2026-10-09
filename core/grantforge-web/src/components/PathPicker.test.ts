// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BrowsePage } from '@/lib/browse'

const api = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/lib/api', async importOriginal => ({ ...await importOriginal<typeof import('@/lib/api')>(), ...api }))

const { ApiError } = await import('@/lib/api')
const { default: PathPicker } = await import('./PathPicker.vue')

const file = (path: string, size = 2048) => ({ name: path.slice(path.lastIndexOf('/') + 1), value: path, directory: false,
  owner: 'etl', group: 'analysts', permission: 'rw-r-----', size, modifiedAt: '2026-10-01T08:00:00Z' })
const folder = (path: string) => ({ name: path.slice(path.lastIndexOf('/') + 1), value: path, directory: true, owner: 'etl',
  group: 'analysts', permission: 'rwxr-x---' })
const page = (directory: string, entries: BrowsePage['entries'], nextCursor?: string): BrowsePage =>
  ({ root: '/data', directory, entries, nextCursor })

let picked: string[][]
function render(): VueWrapper {
  picked = []
  return mount(PathPicker, { attachTo: document.body, props: { modelValue: true, serviceId: '7', resource: 'path', label: 'Path',
    onPick: (values: string[]) => picked.push(values) } })
}
const body = (call: number) => (api.request.mock.calls[call]?.[1] as { body: Record<string, unknown> }).body
const rows = () => Array.from(document.querySelectorAll<HTMLElement>('[data-entry]')).map(row => row.dataset.entry)
const button = (name: string | RegExp) => Array.from(document.querySelectorAll<HTMLButtonElement>('dialog button'))
  .find(element => typeof name === 'string' ? element.textContent?.trim() === name || element.getAttribute('aria-label') === name
    : name.test(element.textContent ?? ''))

describe('path picker', () => {
  beforeEach(() => api.request.mockReset())
  afterEach(() => { document.body.innerHTML = '' })

  it('browses down and up, directories first, and adds what was chosen', async () => {
    api.request
      .mockResolvedValueOnce(page('/data', [file('/data/readme.md'), folder('/data/sales')]))
      .mockResolvedValueOnce(page('/data/sales', [file('/data/sales/orders.csv'), folder('/data/sales/2026')]))
      .mockResolvedValueOnce(page('/data', [file('/data/readme.md'), folder('/data/sales')]))
    render()
    await flushPromises()
    expect(body(0)).toEqual({ resource: 'path', directory: '', cursor: undefined, pageSize: 100 })
    expect(rows()).toEqual(['/data/sales', '/data/readme.md'])
    expect(document.body.textContent).toContain('etl : analysts')
    expect(document.body.textContent).toContain('2.0 KB')
    // The root has nowhere further up to go.
    expect(button('上一级')?.disabled).toBe(true)

    button('打开 sales')?.click()
    await flushPromises()
    expect(body(1)).toMatchObject({ directory: '/data/sales' })
    expect(rows()).toEqual(['/data/sales/2026', '/data/sales/orders.csv'])
    expect(document.querySelector('nav[aria-label="路径"]')?.textContent).toMatch(/\/data.*sales/)
    document.querySelector<HTMLInputElement>('[data-entry="/data/sales/orders.csv"] input')?.click()
    await flushPromises()

    button('上一级')?.click()
    await flushPromises()
    expect(body(2)).toMatchObject({ directory: '/data' })
    // A choice survives moving between directories.
    document.querySelector<HTMLInputElement>('[data-entry="/data/readme.md"] input')?.click()
    await flushPromises()
    button(/添加所选（2）/)?.click()
    await flushPromises()
    expect(picked).toEqual([['/data/sales/orders.csv', '/data/readme.md']])
  })

  it('loads more pages after the cursor and can choose the directory itself', async () => {
    api.request
      .mockResolvedValueOnce(page('/data', [file('/data/a'), file('/data/b')], 'b'))
      .mockResolvedValueOnce(page('/data', [file('/data/c')]))
    render()
    await flushPromises()
    button('加载更多')?.click()
    await flushPromises()
    expect(body(1)).toMatchObject({ directory: '/data', cursor: 'b' })
    expect(rows()).toEqual(['/data/a', '/data/b', '/data/c'])
    expect(button('加载更多')).toBeUndefined()
    button('选择当前目录')?.click()
    expect(picked).toEqual([['/data']])
  })

  it('says why a directory cannot be listed and retries the same request', async () => {
    api.request
      .mockResolvedValueOnce(page('/data', [folder('/data/private')]))
      .mockRejectedValueOnce(new ApiError('目标系统拒绝了查找：Permission denied: user=grantforge', 502))
      .mockResolvedValueOnce(page('/data/private', []))
    render()
    await flushPromises()
    button('打开 private')?.click()
    await flushPromises()
    expect(document.querySelector('[role="alert"]')?.textContent).toContain('无法浏览：目标系统拒绝了查找：Permission denied')
    button(/重试/)?.click()
    await flushPromises()
    expect(body(2)).toMatchObject({ directory: '/data/private' })
    expect(document.querySelector('[role="alert"]')).toBeNull()
    expect(document.body.textContent).toContain('这个目录是空的')
  })

  it('never shows a listing that arrives after the picker was closed and opened again', async () => {
    let slow: (value: BrowsePage) => void = () => undefined
    api.request
      .mockImplementationOnce(() => new Promise(resolve => { slow = resolve }))
      .mockResolvedValueOnce(page('/data', [file('/data/fresh.csv')]))
    const wrapper = render()
    await flushPromises()
    const first = api.request.mock.calls[0]?.[1] as { signal: AbortSignal }
    await wrapper.setProps({ modelValue: false })
    expect(first.signal.aborted).toBe(true)
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    slow(page('/data', [file('/data/late.csv')]))
    await flushPromises()
    expect(rows()).toEqual(['/data/fresh.csv'])
  })
})
