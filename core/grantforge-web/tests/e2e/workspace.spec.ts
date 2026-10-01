// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { expect, test, type Page as BrowserPage } from '@playwright/test'

const roles = [{ id: 1, name: '管理员', code: 'ADMIN', description: '管理工作空间', active: true }, { id: 2, name: '开发者', code: 'DEVELOPER', description: '访问开发资源', active: true }]
const tree = [{ id: 10, title: '管理分组', checked: true, children: [{ id: 11, title: '角色管理', checked: true }, { id: 27, title: '用户管理', checked: false }] }]
const units = [{ id: '1', code: 'hq', name: '总部', sortOrder: 0, depth: 0 }, { id: '2', parentId: '1', code: 'rnd', name: '研发部', sortOrder: 0, depth: 1 }]
interface MockUser { id: string; username: string; displayName?: string; status: string; systemAccount: boolean; mustChangePassword: boolean; createdAt: string; primaryUnitId?: string; primaryUnitName?: string; others: string[] }
async function mockApi(page: BrowserPage, restricted = false, longOptions = false) {
  const users: MockUser[] = [
    { id: '2', username: 'admin', status: 'ACTIVE', systemAccount: true, mustChangePassword: false, createdAt: '2026-09-30T09:30:00Z', primaryUnitId: '1', primaryUnitName: '总部', others: [] },
    { id: '3', username: 'alex', status: 'ACTIVE', systemAccount: false, mustChangePassword: false, createdAt: '2026-09-29T10:00:00Z', primaryUnitId: '1', primaryUnitName: '总部', others: [] },
  ]
  const detail = (user: MockUser) => ({ user, memberships: [...(user.primaryUnitId ? [{ unitId: user.primaryUnitId, unitName: user.primaryUnitName, primary: true }] : []),
    ...user.others.map(id => ({ unitId: id, unitName: units.find(unit => unit.id === id)?.name, primary: false }))] })
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
    if (path === '/api/v1/me/authorization') return route.fulfill({ json: { version: 1, unrestricted: !restricted, resources: [] } })
    if (path === '/api/v1/org-units') return route.fulfill({ json: units })
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
    let data: unknown = null
    const paged = (rows: unknown[]) => ({ content: rows, number: Number(url.searchParams.get('page') || 1), size: Number(url.searchParams.get('size') || 20), totalElements: rows.length, totalPages: 1 })
    if (path === '/api/v1/role/menus') data = method === 'GET' ? tree : { id: 1 }
    else if (path === '/api/v1/role') data = method === 'GET' ? paged(roles) : { id: 3 }
    else if (path === '/api/v1/method') data = paged([{ id: 1, name: 'GET', method: 'GET', active: true, description: '读取资源' }, { id: 2, name: 'POST', method: 'POST', active: true }])
    else if (path === '/api/v1/menu') data = paged([{ id: 1, name: '工作空间', url: '#', parent: 0, active: true, type: { id: 3, name: '菜单' }, icon: { id: 1, name: 'Home', code: 'home' }, methods: [{ id: 1, name: 'GET', method: 'GET' }] }, ...(longOptions ? Array.from({ length: 24 }, (_, index) => ({ id: index + 2, name: `项目分组 ${index + 1}`, url: '#', parent: 0, active: true })) : [])])
    else if (path === '/api/v1/system/menu/type') data = paged([{ id: 3, name: '菜单' }])
    else if (path === '/api/v1/icon') data = paged([{ id: 1, name: 'Home', code: 'home' }, ...(longOptions ? Array.from({ length: 24 }, (_, index) => ({ id: index + 2, name: `Folder ${index + 1}`, code: `folder-${index + 1}` })) : [])])
    await route.fulfill({ json: { code: 2000, message: 'success', data } })
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

test('role grants submit selected leaves with their ancestors', async ({ page }) => {
  await mockApi(page); await login(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '角色管理' }).click()
  await page.getByRole('button', { name: '授权', exact: true }).first().click()
  const dialog = page.getByRole('dialog', { name: '角色授权' })
  await dialog.getByRole('checkbox', { name: /用户管理/ }).check()
  const pending = page.waitForRequest(request => request.url().includes('/api/v1/role/menus') && request.method() === 'PUT')
  await dialog.getByRole('button', { name: '保存权限' }).click()
  expect((await pending).postDataJSON()).toEqual({ roleId: 1, menus: [10, 11, 27] })
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
  await page.getByRole('navigation').getByRole('link', { name: '菜单管理' }).click()
  await expect(page.getByRole('heading', { name: '菜单管理', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '创建菜单', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '创建菜单' }).getByRole('combobox', { name: '菜单类型' })).toHaveText('菜单')
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

test('HTTP method listbox supports keyboard selection, Escape and Tab inside a dialog', async ({ page }) => {
  await mockApi(page); await login(page)
  await page.goto('/#/admin/methods')
  await page.getByRole('button', { name: '创建请求方式', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '创建请求方式' })
  const method = dialog.getByRole('combobox', { name: 'HTTP 方法' })
  await method.focus()
  await page.keyboard.press('ArrowDown')
  await expect(method).toHaveAttribute('aria-expanded', 'true')
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')
  await expect(method).toHaveText('POST')
  await expect(dialog).toBeVisible()
  await expect(method).toBeFocused()
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Escape')
  await expect(method).toHaveAttribute('aria-expanded', 'false')
  await expect(method).toHaveText('POST')
  await expect(dialog).toBeVisible()
  await page.keyboard.press('Home')
  await page.keyboard.press('End')
  await page.keyboard.press('Tab')
  await expect(method).toHaveAttribute('aria-expanded', 'false')
  await expect(method).toHaveText('OPTIONS')
  await expect(dialog.getByRole('textbox', { name: '描述' })).toBeFocused()
  const active = dialog.getByRole('switch', { name: '启用请求方式' })
  await active.focus(); await page.keyboard.press('Space')
  await expect(active).toHaveAttribute('aria-checked', 'false')
  await dialog.getByRole('textbox', { name: '请求方式名称', exact: true }).fill('预检请求')
  const pending = page.waitForRequest(request => new URL(request.url()).pathname === '/api/v1/method' && request.method() === 'POST')
  await dialog.getByRole('button', { name: '创建请求方式', exact: true }).click()
  expect((await pending).postDataJSON()).toMatchObject({ name: '预检请求', method: 'OPTIONS', code: 'OPTIONS', active: false })
  await expect(dialog).not.toBeVisible()
})

test('long menu listboxes stay clickable inside the scrolling modal and a narrow dark viewport', async ({ page }) => {
  await mockApi(page, false, true); await login(page)
  await page.goto('/#/admin/menus')
  await page.getByRole('button', { name: '创建菜单', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '创建菜单' })
  const icon = dialog.getByRole('combobox', { name: '图标', exact: true })
  await expect(icon).toBeEnabled()
  await icon.click()
  await expect(dialog.getByRole('listbox', { name: '图标', exact: true })).toBeVisible()
  await page.screenshot({ path: '/private/tmp/grantforge-controls-desktop-menu.png' })
  await dialog.getByRole('option', { name: 'Folder 24 folder-24', exact: true }).click()
  await expect(icon).toHaveText('Folder 24')
  const parent = dialog.getByRole('combobox', { name: '上级分组' })
  await parent.click()
  await dialog.getByRole('option', { name: '项目分组 24', exact: true }).click()
  await expect(parent).toHaveText('项目分组 24')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '切换深色主题' }).click()
  await page.getByRole('button', { name: '创建菜单', exact: true }).click()
  await icon.scrollIntoViewIfNeeded()
  await icon.click()
  const listbox = dialog.getByRole('listbox', { name: '图标', exact: true })
  await expect(listbox).toBeVisible()
  const bounds = await listbox.boundingBox()
  if (!bounds) throw new Error('listbox has no bounding box')
  expect(bounds.x).toBeGreaterThanOrEqual(0)
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(390)
  expect(bounds.y).toBeGreaterThanOrEqual(0)
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(844)
  await expect(page.locator('html')).toHaveClass(/dark/)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: '/private/tmp/grantforge-controls-mobile-dark.png' })
  await dialog.getByRole('option', { name: 'Folder 12 folder-12', exact: true }).click()
  await expect(icon).toHaveText('Folder 12')
  await parent.scrollIntoViewIfNeeded()
  await parent.click()
  await expect(dialog.getByRole('listbox', { name: '上级分组' })).toBeInViewport({ ratio: 1 })
  await page.screenshot({ path: '/private/tmp/grantforge-controls-mobile-parent.png' })
  await dialog.getByRole('option', { name: '项目分组 20', exact: true }).click()
  await expect(parent).toHaveText('项目分组 20')
})

test('custom checkboxes retain Space, mixed permissions and role request payloads', async ({ page }) => {
  await mockApi(page); await login(page)
  await page.goto('/#/admin/roles')
  await page.getByRole('button', { name: '授权', exact: true }).first().click()
  const grants = page.getByRole('dialog', { name: '角色授权' })
  const group = grants.getByRole('checkbox', { name: '管理分组', exact: true })
  const userAccess = grants.getByRole('checkbox', { name: '用户管理', exact: true })
  await expect(group).toHaveAttribute('aria-checked', 'mixed')
  expect(await group.evaluate(input => getComputedStyle(input).appearance)).toBe('none')
  await userAccess.focus(); await page.keyboard.press('Space')
  await expect(userAccess).toBeChecked()
  await expect(group).toHaveAttribute('aria-checked', 'true')
  await userAccess.focus(); await page.keyboard.press('Space')
  await expect(userAccess).not.toBeChecked()
  await expect(group).toHaveAttribute('aria-checked', 'mixed')
  const permissionRequest = page.waitForRequest(request => request.url().includes('/api/v1/role/menus') && request.method() === 'PUT')
  await grants.getByRole('button', { name: '保存权限' }).click()
  expect((await permissionRequest).postDataJSON()).toEqual({ roleId: 1, menus: [10, 11] })
  await page.goto('/#/admin/users')
  await page.getByRole('row').filter({ hasText: 'alex' }).getByRole('button', { name: '编辑 alex' }).click()
  const userDialog = page.getByRole('dialog', { name: '编辑用户' })
  const other = userDialog.getByRole('checkbox', { name: '— 研发部', exact: true })
  // The primary department cannot also be a further department.
  await expect(userDialog.getByRole('checkbox', { name: '总部', exact: true })).toBeDisabled()
  await other.focus(); await page.keyboard.press('Space')
  await expect(other).toBeChecked()
  const userRequest = page.waitForRequest(request => new URL(request.url()).pathname === '/api/v1/users/3' && request.method() === 'PUT')
  await userDialog.getByRole('button', { name: '保存', exact: true }).click()
  expect((await userRequest).postDataJSON()).toMatchObject({ primaryUnitId: '1', otherUnitIds: ['2'] })
})

test('custom validation rejects incomplete menu forms without sending writes and numeric stepper respects the minimum', async ({ page }) => {
  await mockApi(page); await login(page)
  await page.goto('/#/admin/menus')
  await page.getByRole('button', { name: '创建菜单', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '创建菜单' })
  await expect(dialog.getByRole('combobox', { name: '菜单类型' })).toBeEnabled()
  const writes: string[] = []
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/v1/menu' && request.method() === 'POST') writes.push(request.url())
  })
  await dialog.getByRole('button', { name: '创建菜单', exact: true }).click()
  await expect(dialog.getByRole('alert')).toHaveText('请输入菜单名称')
  await expect(dialog).toBeVisible()
  expect(writes).toEqual([])
  const sort = dialog.getByRole('spinbutton', { name: '排序', exact: true })
  await expect(sort).toHaveValue('1')
  const decrease = dialog.getByRole('button', { name: '减小排序' })
  await expect(decrease).toBeDisabled()
  await dialog.getByRole('button', { name: '增大排序' }).click()
  await expect(sort).toHaveValue('2')
  await decrease.click()
  await expect(sort).toHaveValue('1')
  await sort.fill('0')
  await dialog.getByRole('textbox', { name: '菜单名称', exact: true }).fill('示例菜单')
  await dialog.getByRole('textbox', { name: '菜单路径', exact: true }).fill('/example')
  await dialog.getByRole('button', { name: '创建菜单', exact: true }).click()
  await expect(dialog.getByRole('alert')).toHaveText('排序必须是大于或等于 1 的整数')
  expect(writes).toEqual([])
})
