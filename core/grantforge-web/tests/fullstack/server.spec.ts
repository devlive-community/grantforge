// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

// Runs first: a fresh installation sends every visitor to first-run setup until an administrator exists.
test('sets up a fresh installation with the token from the server log, once', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  const token = process.env.GRANTFORGE_E2E_SETUP_TOKEN ?? ''
  expect(token, 'GRANTFORGE_E2E_SETUP_TOKEN').not.toBe('')

  await page.goto('/')
  await expect(page).toHaveURL(/#\/setup/)
  await expect(page.getByRole('heading', { name: '初始化 GrantForge' })).toBeVisible()
  await expect(page.locator('img[src="/static/images/grantforge-logo.png"]').first()).toBeVisible()

  await page.getByLabel('初始化令牌').fill('not-the-token')
  await page.getByLabel('组织名称').fill('Acme')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码', { exact: true }).fill('a long enough password')
  await page.getByLabel('确认密码').fill('a long enough password')
  await page.getByRole('button', { name: '完成初始化' }).click()
  await expect(page.getByRole('alert')).toHaveText('初始化令牌无效，请使用服务端最近一次日志中输出的令牌。')

  await page.getByLabel('初始化令牌').fill(token)
  await page.getByRole('button', { name: '完成初始化' }).click()
  await expect(page.getByRole('heading', { name: '初始化完成' })).toBeVisible()

  await page.getByRole('button', { name: '前往登录' }).click()
  await expect(page).toHaveURL(/#\/auth\/login/)
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  // Registration is off by default, so the sign-in page does not offer it.
  await expect(page.getByRole('link', { name: '创建账号' })).toHaveCount(0)

  await page.goto('/#/setup')
  await expect(page).toHaveURL(/#\/auth\/login/)
  expect(errors).toEqual([])
})

/** Fetches the CSRF cookie the way the console does (any response carries it) and returns its value. */
async function csrfToken(request: APIRequestContext): Promise<string> {
  await request.get('/api/v1/bootstrap')
  const cookie = (await request.storageState()).cookies.find(entry => entry.name === 'XSRF-TOKEN')
  expect(cookie, 'XSRF-TOKEN cookie').toBeDefined()
  return cookie?.value ?? ''
}

test('signs the administrator in and out with a server-side session', async ({ page }) => {
  await page.goto('/#/auth/login')
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill('wrong password')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByRole('alert')).toHaveText('用户名或密码错误。')

  await page.getByLabel('密码', { exact: true }).fill('a long enough password')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByRole('heading', { name: '工作空间概览' })).toBeVisible()
  const cookies = await page.context().cookies()
  expect(cookies.find(cookie => cookie.name === 'GRANTFORGE_SESSION')).toMatchObject({ httpOnly: true, sameSite: 'Lax' })

  // The session survives a reload: the console asks the server instead of storing a token.
  await page.reload()
  await expect(page.getByRole('heading', { name: '工作空间概览' })).toBeVisible()
  expect(await page.evaluate(() => Object.keys(localStorage))).not.toContain('AuthXToken')
  // Until roles exist the administrator reaches every console page, with no warning about missing permissions.
  await expect(page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '用户管理' })).toBeVisible()
  await expect(page.getByText('导航权限暂未加载')).toHaveCount(0)

  await page.getByRole('button', { name: /退出登录/ }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await page.goto('/#/dashboard')
  await expect(page).toHaveURL(/#\/auth\/login/)
})

async function signIn(page: Page) {
  await page.goto('/#/auth/login')
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill('a long enough password')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByRole('heading', { name: '工作空间概览' })).toBeVisible()
}

test('lists the signed-in browsers and ends another one', async ({ browser }) => {
  const baseURL = test.info().project.use.baseURL
  const iPhone = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1'
  const laptop = await browser.newContext({ baseURL }), phone = await browser.newContext({ baseURL, userAgent: iPhone })
  const desk = await laptop.newPage(), mobile = await phone.newPage()
  await signIn(desk)
  await signIn(mobile)

  await desk.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '在线会话' }).click()
  await expect(desk.getByRole('heading', { name: '在线会话' })).toBeVisible()
  const phoneRow = desk.getByRole('row').filter({ hasText: 'Safari · iOS' })
  await expect(phoneRow).toHaveCount(1)
  await expect(desk.getByRole('row').filter({ hasText: '当前会话' })).toHaveCount(1)

  await phoneRow.getByRole('button', { name: /结束/ }).click()
  await desk.getByRole('dialog').getByRole('button', { name: '结束会话' }).click()
  await expect(desk.getByText('会话已结束')).toBeVisible()
  await expect(phoneRow).toHaveCount(0)

  // The phone's cookie no longer works: its next page load lands on sign-in.
  await mobile.reload()
  await expect(mobile).toHaveURL(/#\/auth\/login/)
  await desk.reload()
  await expect(desk.getByRole('heading', { name: '在线会话' })).toBeVisible()
  await laptop.close()
  await phone.close()
})

test('refuses a second setup', async ({ request }) => {
  const response = await request.post('/api/v1/setup', {
    headers: { 'Accept-Language': 'en', 'X-XSRF-TOKEN': await csrfToken(request) },
    data: { token: process.env.GRANTFORGE_E2E_SETUP_TOKEN, username: 'other', password: 'a long enough password' },
  })
  expect(response.status()).toBe(409)
  expect(await response.json()).toMatchObject({ code: 'GF-IDENTITY-001', detail: 'Setup has already been completed.' })
  const bootstrap = await request.get('/api/v1/bootstrap')
  expect(await bootstrap.json()).toEqual({ setupRequired: false, registrationEnabled: false })
})

test('answers anonymous API calls with a localised RFC 9457 problem and the request ID', async ({ request }) => {
  const response = await request.get('/api/v1/me', {
    headers: { 'Accept-Language': 'zh-CN', 'X-Request-Id': 'fullstack-1' },
  })
  expect(response.status()).toBe(401)
  expect(response.headers()['content-type']).toContain('application/problem+json')
  expect(response.headers()['x-request-id']).toBe('fullstack-1')
  expect(await response.json()).toMatchObject({
    status: 401,
    code: 'GF-COMMON-401',
    messageKey: 'error.common.unauthenticated',
    requestId: 'fullstack-1',
  })
})

test('rejects state-changing calls without the CSRF token', async ({ request }) => {
  const response = await request.post('/api/v1/auth/login', {
    headers: { 'Accept-Language': 'en' }, data: { username: 'admin', password: 'a long enough password' },
  })
  expect(response.status()).toBe(403)
  expect(await response.json()).toMatchObject({ code: 'GF-SECURITY-001' })
})

test('publishes the OpenAPI contract', async ({ request }) => {
  const response = await request.get('/v3/api-docs')
  expect(response.ok()).toBe(true)
  const contract = await response.json() as { info: { title: string; version: string }; servers: { url: string }[] }
  expect(contract.info).toMatchObject({ title: 'GrantForge API', version: 'v1' })
  expect(contract.servers).toEqual([{ url: '/' }])
})

test('reports readiness and liveness for orchestrators without exposing details', async ({ request }) => {
  for (const probe of ['/actuator/health', '/actuator/health/liveness', '/actuator/health/readiness']) {
    const response = await request.get(probe)
    expect(response.ok(), probe).toBe(true)
    const body = await response.json() as Record<string, unknown>
    expect(body.status, probe).toBe('UP')
    // The root lists its probe group names; component details must never be exposed.
    expect(body, probe).not.toHaveProperty('components')
    expect(body, probe).not.toHaveProperty('details')
  }
})

test('creates a tenant whose administrator manages only that tenant', async ({ page, browser }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '租户管理' }).click()
  await expect(page.getByRole('heading', { name: '租户管理' })).toBeVisible()
  await page.getByRole('button', { name: '创建租户' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/^租户编码/).fill('acme')
  await dialog.getByLabel(/^租户名称/).fill('Acme 集团')
  await dialog.getByLabel(/^管理员用户名/).fill('acme-admin')
  await dialog.getByLabel(/^初始密码/).fill('an initial password')
  await dialog.getByLabel(/^确认初始密码/).fill('an initial password')
  await dialog.getByRole('button', { name: '创建租户' }).click()
  await expect(page.getByText('租户已创建')).toBeVisible()
  await expect(page.getByRole('row').filter({ hasText: 'Acme 集团' })).toContainText('acme')

  const other = await browser.newContext({ baseURL: test.info().project.use.baseURL })
  const boss = await other.newPage()
  await boss.goto('/#/auth/login')
  await boss.getByLabel('用户名', { exact: true }).fill('acme-admin')
  await boss.getByLabel('密码', { exact: true }).fill('an initial password')
  await boss.getByRole('button', { name: '登录工作空间' }).click()
  // The first sign-in demands a new password before anything else.
  await expect(boss.getByText('请先设置新密码')).toBeVisible()
  await boss.getByLabel(/^当前密码/).fill('an initial password')
  await boss.getByLabel(/^新密码/).fill('the acme administrator password')
  await boss.getByLabel(/^确认新密码/).fill('the acme administrator password')
  await boss.getByRole('button', { name: '修改密码' }).click()
  await expect(boss.getByText('密码已修改，其他设备上的会话已结束')).toBeVisible()
  await expect(boss.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '用户管理' })).toBeVisible()
  await expect(boss.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '租户管理' })).toHaveCount(0)
  await boss.goto('/#/platform/tenants')
  await expect(boss.getByRole('heading', { name: '这扇门，暂时没有为你打开' })).toBeVisible()
  await other.close()
})

test('builds and rearranges the organization tree', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '组织架构' }).click()
  await expect(page.getByText('还没有部门')).toBeVisible()
  const create = async (button: string, code: string, name: string) => {
    await page.getByRole('button', { name: button }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel(/^部门名称/).fill(name)
    await dialog.getByLabel(/^部门编码/).fill(code)
    await dialog.getByRole('button', { name: '新建部门' }).click()
    await expect(page.getByText('部门已创建').last()).toBeVisible()
  }
  await create('新建顶级部门', 'hq', '总部')
  await create('添加下级部门', 'sales', '销售部')
  await create('新建顶级部门', 'lab', '实验室')

  const tree = page.getByRole('tree', { name: '部门树' })
  await tree.getByRole('treeitem', { name: /销售部/ }).click()
  await page.getByRole('button', { name: '移动到…' }).click()
  await page.getByRole('dialog').getByRole('combobox').click()
  await page.getByRole('option', { name: '实验室' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '移动', exact: true }).click()
  await expect(page.getByText('部门已移动')).toBeVisible()

  // Keyboard: from the first root, the next items are the other root and then its moved child.
  await tree.getByRole('treeitem', { name: /总部/ }).focus()
  await page.keyboard.press('ArrowDown')
  await expect(tree.getByRole('treeitem', { name: /实验室/ })).toBeFocused()
  await page.keyboard.press('ArrowRight')
  await expect(tree.getByRole('treeitem', { name: /销售部/ })).toBeFocused()
  await expect(tree.getByRole('treeitem', { name: /销售部/ })).toHaveAttribute('aria-level', '2')
  await page.keyboard.press('Enter')
  await expect(page.getByRole('heading', { name: '销售部' })).toBeVisible()
})

// Runs last: it changes the administrator's password.
test('edits the profile and changes the password from the account page', async ({ page }) => {
  await signIn(page)
  await page.getByRole('link', { name: /个人中心/ }).click()
  await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible()

  await page.getByLabel('显示名称').fill('超级管理员')
  await page.getByRole('button', { name: '保存资料' }).click()
  await expect(page.getByText('资料已保存')).toBeVisible()
  await expect(page.getByRole('banner').getByText('超级管理员')).toBeVisible()

  await page.getByLabel('当前密码').fill('a long enough password')
  await page.getByLabel(/^新密码/).fill('short')
  await page.getByLabel('确认新密码').fill('short')
  await page.getByRole('button', { name: '修改密码' }).click()
  await expect(page.locator('form#password').getByRole('alert')).toContainText('密码')

  await page.getByLabel(/^新密码/).fill('a brand new long password')
  await page.getByLabel('确认新密码').fill('a brand new long password')
  await page.getByRole('button', { name: '修改密码' }).click()
  await expect(page.getByText('密码已修改，其他设备上的会话已结束')).toBeVisible()

  await page.getByRole('button', { name: /退出登录/ }).click()
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill('a brand new long password')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByRole('heading', { name: '工作空间概览' })).toBeVisible()

  // The audit trail backs the login history, including the wrong password typed in an earlier test.
  await page.getByRole('link', { name: /个人中心/ }).click()
  const history = page.locator('section').filter({ hasText: '最近登录记录' })
  await expect(history.getByText('登录成功').first()).toBeVisible()
  await expect(history.getByText('退出登录').first()).toBeVisible()
  await expect(history.getByText('登录失败 · 密码错误')).toBeVisible()
})
