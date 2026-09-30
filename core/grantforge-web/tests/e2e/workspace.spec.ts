import { expect, test, type Page as BrowserPage } from '@playwright/test'

const roles = [{ id: 1, name: '管理员', code: 'ADMIN', description: '管理工作空间', active: true }, { id: 2, name: '开发者', code: 'DEVELOPER', description: '访问开发资源', active: true }]
const navigation = [{ id: 25, title: '概览', url: '/dashboard' }, { id: 27, title: '用户管理', url: '/admin/users' }, { id: 11, title: '角色管理', url: '/admin/roles' }, { id: 3, title: '菜单管理', url: '/admin/menus' }, { id: 13, title: '请求方式', url: '/admin/methods' }, { id: 49, title: 'JSON', url: '/json/pretty' }]
const tree = [{ id: 10, title: '管理分组', checked: true, children: [{ id: 11, title: '角色管理', checked: true }, { id: 27, title: '用户管理', checked: false }] }]
interface MockUser { id: number; name: string; active: boolean; createTime: string; roles: typeof roles }
async function mockApi(page: BrowserPage, restricted = false, longOptions = false) {
  const users: MockUser[] = [{ id: 2, name: 'admin', active: true, createTime: '2026-09-30 09:30:00', roles: [roles[0]!] }, { id: 3, name: 'alex', active: true, createTime: '2026-09-29 10:00:00', roles: [roles[1]!] }]
  const token = `header.${Buffer.from(JSON.stringify({ user_name: 'admin' })).toString('base64url')}.signature`
  await page.route('**/oauth/token', route => route.fulfill({ json: { code: 2000, message: 'success', data: token } }))
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    let data: unknown = null
    const paged = (rows: unknown[]) => ({ content: rows, number: Number(url.searchParams.get('page') || 1), size: Number(url.searchParams.get('size') || 20), totalElements: rows.length, totalPages: 1 })
    if (path.includes('/user/info/')) data = users[0]
    else if (path === '/api/v1/role/menu') data = restricted ? navigation.slice(0, 1) : navigation
    else if (path === '/api/v1/overview') data = [{ title: '用户总数', value: users.length }]
    else if (path === '/api/v1/user/register') {
      const body = request.postDataJSON() as { username: string }
      const id = 100 + users.length; users.push({ id, name: body.username, active: true, createTime: '2026-09-30 12:00:00', roles: [] }); data = id
    } else if (path === '/api/v1/user/role') {
      const body = request.postDataJSON() as { id: string; values: number[] }
      const user = users.find(user => user.id === Number(body.id)); if (user) user.roles = roles.filter(role => body.values.includes(role.id)); data = Number(body.id)
    } else if (path === '/api/v1/user' && method === 'DELETE') {
      const index = users.findIndex(user => user.id === Number(url.searchParams.get('id'))); if (index >= 0) users.splice(index, 1); data = 1
    } else if (path === '/api/v1/user') data = paged(users)
    else if (path === '/api/v1/role/menus') data = method === 'GET' ? tree : { id: 1 }
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

test('login uses form credentials and restores session after reload', async ({ page }) => {
  await mockApi(page)
  const request = page.waitForRequest('**/oauth/token')
  await login(page)
  const sent = await request
  expect(new URL(sent.url()).search).toBe('')
  expect(new URLSearchParams(sent.postData() || '').get('password')).toBe('test-password')
  await page.reload()
  await expect(page.getByRole('heading', { name: '工作空间概览' })).toBeVisible()
  await page.getByRole('button', { name: /退出登录/ }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
})

test('creates users, persists roles, confirms deletion and supports Escape', async ({ page }) => {
  await mockApi(page); await login(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '用户管理' }).click()
  await page.getByRole('button', { name: '创建用户', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '创建用户' })
  await dialog.getByRole('textbox', { name: '用户名', exact: true }).fill('morgan')
  await dialog.getByLabel('初始密码').fill('new-password')
  await dialog.getByRole('checkbox', { name: '开发者', exact: true }).check()
  await dialog.getByRole('button', { name: '创建用户', exact: true }).click()
  const row = page.getByRole('row').filter({ hasText: 'morgan' })
  await expect(row).toContainText('开发者')
  await row.getByRole('button', { name: '分配角色' }).click()
  await expect(page.getByRole('dialog', { name: '分配角色' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: '分配角色' })).not.toBeVisible()
  await row.getByRole('button', { name: '删除用户 morgan' }).click()
  await page.getByRole('dialog', { name: '删除用户' }).getByRole('button', { name: '确认删除' }).click()
  await expect(row).toHaveCount(0)
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
    return url.pathname === '/api/v1/user' && url.searchParams.get('size') === '50'
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
  expect(bounds).not.toBeNull()
  expect(bounds!.x).toBeGreaterThanOrEqual(0)
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(390)
  expect(bounds!.y).toBeGreaterThanOrEqual(0)
  expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(844)
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
  await page.getByRole('row').filter({ hasText: 'alex' }).getByRole('button', { name: '分配角色' }).click()
  const roleDialog = page.getByRole('dialog', { name: '分配角色' })
  const admin = roleDialog.getByRole('checkbox', { name: '管理员', exact: true })
  await admin.focus(); await page.keyboard.press('Space')
  const roleRequest = page.waitForRequest(request => request.url().includes('/api/v1/user/role') && request.method() === 'PUT')
  await roleDialog.getByRole('button', { name: '保存角色' }).click()
  expect((await roleRequest).postDataJSON()).toEqual({ id: '3', values: [2, 1] })
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
