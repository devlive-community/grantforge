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

const { default: UsersView } = await import('./UsersView.vue')

const units = [{ id: '1', code: 'hq', name: '总部', sortOrder: 0, depth: 0 }, { id: '2', parentId: '1', code: 'rnd', name: '研发部', sortOrder: 0, depth: 1 }]
const admin = { id: '10', username: 'admin', status: 'ACTIVE', systemAccount: true, mustChangePassword: false, createdAt: '2026-10-01T08:00:00Z', lastLoginAt: '2026-10-01T09:00:00Z' }
const alex = { id: '11', username: 'alex', displayName: 'Alex', email: 'alex@acme.io', status: 'ACTIVE', systemAccount: false, mustChangePassword: true,
  createdAt: '2026-10-01T08:00:00Z', primaryUnitId: '1', primaryUnitName: '总部' }
const carol = { id: '12', username: 'carol', status: 'DISABLED', lockedUntil: '9999-01-01T00:00:00Z', systemAccount: false, mustChangePassword: false, createdAt: '2026-10-01T08:00:00Z' }

function answer(path: string, options?: { method?: string }) {
  if (path === '/api/v1/org-units') return Promise.resolve(units)
  if (path === '/api/v1/positions/options') return Promise.resolve([{ id: '7', name: '财务总监' }, { id: '8', name: 'Developer' }])
  if (path === '/api/v1/users' && !options?.method) return Promise.resolve({ items: [admin, alex, carol], page: 1, size: 20, total: 3 })
  if (path === '/api/v1/users/11' && !options?.method) return Promise.resolve({ user: alex, memberships: [{ unitId: '1', unitName: '总部', primary: true }, { unitId: '2', unitName: '研发部', primary: false }],
    positions: [{ positionId: '8', name: 'Developer' }] })
  return Promise.resolve(options?.method === 'DELETE' ? null : { user: alex, memberships: [], positions: [] })
}

async function mountUsers() {
  const mounted = await mountView(UsersView, {}, '/admin/users')
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

async function submit(form: string) {
  document.querySelector(`form#${form}`)?.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

function dialogButton(label: string) {
  const found = [...document.querySelectorAll('dialog[open] button')].find(item => item.textContent?.trim() === label)
  if (!found) throw new Error('missing dialog button ' + label)
  return found as HTMLButtonElement
}

const alertOf = (form: string) => document.querySelector(`form#${form} [role="alert"]`)?.textContent
const toasts = () => useToast().items.map(item => item.message)
const listCalls = () => api.request.mock.calls.filter(([path, options]) => path === '/api/v1/users' && !options?.method)

describe('users view', () => {
  beforeEach(() => { api.request.mockReset(); api.request.mockImplementation(answer) })
  afterEach(() => { document.body.innerHTML = ''; vi.useRealTimers() })

  it('lists accounts with their department, state and protection', async () => {
    const { wrapper } = await mountUsers()
    expect(wrapper.text()).toContain('3 位用户')
    const rows = wrapper.findAll('tbody tr').map(row => row.text())
    expect(rows[0]).toContain('系统')
    expect(rows[1]).toContain('总部')
    expect(rows[1]).toContain('待改密')
    expect(rows[2]).toContain('未分配部门')
    expect(rows[2]).toContain('从未登录')
    expect(rows[2]).toContain('已锁定')
    expect(wrapper.find('[aria-label="禁用 admin"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="禁用 Alex"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="启用 carol"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="解锁 carol"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('searches after a pause and filters by state and department', async () => {
    vi.useFakeTimers()
    const { wrapper } = await mountUsers()
    await wrapper.get('[aria-label="搜索用户"]').setValue(' ale ')
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(listCalls().at(-1)?.[1]).toMatchObject({ query: { q: 'ale', state: undefined, unitId: undefined, page: 1 } })
    const combos = wrapper.findAll('[role="combobox"]')
    const choose = async (label: string) => {
      ;([...document.querySelectorAll<HTMLElement>('[role="option"]')].find(option => option.textContent?.trim() === label))?.click()
      await flushPromises()
    }
    await combos[0]?.trigger('click')
    await choose('已锁定')
    expect(listCalls().at(-1)?.[1]).toMatchObject({ query: { state: 'LOCKED' } })
    await combos[1]?.trigger('click')
    await choose('— 研发部')
    expect(listCalls().at(-1)?.[1]).toMatchObject({ query: { unitId: '2' } })
    wrapper.unmount()
  })

  it('checks and creates an account in departments', async () => {
    const { wrapper } = await mountUsers()
    await wrapper.findAll('button').find(button => button.text().includes('创建用户'))?.trigger('click')
    await submit('user-form')
    expect(alertOf('user-form')).toBe('请输入用户名')
    await fill('用户名', 'morgan')
    await submit('user-form')
    expect(alertOf('user-form')).toBe('请设置初始密码')
    await fill('初始密码', 'a long enough password')
    await submit('user-form')
    expect(alertOf('user-form')).toBe('两次输入的密码不一致')
    await fill('确认初始密码', 'a long enough password')
    await fill('显示名称', 'Morgan')
    api.request.mockRejectedValueOnce(new ApiError('用户名“morgan”已被使用。', 409))
    await submit('user-form')
    expect(alertOf('user-form')).toBe('用户名“morgan”已被使用。')
    await submit('user-form')
    expect(api.request).toHaveBeenCalledWith('/api/v1/users', { method: 'POST', body: { username: 'morgan', password: 'a long enough password',
      profile: { displayName: 'Morgan', email: '', primaryUnitId: null, otherUnitIds: [], positionIds: [] } } })
    expect(toasts()).toContain('用户已创建')
    wrapper.unmount()
  })

  it('edits details and further departments', async () => {
    const { wrapper } = await mountUsers()
    await wrapper.get('[aria-label="编辑 Alex"]').trigger('click')
    await flushPromises()
    const boxes = [...document.querySelectorAll<HTMLInputElement>('dialog[open] input[type="checkbox"]')]
    // Departments (总部 is primary, 研发部 further), then positions (财务总监, Developer held).
    expect(boxes.map(box => box.checked)).toEqual([false, true, false, true])
    expect(boxes[0]?.disabled).toBe(true)
    boxes[1]?.click()
    boxes[2]?.click()
    await flushPromises()
    await fill('邮箱', '')
    dialogButton('保存').click()
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/users/11', { method: 'PUT', body: { displayName: 'Alex', email: '', primaryUnitId: '1', otherUnitIds: [], positionIds: ['8', '7'] } })
    expect(toasts()).toContain('用户已更新')

    api.request.mockRejectedValueOnce(new ApiError('未找到。', 404))
    await wrapper.get('[aria-label="编辑 Alex"]').trigger('click')
    await flushPromises()
    expect(useToast().items.at(-1)).toMatchObject({ message: '未找到。', kind: 'error' })
    wrapper.unmount()
  })

  it('shows the roles of an account', async () => {
    const { wrapper } = await mountUsers()
    api.request.mockImplementation((path: string, options?: { method?: string }) => path.endsWith('/roles')
      ? Promise.resolve([]) : answer(path, options))
    await wrapper.get('[aria-label="查看 Alex 的角色"]').trigger('click')
    await flushPromises()
    expect(api.request.mock.calls.some(call => typeof call[0] === 'string' && call[0].endsWith('/roles'))).toBe(true)
    expect(document.querySelector('dialog[open]')?.textContent).toContain('Alex 的角色')
    wrapper.unmount()
  })

  it('resets passwords after checking the confirmation', async () => {
    const { wrapper } = await mountUsers()
    await wrapper.get('[aria-label="重置 Alex 的密码"]').trigger('click')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('下次登录时必须修改')
    await submit('password-form')
    expect(alertOf('password-form')).toBe('请设置初始密码')
    await fill('新密码', 'another long password')
    await fill('确认新密码', 'another long password')
    await submit('password-form')
    expect(api.request).toHaveBeenCalledWith('/api/v1/users/11/password', { method: 'POST', body: { password: 'another long password' } })
    expect(toasts()).toContain('密码已重置')
    wrapper.unmount()
  })

  it('confirms disabling, locking and deleting, and enables and unlocks at once', async () => {
    const { wrapper } = await mountUsers()
    for (const [label, warning, button, call, done] of [
      ['禁用 Alex', '无法登录，直到被重新启用', '禁用', ['/api/v1/users/11/disable', { method: 'POST' }], '用户已禁用'],
      ['锁定 Alex', '疑似被盗用', '锁定', ['/api/v1/users/11/lock', { method: 'POST' }], '用户已锁定'],
      ['删除 Alex', '审计记录会保留', '删除', ['/api/v1/users/11', { method: 'DELETE' }], '用户已删除'],
    ] as const) {
      await wrapper.get(`[aria-label="${label}"]`).trigger('click')
      expect(document.querySelector('dialog[open]')?.textContent).toContain(warning)
      dialogButton(button).click()
      await flushPromises()
      expect(api.request).toHaveBeenCalledWith(...call)
      expect(toasts()).toContain(done)
    }
    await wrapper.get('[aria-label="启用 carol"]').trigger('click')
    await flushPromises()
    await wrapper.get('[aria-label="解锁 carol"]').trigger('click')
    await flushPromises()
    expect(api.request).toHaveBeenCalledWith('/api/v1/users/12/enable', { method: 'POST' })
    expect(api.request).toHaveBeenCalledWith('/api/v1/users/12/unlock', { method: 'POST' })

    api.request.mockRejectedValueOnce(new ApiError('系统账号和你自己的账号不能被禁用、锁定或删除。', 409))
    await wrapper.get('[aria-label="禁用 Alex"]').trigger('click')
    dialogButton('禁用').click()
    await flushPromises()
    expect(document.querySelector('dialog[open] [role="alert"]')?.textContent).toContain('系统账号')
    dialogButton('取消').click()
    await flushPromises()
    api.request.mockRejectedValueOnce(new ApiError('无权执行此操作。', 403))
    await wrapper.get('[aria-label="启用 carol"]').trigger('click')
    await flushPromises()
    expect(useToast().items.at(-1)).toMatchObject({ message: '无权执行此操作。', kind: 'error' })
    wrapper.unmount()
  })

  it('shows why the list failed and copes without departments', async () => {
    api.request.mockImplementation((path: string) => path === '/api/v1/users' ? Promise.reject(new ApiError('无权执行此操作。', 403))
      : path === '/api/v1/org-units' ? Promise.reject(new Error('offline')) : answer(path))
    const { wrapper } = await mountUsers()
    expect(wrapper.text()).toContain('无权执行此操作。')
    await wrapper.findAll('button').find(button => button.text().includes('创建用户'))?.trigger('click')
    // Without departments only the positions are offered.
    expect(document.querySelector('dialog[open]')?.textContent).not.toContain('兼职部门')
    expect(document.querySelector('dialog[open]')?.textContent).toContain('财务总监')
    wrapper.unmount()
  })
})
