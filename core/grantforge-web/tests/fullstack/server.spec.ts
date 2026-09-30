// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { expect, test } from '@playwright/test'

test('serves the packaged console, which opens on the sign-in page', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/')
  await expect(page).toHaveURL(/#\/auth\/login/)
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await expect(page.locator('img[src="/static/images/grantforge-logo.png"]').first()).toBeVisible()
  expect(errors).toEqual([])
})

test('answers unknown APIs with a localised RFC 9457 problem and the request ID', async ({ request }) => {
  const response = await request.get('/api/v1/does-not-exist', {
    headers: { 'Accept-Language': 'zh-CN', 'X-Request-Id': 'fullstack-1' },
  })
  expect(response.status()).toBe(404)
  expect(response.headers()['content-type']).toContain('application/problem+json')
  expect(response.headers()['x-request-id']).toBe('fullstack-1')
  expect(await response.json()).toMatchObject({
    status: 404,
    code: 'GF-COMMON-404',
    messageKey: 'error.common.not-found',
    detail: '请求的资源不存在。',
    requestId: 'fullstack-1',
  })
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
