// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { readFileSync } from 'node:fs'
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
  // The tree reloads after the move; wait for its new order, or the reload would take the focus away mid-way.
  await expect(tree.getByRole('treeitem')).toHaveText([/总部/, /实验室/, /销售部/])

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

test('builds the console resource catalog and rearranges it by dragging', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '资源目录' }).click()
  await expect(page.getByRole('combobox', { name: '应用' })).toHaveText('GrantForge Console')
  // The server already registered its API permissions below the built-in API module.
  await expect(page.getByRole('tree', { name: '资源树' }).getByRole('treeitem', { name: /模块\s*API/ })).toBeVisible()
  const create = async (button: string, name: string, code: string, type?: string) => {
    await page.getByRole('button', { name: button }).click()
    const dialog = page.getByRole('dialog')
    if (type) {
      await dialog.getByRole('combobox', { name: /^资源类型/ }).click()
      await page.getByRole('option', { name: type, exact: true }).click()
    }
    await dialog.getByLabel(/^资源名称/).fill(name)
    await dialog.getByLabel(/^资源编码/).fill(code)
    await dialog.getByRole('button', { name: '新建资源' }).click()
    // The new resource is selected once the tree has reloaded; the next step adds below it.
    // Matched by the code at the end of the name: other resources may share words of the name.
    const exact = new RegExp(` ${code.replaceAll('.', '\\.')}$`)
    await expect(page.getByRole('tree', { name: '资源树' }).getByRole('treeitem', { name: exact })).toHaveAttribute('aria-selected', 'true')
  }
  await create('新建顶级资源', '演示模块', 'demo')
  await create('添加下级资源', '演示页面', 'demo.page', '页面')
  await create('添加下级资源', '演示导出', 'demo.page.btn.export', '按钮')
  await create('新建顶级资源', '审计', 'audit')

  const tree = page.getByRole('tree', { name: '资源树' })
  await expect(tree.getByRole('treeitem', { name: /演示导出/ })).toHaveAttribute('aria-level', '3')
  // Drag the audit module above the system module.
  await tree.getByRole('treeitem', { name: / audit$/ }).dragTo(tree.getByRole('treeitem', { name: /演示模块/ }), { targetPosition: { x: 40, y: 2 } })
  await expect(page.getByText('资源已移动').last()).toBeVisible()
  await expect.poll(async () => {
    const labels = await tree.getByRole('treeitem', { level: 1 }).allTextContents()
    return labels.findIndex(label => label.includes('审计')) < labels.findIndex(label => label.includes('演示模块'))
  }).toBe(true)
  // Drag the page, with its button, into the audit module.
  await tree.getByRole('treeitem', { name: /演示页面/ }).dragTo(tree.getByRole('treeitem', { name: / audit$/ }))
  await expect(tree.getByRole('treeitem', { name: /演示页面/ })).toHaveAttribute('aria-level', '2')
  await tree.getByRole('treeitem', { name: / audit$/ }).click()
  await expect(page.locator('div:has(> dt:text-is("下级资源")) > dd')).toHaveText('1')

  // A button cannot live outside a page: the server refuses it as well.
  await tree.getByRole('treeitem', { name: /演示导出/ }).click()
  await page.getByRole('button', { name: '移动到…' }).click()
  await page.getByRole('dialog').getByRole('combobox').click()
  // Only pages qualify: the demo page and the console's own pages, never a module or the top level.
  await expect(page.getByRole('option', { name: '— 演示页面' })).toBeVisible()
  await expect(page.getByRole('option', { name: /演示模块|（顶级）/ })).toHaveCount(0)
  await page.keyboard.press('Escape')
})

test('links a button to the APIs it needs and draws the dependencies', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '资源目录' }).click()
  const tree = page.getByRole('tree', { name: '资源树' })
  // The button created by the catalog test above.
  await tree.getByRole('treeitem', { name: /演示导出/ }).click()
  const dependencies = page.getByRole('region', { name: '依赖关系' })
  await expect(dependencies).toContainText('还没有依赖。')
  await dependencies.getByRole('button', { name: '添加依赖' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox', { name: /^依赖的资源/ }).click()
  await page.getByRole('option', { name: /system\.user\.export/ }).click()
  await dialog.getByRole('button', { name: '添加依赖' }).click()
  // The dependency's impact on roles of every tenant is shown first; adding it takes a confirmation.
  await expect(dialog.locator('[data-impact]')).toBeVisible()
  await dialog.getByRole('button', { name: '确认添加' }).click()
  await expect(dependencies).toContainText('api:system.user.export')
  await expect(dependencies).toContainText('必需')

  await dependencies.getByRole('button', { name: '依赖关系图' }).click()
  await expect(page.getByRole('img', { name: /依赖关系图，共 1 个相关资源/ })).toBeVisible()
  await page.keyboard.press('Escape')

  // The API's own panel says which button needs it.
  await tree.getByRole('treeitem', { name: /api:system\.user\.export/ }).click()
  await expect(page.getByRole('region', { name: '依赖关系' })).toContainText('演示导出')
})

test('lists the secured fields below their entities with the APIs they appear in', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '资源目录' }).click()
  const tree = page.getByRole('tree', { name: '资源树' })
  const email = tree.getByRole('treeitem', { name: / entity:user\.email$/ })
  await expect(email).toHaveAttribute('aria-level', '3')
  await email.click()
  const usages = page.getByRole('region', { name: '出现在的接口' })
  await expect(usages.locator('[data-usage="GET /api/v1/users"]')).toContainText('返回')
  await expect(usages.locator('[data-usage="PUT /api/v1/users/{id}"]').filter({ hasText: '接收' })).toBeVisible()
})

test('lists the API catalog the server registered and confirms its changes', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: 'API 目录' }).click()
  const users = page.getByRole('row').filter({ hasText: '/api/v1/users/{id}' }).filter({ hasText: 'UserController#find' })
  await expect(users).toContainText('system.user.read')
  await expect(users).toContainText('新增')
  await expect(page.getByRole('row').filter({ hasText: '/api/v1/bootstrap' })).toContainText('公开')

  await page.getByRole('button', { name: /确认变更/ }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText(/已确认 \d+ 项变更/)).toBeVisible()
  await expect(users).not.toContainText('新增')
  await expect(page.getByRole('button', { name: /确认变更（0）/ })).toBeDisabled()
})

test('manages a user from creation through an administrator lock', async ({ page, browser }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '用户管理' }).click()
  await page.getByRole('button', { name: '创建用户' }).click()
  const dialog = page.getByRole('dialog', { name: '创建用户' })
  await dialog.getByLabel(/^用户名/).fill('dora')
  await dialog.getByLabel(/^显示名称/).fill('多拉')
  await dialog.getByRole('combobox', { name: '主部门' }).click()
  await dialog.getByRole('option', { name: '实验室', exact: true }).click()
  await dialog.getByLabel(/^初始密码/).fill('an initial password')
  await dialog.getByLabel(/^确认初始密码/).fill('an initial password')
  await dialog.getByRole('button', { name: '创建用户' }).click()
  await expect(page.getByText('用户已创建')).toBeVisible()
  const row = page.getByRole('row').filter({ hasText: 'dora' })
  await expect(row).toContainText('实验室')
  await expect(row).toContainText('待改密')

  const other = await browser.newContext({ baseURL: test.info().project.use.baseURL })
  const dora = await other.newPage()
  const signInDora = async (password: string) => {
    await dora.goto('/#/auth/login')
    await dora.getByLabel('用户名', { exact: true }).fill('dora')
    await dora.getByLabel('密码', { exact: true }).fill(password)
    await dora.getByRole('button', { name: '登录工作空间' }).click()
  }
  await signInDora('an initial password')
  await dora.getByLabel(/^当前密码/).fill('an initial password')
  await dora.getByLabel(/^新密码/).fill('a secret only she knows')
  await dora.getByLabel(/^确认新密码/).fill('a secret only she knows')
  await dora.getByRole('button', { name: '修改密码' }).click()
  await expect(dora.getByText('密码已修改，其他设备上的会话已结束')).toBeVisible()

  await row.getByRole('button', { name: '锁定 多拉' }).click()
  await page.getByRole('dialog', { name: '请确认' }).getByRole('button', { name: '锁定', exact: true }).click()
  await expect(page.getByText('用户已锁定')).toBeVisible()
  await expect(row).toContainText('已锁定')
  // The lock ends dora's session at once and explains itself at the next sign-in.
  await dora.reload()
  await expect(dora).toHaveURL(/#\/auth\/login/)
  await signInDora('a secret only she knows')
  await expect(dora.getByRole('alert')).toHaveText('账号已被管理员锁定，请联系管理员。')

  await row.getByRole('button', { name: '解锁 多拉' }).click()
  await expect(page.getByText('用户已解锁')).toBeVisible()
  await signInDora('a secret only she knows')
  await expect(dora.getByRole('heading', { name: '工作空间概览' })).toBeVisible()
  await other.close()
})

test('groups accounts and changes the members in batches', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '用户组' }).click()
  await page.getByRole('button', { name: '创建用户组' }).click()
  const form = page.getByRole('dialog', { name: '创建用户组' })
  await form.getByLabel(/^组名称/).fill('实验室成员')
  await form.getByLabel(/^组编码/).fill('lab-members')
  await form.getByRole('button', { name: '创建用户组' }).click()
  await expect(page.getByText('用户组已创建')).toBeVisible()

  const row = page.getByRole('row').filter({ hasText: '实验室成员' })
  await row.getByRole('button', { name: '管理 实验室成员 的成员' }).click()
  const members = page.getByRole('dialog', { name: '实验室成员 的成员' })
  await members.getByRole('checkbox', { name: '多拉' }).check()
  await members.getByRole('button', { name: '添加所选（1）' }).click()
  await expect(page.getByText('已添加 1 位成员')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(row).toContainText('1')

  await row.getByRole('button', { name: '管理 实验室成员 的成员' }).click()
  await members.getByRole('checkbox', { name: '多拉' }).check()
  await members.getByRole('button', { name: '移除所选（1）' }).click()
  await expect(page.getByText('已移除 1 位成员')).toBeVisible()
  await expect(members.getByText('还没有成员')).toBeVisible()
})

test('creates a position and gives it to a user', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '岗位' }).click()
  await page.getByRole('button', { name: '创建岗位' }).click()
  const form = page.getByRole('dialog', { name: '创建岗位' })
  await form.getByLabel(/^岗位名称/).fill('首席研究员')
  await form.getByLabel(/^岗位编码/).fill('chief-researcher')
  await form.getByRole('button', { name: '创建岗位' }).click()
  await expect(page.getByText('岗位已创建')).toBeVisible()

  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '用户管理' }).click()
  await page.getByRole('button', { name: '编辑 多拉' }).click()
  const edit = page.getByRole('dialog', { name: '编辑用户' })
  await edit.getByRole('checkbox', { name: '首席研究员' }).check()
  await edit.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('用户已更新')).toBeVisible()

  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '岗位' }).click()
  const row = page.getByRole('row').filter({ hasText: '首席研究员' })
  await expect(row).toContainText('1')
  await row.getByRole('button', { name: '查看 首席研究员 的任职人员' }).click()
  await expect(page.getByRole('dialog', { name: '首席研究员 的任职人员' })).toContainText('多拉')
})

test('manages roles next to the system roles every tenant has', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '角色管理' }).click()
  const admin = page.getByRole('row').filter({ hasText: 'tenant-admin' })
  await expect(admin).toContainText('租户管理员')
  await expect(page.getByRole('row').filter({ hasText: 'platform-admin' })).toContainText('平台管理员')
  await expect(admin.getByRole('button', { name: /删除/ })).toHaveCount(0)

  await page.getByRole('button', { name: '新建角色' }).click()
  let dialog = page.getByRole('dialog')
  await dialog.getByLabel(/^角色名称/).fill('审计员')
  await dialog.getByLabel(/^角色编码/).fill('auditors')
  await dialog.getByRole('button', { name: '新建角色' }).click()
  const auditors = page.getByRole('row').filter({ hasText: 'auditors' })
  await expect(auditors).toContainText('自定义')
  await auditors.getByRole('button', { name: '停用' }).click()
  await expect(auditors).toContainText('已停用')

  // Give the role to dora (created by the user test above) for a limited time, then look at her roles.
  await auditors.getByRole('button', { name: '分配 审计员' }).click()
  const assign = page.getByRole('dialog', { name: '审计员 的分配' })
  await assign.getByRole('button', { name: '添加分配' }).click()
  await assign.getByLabel(/^搜索/).fill('dora')
  await assign.getByRole('combobox', { name: /^对象/ }).click()
  await page.getByRole('option', { name: /dora/ }).click()
  await assign.getByLabel(/^截止日期/).fill('2099-12-31')
  await assign.getByRole('button', { name: '分配', exact: true }).click()
  await expect(assign.locator('[data-assignment]').filter({ hasText: 'dora' })).toContainText('生效中')
  await page.keyboard.press('Escape')
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '用户管理' }).click()
  await page.getByRole('button', { name: '查看 多拉 的角色' }).click()
  const doraRoles = page.getByRole('dialog')
  await expect(doraRoles.locator('[data-role="auditors"]')).toContainText('直接分配')
  // The role is disabled, so it grants nothing yet.
  await expect(doraRoles.locator('[data-role="auditors"]')).toContainText('角色已停用')
  await page.keyboard.press('Escape')
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '角色管理' }).click()

  // Allow the edit-users button: its page and the APIs it needs follow, then save.
  await auditors.getByRole('button', { name: '为 审计员 授权' }).click()
  const grants = page.getByRole('dialog', { name: '审计员 的授权' })
  await grants.getByLabel('搜索资源').fill('system.user')
  await grants.locator('[data-resource="system.user.btn.edit"]').getByRole('button', { name: '允许' }).click()
  await expect(grants.locator('[data-resource="system.user"]')).toContainText('推导允许')
  await grants.getByLabel('搜索资源').fill('api:system.user.update')
  await expect(grants.locator('[data-resource="api:system.user.update"]')).toContainText('被 编辑用户 需要')
  await grants.getByRole('button', { name: '保存授权' }).click()
  // Saving first shows which roles change and how many people hold them; the role is disabled, so none change yet.
  await expect(grants.locator('[data-impact]')).toContainText('这次修改不会改变任何角色的权限')
  await grants.getByRole('button', { name: '确认保存' }).click()
  await expect(page.getByText('授权已保存')).toBeVisible()
  await page.keyboard.press('Escape')
  // The tenant administrator role is read-only: it has its whole module.
  await admin.getByRole('button', { name: '为 租户管理员 授权' }).click()
  await expect(page.getByRole('dialog').getByRole('note')).toContainText('系统角色')
  await page.keyboard.press('Escape')

  await admin.getByRole('button', { name: '复制 租户管理员' }).click()
  dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: '复制', exact: true }).click()
  await expect(page.getByRole('row').filter({ hasText: 'tenant-admin-copy' })).toContainText('租户管理员（副本）')
})

test('lets a role inherit from others and refuses cycles', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '角色管理' }).click()
  await page.getByRole('button', { name: '新建角色' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/^角色名称/).fill('观察员')
  await dialog.getByLabel(/^角色编码/).fill('observers')
  await dialog.getByRole('button', { name: '新建角色' }).click()
  const observers = page.getByRole('row').filter({ hasText: 'observers' })

  // Observers inherit from the auditors role made by the roles test.
  await observers.getByRole('button', { name: '设置 观察员 的继承' }).click()
  let inheritance = page.getByRole('dialog', { name: '观察员 的继承' })
  await inheritance.locator('[data-role="auditors"]').getByRole('checkbox').check()
  await inheritance.getByRole('button', { name: '保存继承' }).click()
  await expect(page.getByText('继承关系已保存')).toBeVisible()
  await expect(inheritance.locator('[data-list="ancestors"]')).toContainText('审计员')
  await page.keyboard.press('Escape')
  await expect(observers).toContainText('继承自 审计员')

  // Auditors cannot inherit from observers in turn: that would be a cycle, so the choice is unavailable.
  const auditors = page.getByRole('row').filter({ hasText: 'auditors' })
  await auditors.getByRole('button', { name: '设置 审计员 的继承' }).click()
  inheritance = page.getByRole('dialog', { name: '审计员 的继承' })
  await expect(inheritance.locator('[data-role="observers"]')).toContainText('已继承本角色')
  await expect(inheritance.locator('[data-role="observers"]').getByRole('checkbox')).toBeDisabled()
  await expect(inheritance.locator('[data-list="descendants"]')).toContainText('观察员')
})

test('shows a user only what their roles allow and refuses the rest', async ({ page, browser }) => {
  // dora holds the auditors role from the test above, which may edit users; enabling it puts that into effect.
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '角色管理' }).click()
  const auditors = page.getByRole('row').filter({ hasText: 'auditors' })
  await auditors.getByRole('button', { name: '启用' }).click()
  await expect(auditors).not.toContainText('已停用')
  // Rows need data permissions as well: the role sees and edits only its holder's own account.
  await auditors.getByRole('button', { name: '设置 审计员 的数据权限' }).click()
  const data = page.getByRole('dialog', { name: '数据权限：审计员' })
  for (const [action, code] of [['查看', 'READ'], ['修改', 'UPDATE']]) {
    await data.getByRole('button', { name: '添加数据权限' }).click()
    await data.getByRole('combobox', { name: '数据' }).click()
    await page.getByRole('option', { name: '用户', exact: true }).click()
    await data.getByRole('combobox', { name: '操作' }).click()
    await page.getByRole('option', { name: action, exact: true }).click()
    await data.getByRole('combobox', { name: '范围' }).click()
    await page.getByRole('option', { name: '仅本人' }).click()
    await data.getByRole('button', { name: '添加数据权限' }).click()
    await expect(data.locator(`[data-policy="user:${code}"]`)).toContainText('仅本人')
  }
  await page.keyboard.press('Escape')
  await expect(data).toBeHidden()
  // Holders do not see when anyone last signed in.
  await auditors.getByRole('button', { name: '设置 审计员 的字段权限' }).click()
  const fields = page.getByRole('dialog', { name: '字段权限：审计员' })
  await fields.locator('[data-field="user.lastLoginAt"]').getByRole('combobox').first().click()
  await page.getByRole('option', { name: '隐藏', exact: true }).click()
  await fields.getByRole('button', { name: '保存字段权限' }).click()
  await expect(page.getByText('字段权限已保存')).toBeVisible()
  // The users page shows what the role gives her, and why.
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '用户管理' }).click()
  await page.getByRole('button', { name: '查看 多拉 的有效权限' }).click()
  const effective = page.getByRole('dialog', { name: '有效权限：多拉' })
  await expect(effective.locator('[data-role="auditors"]')).toContainText('审计员')
  await expect(effective.locator('[data-rule="user:READ"]')).toContainText('仅本人')
  await expect(effective.locator('[data-field="user.lastLoginAt"]')).toContainText('隐藏')
  await effective.locator('[data-resource="system.user"]').getByRole('button').click()
  await expect(effective.getByRole('status')).toContainText('可以使用')
  await expect(effective.locator('[data-path]').first()).toContainText('审计员')
  // Without the role she would lose the users page; nothing is saved.
  const simulator = effective.getByRole('region', { name: '模拟变更' })
  await simulator.getByRole('group', { name: '假设移除角色' }).getByRole('checkbox', { name: '审计员' }).check()
  await simulator.getByRole('button', { name: '开始模拟' }).click()
  await expect(simulator.locator('[data-lost]').filter({ hasText: '用户管理' })).toBeVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '角色管理' }).click()

  const other = await browser.newContext({ baseURL: test.info().project.use.baseURL })
  const dora = await other.newPage()
  await dora.goto('/#/auth/login')
  await dora.getByLabel('用户名', { exact: true }).fill('dora')
  await dora.getByLabel('密码', { exact: true }).fill('a secret only she knows')
  await dora.getByRole('button', { name: '登录工作空间' }).click()
  const navigation = dora.getByRole('navigation', { name: '主导航' })
  await expect(navigation.getByRole('link', { name: '用户管理' })).toBeVisible()
  await expect(navigation.getByRole('link', { name: '角色管理' })).toHaveCount(0)
  await expect(navigation.getByRole('link', { name: '用户组' })).toHaveCount(0)

  await navigation.getByRole('link', { name: '用户管理' }).click()
  const row = dora.getByRole('row').filter({ hasText: 'dora' })
  await expect(row.getByRole('button', { name: '编辑 多拉' })).toBeVisible()
  await expect(row.getByRole('button', { name: '删除 多拉' })).toBeHidden()
  await expect(dora.getByRole('button', { name: '创建用户' })).toBeHidden()
  await expect(dora.getByRole('columnheader', { name: '最近登录' })).toHaveCount(0)
  // Other accounts lie outside her data permissions, in the list and when asked for directly.
  await expect(dora.getByRole('row').filter({ hasText: 'admin' })).toHaveCount(0)
  const adminId = (await (await page.request.get('/api/v1/users?q=admin')).json()).items
    .find((user: { username: string }) => user.username === 'admin').id
  expect((await other.request.get(`/api/v1/users/${adminId}`)).status()).toBe(404)
  await row.getByRole('button', { name: '编辑 多拉' }).click()
  await dora.getByRole('dialog').getByRole('button', { name: '保存', exact: true }).click()
  await expect(dora.getByText('用户已更新')).toBeVisible()

  // Pages and APIs outside the role are refused even when reached directly.
  await dora.goto('/#/admin/groups')
  await expect(dora).toHaveURL(/#\/common\/403/)
  const token = (await other.cookies()).find(cookie => cookie.name === 'XSRF-TOKEN')?.value ?? ''
  const refused = await other.request.post('/api/v1/groups', { headers: { 'X-XSRF-TOKEN': token }, data: { code: 'x', name: 'X' } })
  expect(refused.status()).toBe(403)
  expect((await refused.json()).code).toBe('GF-SECURITY-002')

  // Disabling the role takes effect at dora's next call: the console reloads her permissions and drops the page.
  await auditors.getByRole('button', { name: '停用' }).click()
  await expect(auditors).toContainText('已停用')
  await dora.goto('/#/dashboard')
  await expect(navigation.getByRole('link', { name: '用户管理' })).toBeVisible()
  await dora.goto('/#/admin/users')
  await expect(navigation.getByRole('link', { name: '用户管理' })).toHaveCount(0)
  await other.close()
})

test('searches and exports the audit log', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '审计日志' }).click()
  await expect(page.locator('[data-event]').filter({ hasText: 'LOGIN_SUCCEEDED' }).first()).toBeVisible()
  // dora was refused the group API in the test above; the refusal is in the log.
  await page.getByRole('combobox', { name: '事件' }).click()
  await page.getByRole('option', { name: 'ACCESS_DENIED', exact: true }).click()
  await page.getByRole('button', { name: '查询' }).click()
  await expect(page.locator('[data-event]').filter({ hasText: 'dora' }).filter({ hasText: 'system.group.create' })).toHaveCount(1)
  const exported = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出 CSV' }).click()
  expect((await exported).suggestedFilename()).toMatch(/^audit-.*\.csv$/)
})

test('checks the catalog for settings that silently do not work', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '目录体检' }).click()
  await expect(page.getByRole('heading', { name: '目录体检' })).toBeVisible()
  // Earlier tests granted and changed resources; whatever they left behind, the report states it and when.
  await expect(page.getByText(/发现 \d+ 个问题 · 体检于/)).toBeVisible()
  await page.getByRole('button', { name: '重新体检' }).click()
  await expect(page.getByText(/发现 \d+ 个问题 · 体检于/)).toBeVisible()
})

test('limits a role to rows a condition selects and previews what a user would see', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '角色管理' }).click()
  await page.getByRole('button', { name: '新建角色' }).click()
  const create = page.getByRole('dialog')
  await create.getByLabel(/^角色名称/).fill('数据查看')
  await create.getByLabel(/^角色编码/).fill('data-viewers')
  await create.getByRole('button', { name: '新建角色' }).click()
  await page.getByRole('button', { name: '设置 数据查看 的数据权限' }).click()
  const dialog = page.getByRole('dialog', { name: '数据权限：数据查看' })
  await expect(dialog.getByText('还没有数据权限')).toBeVisible()
  await dialog.getByRole('button', { name: '添加数据权限' }).click()
  await dialog.getByRole('combobox', { name: '数据' }).click()
  await page.getByRole('option', { name: '用户', exact: true }).click()
  await dialog.getByRole('combobox', { name: '范围' }).click()
  await page.getByRole('option', { name: '按条件' }).click()
  await dialog.getByRole('button', { name: '添加条件', exact: true }).click()
  await dialog.getByLabel(/^值/).fill('admin')
  await dialog.getByRole('button', { name: '添加数据权限' }).click()
  await expect(page.getByText('数据权限已添加')).toBeVisible()
  await expect(dialog.locator('[data-policy="user:READ"]')).toContainText('按条件')

  // With only this role, admin would see the one user called admin.
  await dialog.getByRole('combobox', { name: '数据' }).click()
  await page.getByRole('option', { name: '用户', exact: true }).click()
  await dialog.getByLabel(/^用户名/).fill('admin')
  await dialog.getByRole('button', { name: '预览' }).click()
  await expect(dialog.getByRole('status')).toContainText('只拥有该角色时可见 1 行')
})

test('lists the example plugin and looks for new ones', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '插件' }).click()
  await expect(page.getByRole('heading', { name: '插件', exact: true })).toBeVisible()
  // The test server has the example plugin in its plugins directory: built against the plugin API alone.
  const example = page.locator('[data-plugin="example"]')
  await expect(example).toContainText('运行中')
  await expect(example).toContainText('Example warehouse')
  await page.getByRole('button', { name: '重新扫描' }).click()
  await expect(page.getByText('插件已重新扫描')).toBeVisible()
  await expect(example).toContainText('运行中')
})

test('sends to the services page while there is no service to write policies for', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '策略' }).click()
  await expect(page.getByRole('heading', { name: '策略', exact: true })).toBeVisible()
  await expect(page.getByText('还没有数据服务')).toBeVisible()
  await page.getByRole('link', { name: '先去添加数据服务' }).click()
  await expect(page.getByRole('heading', { name: '数据服务', exact: true })).toBeVisible()
})

test('adds a data service of the example type after testing its connection', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '数据服务' }).click()
  await expect(page.getByText('还没有数据服务')).toBeVisible()
  await page.getByRole('button', { name: '添加服务' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/^服务名称/).fill('warehouse')
  await dialog.getByLabel(/^显示名称/).fill('Warehouse')
  await dialog.getByLabel(/^Address/).fill('example://warehouse')
  await dialog.getByLabel(/^Password/).fill('wrong')
  await dialog.getByRole('button', { name: '测试连接' }).click()
  await expect(dialog.getByRole('status')).toHaveText('连接失败：the example warehouse refused the password')
  await dialog.getByLabel(/^Password/).fill('example')
  await dialog.getByRole('button', { name: '测试连接' }).click()
  await expect(dialog.getByRole('status')).toHaveText('连接成功')
  await dialog.getByRole('button', { name: '添加服务' }).click()
  await expect(page.getByText('服务已添加')).toBeVisible()
  await expect(page.locator('[data-service="warehouse"]')).toContainText('Example warehouse')
})

test('writes an access policy with the generic editor', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '策略' }).click()
  await page.getByRole('button', { name: '添加策略' }).click()
  await page.getByLabel(/^策略名称/).fill('sales readers')
  // Database names come from the service through the plugin's lookup.
  await page.getByRole('combobox', { name: 'Database的值' }).fill('sa')
  await page.getByRole('option', { name: 'sales' }).click()
  const allow = page.locator('[data-items="allow"]')
  await allow.getByRole('combobox', { name: '用户', exact: true }).fill('nobody,')
  await allow.getByRole('checkbox', { name: 'Select' }).check()
  await page.getByRole('button', { name: '添加策略' }).click()
  await expect(allow.getByText('不存在：nobody')).toBeVisible()
  await allow.getByRole('button', { name: '移除 nobody' }).click()
  await allow.getByRole('combobox', { name: '用户', exact: true }).fill('admin,')
  await page.getByRole('button', { name: '添加策略' }).click()
  await expect(page.getByText('策略已添加')).toBeVisible()
  await expect(page.locator('[data-policy="sales readers"]')).toContainText('Database: sales')
})

test('lets an agent download its policies and report accesses the console then shows', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '代理' }).click()
  await expect(page.getByText('还没有代理上报心跳')).toBeVisible()
  await page.getByRole('button', { name: '签发令牌' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel(/^名称/).fill('cluster-a')
  await dialog.getByRole('button', { name: '签发令牌' }).click()
  const secret = (await dialog.locator('[data-issued] code').textContent())?.trim() ?? ''
  expect(secret).toMatch(/^gfa_/)
  await dialog.getByRole('button', { name: '完成' }).click()
  await expect(page.locator('[data-token="cluster-a"]')).toContainText('可用')

  // What an agent does: report, download the signed snapshot, report again with it applied, send what it decided.
  const headers = { Authorization: `Bearer ${secret}` }
  const beat = await page.request.post('/api/v1/agent/heartbeat', { headers, data: { instance: 'e2e-agent', host: 'localhost' } })
  expect(beat.ok()).toBeTruthy()
  const { policyVersion } = await beat.json() as { policyVersion: number }
  const snapshot = await page.request.get('/api/v1/agent/policies', { headers })
  expect(snapshot.ok()).toBeTruthy()
  expect(snapshot.headers()['x-grantforge-signature']).toBeTruthy()
  const body = await snapshot.json() as { policies: { id: number, name: string }[] }
  expect(body.policies.map(policy => policy.name)).toEqual(['sales readers'])
  const unchanged = await page.request.get('/api/v1/agent/policies', { headers: { ...headers, 'If-None-Match': snapshot.headers()['etag'] ?? '' } })
  expect(unchanged.status()).toBe(304)
  await page.request.post('/api/v1/agent/heartbeat', { headers, data: { instance: 'e2e-agent', appliedPolicyVersion: policyVersion } })
  const reported = await page.request.post('/api/v1/agent/access-events', { headers, data: { instance: 'e2e-agent', events: [
    { eventId: 'e2e-1', occurredAt: new Date().toISOString(), user: 'admin', resource: 'sales.orders', accessType: 'select', outcome: 'ALLOWED',
      policyId: body.policies[0]?.id, policyVersion },
    { eventId: 'e2e-2', occurredAt: new Date().toISOString(), user: 'mallory', resource: 'hr.salaries', accessType: 'select', outcome: 'DENIED' },
  ] } })
  expect(await reported.json()).toEqual({ accepted: 2, duplicates: 0, expired: 0 })

  await page.getByRole('button', { name: '刷新' }).click()
  await expect(page.locator('[data-agent="e2e-agent"]')).toContainText('已同步')
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '访问审计' }).click()
  await expect(page.locator('[data-event="e2e-1"]')).toContainText('sales readers')
  await expect(page.locator('[data-event="e2e-2"]')).toContainText('系统自身权限')
  await page.getByLabel(/^用户/).fill('mall')
  await page.getByRole('button', { name: '查询' }).click()
  await expect(page.locator('[data-event]')).toHaveCount(1)
})

test('imports departments and users from CSV files and exports them', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '导入导出' }).click()
  const units = page.locator('section').filter({ has: page.getByRole('heading', { name: '组织架构' }) })
  await units.getByLabel('选择组织架构导入文件').setInputFiles({ name: 'tree.csv', mimeType: 'text/csv',
    buffer: Buffer.from('code,name,parentCode\nrd-web,前端组,rd\nrd,研发中心,hq\n') })
  await units.getByRole('button', { name: '预检', exact: true }).click()
  await expect(units.getByText('预检通过：共 2 行')).toBeVisible()
  await units.getByRole('button', { name: '确认导入 2 行' }).click()
  await expect(units.getByText('已导入 2 条记录。')).toBeVisible()

  const users = page.locator('section').filter({ has: page.getByRole('heading', { name: '用户', exact: true }) })
  const accounts = 'username,password,displayName,primaryUnit\nkai,a long enough password,凯,rd-web\nlee,short,,nowhere\n'
  await users.getByLabel('选择用户导入文件').setInputFiles({ name: 'users.csv', mimeType: 'text/csv', buffer: Buffer.from(accounts) })
  await users.getByRole('button', { name: '预检', exact: true }).click()
  await expect(users.getByText('发现 2 个问题')).toBeVisible()
  await expect(users.getByRole('cell', { name: '找不到部门“nowhere”。' })).toBeVisible()
  await users.getByLabel('选择用户导入文件').setInputFiles({ name: 'users.csv', mimeType: 'text/csv',
    buffer: Buffer.from(accounts.split('\n').slice(0, 2).join('\n')) })
  await users.getByRole('button', { name: '预检', exact: true }).click()
  await users.getByRole('button', { name: '确认导入 1 行' }).click()
  await expect(users.getByText('已导入 1 条记录。')).toBeVisible()

  const pending = page.waitForEvent('download')
  await users.getByRole('button', { name: '导出' }).click()
  const file = await pending
  expect(file.suggestedFilename()).toMatch(/^users-\d{4}-\d{2}-\d{2}\.csv$/)
  const exported = readFileSync(await file.path(), 'utf8')
  expect(exported).toContain('kai,凯,,ACTIVE,rd-web')
})

// Runs last: it changes the administrator's password.
test('edits the profile and changes the password from the account page', async ({ page }) => {
  // A wrong password first, so the login history below has a refusal among its latest entries.
  await page.goto('/#/auth/login')
  await page.getByLabel('用户名', { exact: true }).fill('admin')
  await page.getByLabel('密码', { exact: true }).fill('not the password')
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByRole('alert')).toHaveText('用户名或密码错误。')
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

  // The audit trail backs the login history, including the wrong password typed at the start.
  await page.getByRole('link', { name: /个人中心/ }).click()
  const history = page.locator('section').filter({ hasText: '最近登录记录' })
  await expect(history.getByText('登录成功').first()).toBeVisible()
  await expect(history.getByText('退出登录').first()).toBeVisible()
  await expect(history.getByText('登录失败 · 密码错误')).toBeVisible()
})
