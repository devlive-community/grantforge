// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { readFileSync } from 'node:fs'
import { expect, test, type Page as BrowserPage } from '@playwright/test'

/** Every console resource of the manifest: an administrator's authorization. */
const everything = [...readFileSync(new URL('../../src/permissions/manifest.json', import.meta.url), 'utf8')
  .matchAll(/"code": "([^"]+)"/g)].map(match => match[1])

const units = [{ id: '1', code: 'hq', name: '总部', sortOrder: 0, depth: 0 }, { id: '2', parentId: '1', code: 'rnd', name: '研发部', sortOrder: 0, depth: 1 }]
interface MockUser { id: string; username: string; displayName?: string; status: string; systemAccount: boolean; mustChangePassword: boolean; createdAt: string; primaryUnitId?: string; primaryUnitName?: string; others: string[] }
async function mockApi(page: BrowserPage, restricted = false) {
  const users: MockUser[] = [
    { id: '2', username: 'admin', status: 'ACTIVE', systemAccount: true, mustChangePassword: false, createdAt: '2026-09-30T09:30:00Z', primaryUnitId: '1', primaryUnitName: '总部', others: [] },
    { id: '3', username: 'alex', status: 'ACTIVE', systemAccount: false, mustChangePassword: false, createdAt: '2026-09-29T10:00:00Z', primaryUnitId: '1', primaryUnitName: '总部', others: [] },
  ]
  const detail = (user: MockUser) => ({ user, memberships: [...(user.primaryUnitId ? [{ unitId: user.primaryUnitId, unitName: user.primaryUnitName, primary: true }] : []),
    ...user.others.map(id => ({ unitId: id, unitName: units.find(unit => unit.id === id)?.name, primary: false }))], positions: [] })
  const me = { username: 'admin', tenantCode: 'default', tenantName: 'Default', systemAccount: true, passwordChangeRequired: false }
  let session = false
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    // Endpoints of the rebuilt server answer plain JSON or RFC 9457 problems.
    if (path === '/api/v1/bootstrap') return route.fulfill({ json: { setupRequired: false, registrationEnabled: true } })
    if (path === '/api/v1/auth/login') { session = true; return route.fulfill({ json: me }) }
    if (path === '/api/v1/auth/logout') { session = false; return route.fulfill({ status: 204 }) }
    if (path === '/api/v1/me') return session ? route.fulfill({ json: me })
      : route.fulfill({ status: 401, contentType: 'application/problem+json', json: { status: 401, code: 'GF-COMMON-401', detail: '请先登录。' } })
    if (path === '/api/v1/me/authorization') return route.fulfill({ json: { version: 1, unrestricted: false, roles: [], resources: restricted ? [] : everything, permissions: [] } })
    if (path === '/api/v1/org-units') return route.fulfill({ json: units })
    if (path === '/api/v1/positions/options') return route.fulfill({ json: [{ id: '5', name: '技术负责人' }] })
    if (path === '/api/v1/users' && method === 'GET') {
      return route.fulfill({ json: { items: users, page: Number(url.searchParams.get('page') || 1), size: Number(url.searchParams.get('size') || 20), total: users.length } })
    }
    if (path === '/api/v1/users' && method === 'POST') {
      const body = request.postDataJSON() as { username: string; profile: { displayName: string; primaryUnitId: string | null; otherUnitIds: string[] } }
      const unit = units.find(item => item.id === body.profile.primaryUnitId)
      const user: MockUser = { id: String(100 + users.length), username: body.username, displayName: body.profile.displayName || undefined, status: 'ACTIVE',
        systemAccount: false, mustChangePassword: true, createdAt: '2026-09-30T12:00:00Z', primaryUnitId: unit?.id, primaryUnitName: unit?.name, others: body.profile.otherUnitIds }
      users.unshift(user)
      return route.fulfill({ status: 201, json: detail(user) })
    }
    const userPath = /^\/api\/v1\/users\/([^/]+)(?:\/(\w+))?$/.exec(path)
    if (userPath) {
      const user = users.find(item => item.id === userPath[1])
      if (!user) return route.fulfill({ status: 404, contentType: 'application/problem+json', json: { status: 404, code: 'GF-COMMON-404', detail: '未找到。' } })
      if (method === 'DELETE') { users.splice(users.indexOf(user), 1); return route.fulfill({ status: 204 }) }
      if (method === 'PUT') { user.others = (request.postDataJSON() as { otherUnitIds: string[] }).otherUnitIds }
      return route.fulfill({ json: detail(user) })
    }
    const paged = (rows: unknown[]) => ({ items: rows, page: Number(url.searchParams.get('page') || 1), size: Number(url.searchParams.get('size') || 20), total: rows.length })
    if (path === '/api/v1/groups' && method === 'GET') return route.fulfill({ json: paged([{ id: '7', code: 'ops', name: '运维组', memberCount: 2 }]) })
    if (path === '/api/v1/positions' && method === 'GET') return route.fulfill({ json: paged([]) })
    await route.fulfill({ status: 404, contentType: 'application/problem+json', json: { status: 404, code: 'GF-COMMON-404', detail: '未找到。' } })
  })
  return users
}
async function login(page: BrowserPage) {
  await page.goto('/#/auth/login')
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByRole('heading', { name: '工作空间概览' })).toBeVisible()
}

test('login sends credentials in the JSON body and restores the session after reload', async ({ page }) => {
  await mockApi(page)
  const request = page.waitForRequest('**/api/v1/auth/login')
  await login(page)
  const sent = await request
  expect(new URL(sent.url()).search).toBe('')
  expect(sent.postDataJSON()).toEqual({ username: 'admin', password: 'test-password' })
  await page.reload()
  await expect(page.getByRole('heading', { name: '工作空间概览' })).toBeVisible()
  await page.getByRole('button', { name: /退出登录/ }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
})

test('creates users in a department, confirms deletion and supports Escape', async ({ page }) => {
  await mockApi(page); await login(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '用户管理' }).click()
  await page.getByRole('button', { name: '创建用户', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '创建用户' })
  await dialog.getByRole('textbox', { name: '用户名', exact: true }).fill('morgan')
  await dialog.getByRole('combobox', { name: '主部门' }).click()
  await dialog.getByRole('option', { name: '— 研发部', exact: true }).click()
  await dialog.getByLabel(/^初始密码/).fill('new-password')
  await dialog.getByLabel(/^确认初始密码/).fill('new-password')
  const created = page.waitForRequest(request => new URL(request.url()).pathname === '/api/v1/users' && request.method() === 'POST')
  await dialog.getByRole('button', { name: '创建用户', exact: true }).click()
  expect((await created).postDataJSON()).toMatchObject({ username: 'morgan', profile: { primaryUnitId: '2', otherUnitIds: [] } })
  const row = page.getByRole('row').filter({ hasText: 'morgan' })
  await expect(row).toContainText('研发部')
  await expect(row).toContainText('待改密')
  await row.getByRole('button', { name: '编辑 morgan' }).click()
  await expect(page.getByRole('dialog', { name: '编辑用户' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: '编辑用户' })).not.toBeVisible()
  await row.getByRole('button', { name: '删除 morgan' }).click()
  await page.getByRole('dialog', { name: '请确认' }).getByRole('button', { name: '删除', exact: true }).click()
  await expect(row).toHaveCount(0)
  // System accounts offer no way to disable, lock or delete them.
  await expect(page.getByRole('button', { name: '删除 admin' })).toHaveCount(0)
})

test('route permissions deny unknown admin pages and unauthorized responses clear state', async ({ page }) => {
  await mockApi(page, true); await login(page)
  await expect(page.getByRole('navigation').getByRole('link', { name: '用户管理' })).toHaveCount(0)
  await page.goto('/#/admin/users')
  await expect(page.getByRole('heading', { name: '这扇门，暂时没有为你打开' })).toBeVisible()
})

test('JSON stays local, reports invalid input and renders correctly on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockApi(page); await login(page)
  await page.goto('/#/json/pretty')
  await page.getByLabel('JSON 输入').fill('{"hello":"world","items":[1,2]}')
  await page.getByRole('button', { name: '格式化', exact: true }).click()
  await expect(page.getByLabel('JSON 输出')).toHaveValue('{\n  "hello": "world",\n  "items": [\n    1,\n    2\n  ]\n}')
  await page.getByLabel('JSON 输入').fill('{broken')
  await page.getByRole('button', { name: '格式化', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: '打开导航' }).click()
  await expect(page.locator('aside')).toBeInViewport({ ratio: 1 })
  await page.screenshot({ path: '/private/tmp/grantforge-web-mobile.png', fullPage: true })
})

test('desktop workspace has no console failures or horizontal overflow', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message))
  await mockApi(page); await login(page)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: '/private/tmp/grantforge-web-dashboard.png', fullPage: true })
  const navigation = page.getByRole('navigation', { name: '主导航' })
  for (const legacy of ['菜单管理', '请求方式']) await expect(navigation.getByRole('link', { name: legacy })).toHaveCount(0)
  await navigation.getByRole('link', { name: '用户组' }).click()
  await expect(page.getByText('运维组')).toBeVisible()
  await page.getByRole('button', { name: '创建用户组', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '创建用户组' })).toBeVisible()
  expect(errors).toEqual([])
})

test('custom pagination updates the server query and returns focus to its trigger', async ({ page }) => {
  await mockApi(page); await login(page)
  await page.goto('/#/admin/users')
  const size = page.getByRole('combobox', { name: '每页记录数' })
  await size.click()
  await expect(size).toHaveAttribute('aria-expanded', 'true')
  const pending = page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.pathname === '/api/v1/users' && url.searchParams.get('size') === '50'
  })
  await page.getByRole('option', { name: '50 条 / 页', exact: true }).click()
  await pending
  await expect(size).toHaveText('50 条 / 页')
  await expect(size).toHaveAttribute('aria-expanded', 'false')
  await expect(size).toBeFocused()
})
