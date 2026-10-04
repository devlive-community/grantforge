// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { expect, test, type Browser, type Page } from '@playwright/test'
import { ADMIN, NOTES, SAM, SHOP } from './environment'

/** Signs in at GrantForge's console sign-in page, where an application sent the browser. */
async function signInAtGrantForge(page: Page, user: { username: string, password: string }) {
  await expect(page).toHaveURL(/#\/auth\/login\?authorize=/)
  await page.getByLabel('用户名', { exact: true }).fill(user.username)
  await page.getByLabel('密码', { exact: true }).fill(user.password)
  await page.getByRole('button', { name: '登录工作空间' }).click()
}

async function shopAs(browser: Browser, user: { username: string, password: string }) {
  const page = await (await browser.newContext()).newPage()
  await page.goto(SHOP)
  await page.getByRole('button', { name: 'Sign in with GrantForge' }).click()
  await signInAtGrantForge(page, user)
  // Back at the shop, from another origin: signed in with PKCE and told what the user may do through CORS.
  await expect(page.locator('#user')).toHaveText(user.username)
  return page
}

test('the shop shows each user the buttons and orders GrantForge allows', async ({ browser }) => {
  const sam = await shopAs(browser, SAM)
  await expect(sam.getByRole('button', { name: 'Place order' })).toBeVisible()
  await sam.getByLabel('Title').fill('Paper for Sam')
  await sam.getByRole('button', { name: 'Place order' }).click()
  const samsOrder = sam.locator('[data-order="Paper for Sam"]')
  await expect(samsOrder).toBeVisible()
  // Buyers may not delete: the button is not shown, and the API refuses anyway.
  await expect(samsOrder.getByRole('button', { name: 'Delete' })).toBeHidden()
  const refused = await sam.evaluate(async () => {
    const tokens = JSON.parse(sessionStorage.getItem('grantforge.tokens') ?? '{}') as { accessToken?: string }
    return (await fetch('/api/orders/1', { method: 'DELETE', headers: { Authorization: `Bearer ${tokens.accessToken}` } })).status
  })
  expect(refused).toBe(403)

  const admin = await shopAs(browser, ADMIN)
  await admin.getByLabel('Title').fill('Pens for admin')
  await admin.getByRole('button', { name: 'Place order' }).click()
  // Managers see every order of the tenant and may delete them.
  await expect(admin.locator('[data-order="Paper for Sam"]')).toBeVisible()
  const adminsOrder = admin.locator('[data-order="Pens for admin"]')
  await expect(adminsOrder.getByRole('button', { name: 'Delete' })).toBeVisible()
  await adminsOrder.getByRole('button', { name: 'Delete' }).click()
  await expect(adminsOrder).toHaveCount(0)

  // Buyers see their own orders only.
  await admin.getByLabel('Title').fill('Ink for admin')
  await admin.getByRole('button', { name: 'Place order' }).click()
  await expect(admin.locator('[data-order="Ink for admin"]')).toBeVisible()
  await sam.reload()
  await expect(sam.locator('[data-order="Paper for Sam"]')).toBeVisible()
  await expect(sam.locator('[data-order="Ink for admin"]')).toHaveCount(0)

  await sam.getByRole('button', { name: 'Sign out' }).click()
  await expect(sam.getByRole('button', { name: 'Sign in with GrantForge' })).toBeVisible()
})

test('the notes application lets writers write, through a server-side sign-in', async ({ browser }) => {
  const sam = await (await browser.newContext()).newPage()
  await sam.goto(NOTES)
  await sam.getByRole('link', { name: 'Sign in with GrantForge' }).click()
  await signInAtGrantForge(sam, SAM)
  await expect(sam.locator('#user')).toContainText('Signed in as sam')
  await expect(sam.locator('#write')).toHaveCount(0)

  const admin = await (await browser.newContext()).newPage()
  await admin.goto(NOTES)
  await admin.getByRole('link', { name: 'Sign in with GrantForge' }).click()
  await signInAtGrantForge(admin, ADMIN)
  await admin.getByLabel('Note').fill('Order more ink')
  await admin.getByRole('button', { name: 'Write note' }).click()
  await expect(admin.locator('#notes')).toContainText('admin: Order more ink')
  // The note is for everybody to read; only writing needs the permission.
  await sam.reload()
  await expect(sam.locator('#notes')).toContainText('admin: Order more ink')
})
