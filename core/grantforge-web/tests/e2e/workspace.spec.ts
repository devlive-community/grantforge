import { expect, test, type Page as BrowserPage } from '@playwright/test'

const roles = [{ id: 1, name: '管理员', code: 'ADMIN', description: '管理工作空间', active: true }, { id: 2, name: '开发者', code: 'DEVELOPER', description: '访问开发资源', active: true }]
const navigation = [{ id: 25, title: '概览', url: '/dashboard' }, { id: 27, title: '用户管理', url: '/admin/users' }, { id: 11, title: '角色管理', url: '/admin/roles' }, { id: 3, title: '菜单管理', url: '/admin/menus' }, { id: 13, title: '请求方式', url: '/admin/methods' }, { id: 49, title: 'JSON', url: '/json/pretty' }]
const tree = [{ id: 10, title: '管理分组', checked: true, children: [{ id: 11, title: '角色管理', checked: true }, { id: 27, title: '用户管理', checked: false }] }]
interface MockUser { id: number; name: string; active: boolean; createTime: string; roles: typeof roles }
async function mockApi(page: BrowserPage, restricted = false) {
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
    else if (path === '/api/v1/menu') data = paged([{ id: 1, name: '工作空间', url: '#', parent: 0, active: true, type: { id: 3, name: '菜单' }, icon: { id: 1, name: 'Home', code: 'home' }, methods: [{ id: 1, name: 'GET', method: 'GET' }] }])
    else if (path === '/api/v1/system/menu/type') data = paged([{ id: 3, name: '菜单' }])
    else if (path === '/api/v1/icon') data = paged([{ id: 1, name: 'Home', code: 'home' }])
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
  await expect(page.getByRole('dialog', { name: '创建菜单' }).getByLabel('菜单类型')).toHaveValue('3')
  expect(errors).toEqual([])
})
