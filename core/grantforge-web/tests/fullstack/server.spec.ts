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
    await expect(page.getByRole('tree', { name: '资源树' }).getByRole('treeitem', { name: new RegExp(name) })).toHaveAttribute('aria-selected', 'true')
  }
  await create('新建顶级资源', '系统管理', 'system')
  await create('添加下级资源', '用户管理', 'system.user.list', '页面')
  await create('添加下级资源', '导出', 'system.user.btn.export', '按钮')
  await create('新建顶级资源', '审计', 'audit')

  const tree = page.getByRole('tree', { name: '资源树' })
  await expect(tree.getByRole('treeitem', { name: /导出/ })).toHaveAttribute('aria-level', '3')
  // Drag the audit module above the system module.
  await tree.getByRole('treeitem', { name: /审计/ }).dragTo(tree.getByRole('treeitem', { name: /系统管理/ }), { targetPosition: { x: 40, y: 2 } })
  await expect(page.getByText('资源已移动').last()).toBeVisible()
  await expect.poll(async () => {
    const labels = await tree.getByRole('treeitem', { level: 1 }).allTextContents()
    return labels.findIndex(label => label.includes('审计')) < labels.findIndex(label => label.includes('系统管理'))
  }).toBe(true)
  // Drag the page, with its button, into the audit module.
  await tree.getByRole('treeitem', { name: /用户管理/ }).dragTo(tree.getByRole('treeitem', { name: /审计/ }))
  await expect(tree.getByRole('treeitem', { name: /用户管理/ })).toHaveAttribute('aria-level', '2')
  await tree.getByRole('treeitem', { name: /审计/ }).click()
  await expect(page.locator('div:has(> dt:text-is("下级资源")) > dd')).toHaveText('1')

  // A button cannot live outside a page: the server refuses it as well.
  await tree.getByRole('treeitem', { name: /导出/ }).click()
  await page.getByRole('button', { name: '移动到…' }).click()
  await page.getByRole('dialog').getByRole('combobox').click()
  await expect(page.getByRole('option')).toHaveText(['— 用户管理'])
  await page.keyboard.press('Escape')
})

test('links a button to the APIs it needs and draws the dependencies', async ({ page }) => {
  await signIn(page)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '资源目录' }).click()
  const tree = page.getByRole('tree', { name: '资源树' })
  // The button created by the catalog test above.
  await tree.getByRole('treeitem', { name: /导出/ }).click()
  const dependencies = page.getByRole('region', { name: '依赖关系' })
  await expect(dependencies).toContainText('还没有依赖。')
  await dependencies.getByRole('button', { name: '添加依赖' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox', { name: /^依赖的资源/ }).click()
  await page.getByRole('option', { name: /system\.user\.export/ }).click()
  await dialog.getByRole('button', { name: '添加依赖' }).click()
  await expect(dependencies).toContainText('api:system.user.export')
  await expect(dependencies).toContainText('必需')

  await dependencies.getByRole('button', { name: '依赖关系图' }).click()
  await expect(page.getByRole('img', { name: /依赖关系图，共 1 个相关资源/ })).toBeVisible()
  await page.keyboard.press('Escape')

  // The API's own panel says which button needs it.
  await tree.getByRole('treeitem', { name: /api:system\.user\.export/ }).click()
  await expect(page.getByRole('region', { name: '依赖关系' })).toContainText('导出')
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
