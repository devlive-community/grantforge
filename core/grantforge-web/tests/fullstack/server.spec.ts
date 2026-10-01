// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { expect, test, type APIRequestContext } from '@playwright/test'

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

  await page.getByRole('button', { name: /退出登录/ }).click()
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await page.goto('/#/dashboard')
  await expect(page).toHaveURL(/#\/auth\/login/)
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
