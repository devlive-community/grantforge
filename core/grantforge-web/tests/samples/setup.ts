// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { request, type APIRequestContext } from '@playwright/test'
import { ADMIN, GRANTFORGE, NOTES, SAM, SHOP } from './environment'

const ROOT = resolve(import.meta.dirname, '../../../..')
export const PIDS = resolve(import.meta.dirname, '../../test-results/samples-pids.json')

/** Signs in to GrantForge's console API and returns a context that sends the CSRF token. */
async function console(user: { username: string, password: string }): Promise<{ api: APIRequestContext, xsrf: () => Promise<string> }> {
  const api = await request.newContext({ baseURL: GRANTFORGE })
  const xsrf = async () => (await api.storageState()).cookies.find(cookie => cookie.name === 'XSRF-TOKEN')?.value ?? ''
  await api.get('/api/v1/bootstrap')
  const signedIn = await api.post('/api/v1/auth/login', { headers: { 'X-XSRF-TOKEN': await xsrf() }, data: user })
  if (!signedIn.ok()) throw new Error(`${user.username} could not sign in: ${signedIn.status()} ${await signedIn.text()}`)
  // Signing in renews the CSRF token; the next answer carries it, as the console finds it.
  await api.get('/api/v1/me')
  return { api, xsrf }
}

async function call(api: APIRequestContext, xsrf: () => Promise<string>, method: 'post' | 'put', path: string, data: unknown) {
  const response = await api[method](path, { headers: { 'X-XSRF-TOKEN': await xsrf() }, data })
  if (!response.ok()) throw new Error(`${method.toUpperCase()} ${path}: ${response.status()} ${await response.text()}`)
  return response.status() === 204 ? null : response.json()
}

/** Registers an application with its resources; returns the resource IDs by code. */
async function application(api: APIRequestContext, xsrf: () => Promise<string>, code: string, name: string,
  tree: { type: string, code: string, parent?: string }[]) {
  const created = await call(api, xsrf, 'post', '/api/v1/applications', { code, name })
  const ids: Record<string, string> = {}
  for (const resource of tree) {
    const made = await call(api, xsrf, 'post', `/api/v1/applications/${created.id}/resources`, { type: resource.type, code: resource.code,
      name: resource.code, parentId: resource.parent ? ids[resource.parent] : undefined })
    ids[resource.code] = made.id
  }
  return { id: created.id as string, ids }
}

async function waitFor(url: string, what: string) {
  for (let attempt = 0; attempt < 120; attempt++) {
    try {
      if ((await fetch(url)).ok) return
    } catch {
      // Not up yet.
    }
    await new Promise(done => setTimeout(done, 500))
  }
  throw new Error(`${what} did not start at ${url}`)
}

export default async function setup() {
  const { api, xsrf } = await console(ADMIN)
  const found = await (await api.get('/api/v1/users?q=admin')).json() as { items: { id: string, username: string }[] }
  const adminId = found.items.find(user => user.username === ADMIN.username)?.id
  if (!adminId) throw new Error('No admin account')

  // Sam, an ordinary user of the platform tenant, with a password of his own.
  const sam = await call(api, xsrf, 'post', '/api/v1/users', { username: SAM.username, password: 'an initial password for him',
    profile: { displayName: 'Sam', otherUnitIds: [], positionIds: [] } })
  const samConsole = await console({ username: SAM.username, password: 'an initial password for him' })
  await call(samConsole.api, samConsole.xsrf, 'post', '/api/v1/me/password', { currentPassword: 'an initial password for him', newPassword: SAM.password })

  // The shop: a page with two buttons, three APIs; a browser client and a server client that declares its entity.
  const shop = await application(api, xsrf, 'shop', 'Shop', [
    { type: 'MODULE', code: 'shop' }, { type: 'PAGE', code: 'shop.orders', parent: 'shop' },
    { type: 'ACTION', code: 'shop.orders.btn.create', parent: 'shop.orders' }, { type: 'ACTION', code: 'shop.orders.btn.delete', parent: 'shop.orders' },
    { type: 'API', code: 'orders.read' }, { type: 'API', code: 'orders.create' }, { type: 'API', code: 'orders.delete' }])
  const browser = await call(api, xsrf, 'post', `/api/v1/applications/${shop.id}/clients`, { type: 'PUBLIC', settings: { name: 'Shop browser',
    redirectUris: [`${SHOP}/`], scopes: ['openid', 'profile', 'permissions'], grants: ['AUTHORIZATION_CODE'] } })
  const server = await call(api, xsrf, 'post', `/api/v1/applications/${shop.id}/clients`, { type: 'CONFIDENTIAL', settings: { name: 'Shop server',
    scopes: ['catalog'], grants: ['CLIENT_CREDENTIALS'] } })

  // The notes application: a page with a button and an API; a confidential client signing users in.
  const notes = await application(api, xsrf, 'notes', 'Notes', [
    { type: 'MODULE', code: 'notes' }, { type: 'PAGE', code: 'notes.home', parent: 'notes' },
    { type: 'ACTION', code: 'notes.btn.write', parent: 'notes.home' }, { type: 'API', code: 'notes.write' }])
  const notesClient = await call(api, xsrf, 'post', `/api/v1/applications/${notes.id}/clients`, { type: 'CONFIDENTIAL', settings: {
    name: 'Notes', redirectUris: [`${NOTES}/login/oauth2/code/grantforge`], scopes: ['openid', 'profile', 'permissions'],
    grants: ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'] } })

  // Roles: buyers place and read their own orders; managers also see and delete every order; writers write notes.
  const role = async (code: string, applicationId: string, resources: string[]) => {
    const made = await call(api, xsrf, 'post', '/api/v1/roles', { code, name: code })
    await call(api, xsrf, 'put', `/api/v1/roles/${made.id}/grants`, { applicationId, changes: resources.map(resourceId => ({ resourceId, effect: 'ALLOW' })) })
    return made.id as string
  }
  const buyers = await role('shop-buyers', shop.id, ['shop.orders.btn.create', 'orders.read', 'orders.create'].map(code => shop.ids[code] ?? ''))
  const managers = await role('shop-managers', shop.id, ['shop.orders.btn.create', 'shop.orders.btn.delete', 'orders.read', 'orders.create',
    'orders.delete'].map(code => shop.ids[code] ?? ''))
  const writers = await role('notes-writers', notes.id, ['notes.btn.write', 'notes.write'].map(code => notes.ids[code] ?? ''))
  const assign = (roleId: string, subjectId: string) => call(api, xsrf, 'post', `/api/v1/roles/${roleId}/assignments`, { subjectType: 'USER', subjectId })
  await assign(buyers, sam.user.id)
  await assign(managers, adminId)
  await assign(writers, adminId)

  // Start the samples with their clients; the shop declares its order entity once it is ready.
  const java = process.env.JAVA_HOME ? `${process.env.JAVA_HOME}/bin/java` : 'java'
  const start = (jar: string, env: Record<string, string>) => spawn(java, ['-jar', resolve(ROOT, jar)], {
    env: { ...process.env, GRANTFORGE_URL: GRANTFORGE, ...env }, stdio: ['ignore', 'inherit', 'inherit'] })
  const shopProcess = start('samples/shop/target/grantforge-sample-shop-2026.1.0.jar', { SHOP_BROWSER_CLIENT_ID: browser.client.clientId,
    SHOP_CLIENT_ID: server.client.clientId, SHOP_CLIENT_SECRET: server.secret, SHOP_SDK_DIRECTORY: resolve(ROOT, 'sdk/grantforge-js/dist') })
  const notesProcess = start('samples/notes/target/grantforge-sample-notes-2026.1.0.jar', { NOTES_CLIENT_ID: notesClient.client.clientId,
    NOTES_CLIENT_SECRET: notesClient.secret })
  writeFileSync(PIDS, JSON.stringify([shopProcess.pid, notesProcess.pid]))
  await waitFor(`${SHOP}/config.json`, 'The shop')
  await waitFor(`${NOTES}/`, 'The notes application')
  for (let attempt = 0; ; attempt++) {
    const entities = await (await api.get('/api/v1/data-entities')).json() as { entities: { code: string }[] }
    if (entities.entities.some(entity => entity.code === 'shop:order')) break
    if (attempt > 60) throw new Error('The shop did not declare its order entity')
    await new Promise(done => setTimeout(done, 500))
  }
  const policy = (roleId: string, action: string, scope: string) => call(api, xsrf, 'post', `/api/v1/roles/${roleId}/data-policies`,
    { entityCode: 'shop:order', action, scope })
  await policy(buyers, 'READ', 'SELF')
  await policy(managers, 'READ', 'TENANT')
  await policy(managers, 'DELETE', 'TENANT')
  await api.dispose()
  await samConsole.api.dispose()
}
