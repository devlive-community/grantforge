// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test'

// The screenshots of the documentation, from a fresh server: first-run setup, demo data of a made-up company through
// the API, then one picture per page the documentation shows. Each picture is its own test, so one that breaks after
// a console change names itself and the others are still taken.

const OUT = process.env.GRANTFORGE_DOCS_SCREENSHOTS ?? join(process.cwd(), '../../docs/public/screenshots')
const ADMIN = { username: 'admin', password: 'a long enough password' }

test.describe.configure({ mode: 'serial' })

interface Seeded { roleId: Record<string, string>; serviceId: string }
let seeded: Seeded = { roleId: {}, serviceId: '' }

async function shoot(page: Page, name: string, target?: Locator) {
  // Wait for the page to settle: no spinner, fonts loaded, nothing moving.
  await page.waitForLoadState('networkidle')
  await page.evaluate(() => document.fonts.ready)
  await page.mouse.move(0, 0)
  mkdirSync(OUT, { recursive: true })
  await (target ?? page).screenshot({ path: join(OUT, `${name}.png`), animations: 'disabled' })
}

async function signIn(page: Page) {
  await page.goto('/#/auth/login')
  await page.getByLabel('用户名', { exact: true }).fill(ADMIN.username)
  await page.getByLabel('密码', { exact: true }).fill(ADMIN.password)
  await page.getByRole('button', { name: '登录工作空间' }).click()
  await expect(page.getByRole('heading', { name: '工作空间概览' })).toBeVisible()
}

async function open(page: Page, route: string, heading?: string | RegExp) {
  await page.goto(`/#/${route}`)
  if (heading) await expect(page.getByRole('heading', { name: heading }).first()).toBeVisible()
}

/** Calls the console API as the signed-in administrator; fails the seeding with the server's answer. */
function api(request: APIRequestContext, xsrf: string) {
  return async <T = Record<string, unknown>>(method: 'GET' | 'POST' | 'PUT', path: string, data?: unknown): Promise<T> => {
    const response = await request.fetch(path, { method, data, headers: { 'X-XSRF-TOKEN': xsrf } })
    expect(response.ok(), `${method} ${path}: ${response.status()} ${await response.text()}`).toBeTruthy()
    const text = await response.text()
    return (text ? JSON.parse(text) : {}) as T
  }
}

test('sets up the installation and seeds the demo company', async ({ page }) => {
  test.setTimeout(120_000)
  const token = process.env.GRANTFORGE_E2E_SETUP_TOKEN ?? ''
  expect(token, 'GRANTFORGE_E2E_SETUP_TOKEN').not.toBe('')
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '初始化 GrantForge' })).toBeVisible()
  await page.getByLabel('初始化令牌').fill(token)
  await page.getByLabel('组织名称').fill('星河科技')
  await page.getByLabel('用户名').fill(ADMIN.username)
  await page.getByLabel('密码', { exact: true }).fill(ADMIN.password)
  await page.getByLabel('确认密码').fill(ADMIN.password)
  await page.getByRole('button', { name: '完成初始化' }).click()
  await expect(page.getByRole('heading', { name: '初始化完成' })).toBeVisible()
  await signIn(page)

  const xsrf = (await page.context().cookies()).find(cookie => cookie.name === 'XSRF-TOKEN')?.value ?? ''
  const call = api(page.request, xsrf)
  type Id = { id: string }

  // Departments, positions, people and groups.
  const unit = async (code: string, name: string, parentId?: string) => (await call<Id>('POST', '/api/v1/org-units', { code, name, parentId })).id
  const hq = await unit('hq', '总部')
  const rnd = await unit('rnd', '研发中心', hq)
  const platform = await unit('platform', '平台组', rnd)
  const data = await unit('data', '数据组', rnd)
  const finance = await unit('finance', '财务部', hq)
  const sales = await unit('sales', '销售部', hq)
  await unit('east-sales', '华东销售', sales)
  const position = async (code: string, name: string, sortOrder: number) =>
    (await call<Id>('POST', '/api/v1/positions', { code, name, sortOrder })).id
  const engineer = await position('engineer', '研发工程师', 1)
  const financeManager = await position('finance-manager', '财务经理', 2)
  const salesRep = await position('sales-rep', '销售代表', 3)
  const people: [string, string, string, string[]][] = [
    ['zhangwei', '张伟', platform, [engineer]], ['liuyang', '刘洋', data, [engineer]], ['lina', '李娜', finance, [financeManager]],
    ['chenjing', '陈静', finance, []], ['wangfang', '王芳', sales, [salesRep]], ['zhaolei', '赵磊', sales, [salesRep]],
    ['sunli', '孙丽', rnd, []], ['zhoujie', '周杰', platform, [engineer]],
  ]
  const accounts = new Map<string, string>()
  const account = (username: string) => accounts.get(username) ?? ''
  for (const [username, displayName, primaryUnitId, positionIds] of people) {
    const created = await call<{ user: Id }>('POST', '/api/v1/users', { username, password: `${displayName} 的演示密码 2026`,
      profile: { displayName, email: `${username}@xinghe.example`, primaryUnitId, otherUnitIds: [], positionIds } })
    accounts.set(username, created.user.id)
  }
  const group = async (code: string, name: string, description: string, members: string[]) => {
    const created = await call<Id>('POST', '/api/v1/groups', { code, name, description })
    await call('POST', `/api/v1/groups/${created.id}/members`, { accountIds: members.map(account) })
    return created.id
  }
  const oncall = await group('oncall', '值班组', '夜间与节假日值班', ['zhangwei', 'liuyang', 'zhoujie'])
  await group('polaris', '北极星项目组', '跨部门的数据平台项目', ['zhangwei', 'liuyang', 'wangfang', 'sunli'])

  // Roles, their grants, rows and fields, and who holds them.
  const role = async (code: string, name: string, description: string) => (await call<Id>('POST', '/api/v1/roles', { code, name, description })).id
  const auditors = await role('auditors', '审计员', '查看审计日志与用户信息')
  const pay = await role('finance-pay', '财务付款', '发起付款')
  const approve = await role('finance-approve', '财务审批', '审批付款')
  const analyst = await role('sales-analyst', '销售分析', '查看销售数据')
  const operator = await role('operators', '运维值班', '处理告警与会话')
  seeded = { roleId: { auditors, pay, approve, analyst, operator }, serviceId: '' }

  const consoleApp = (await call<{ id: string; code: string }[]>('GET', '/api/v1/applications')).find(app => app.code === 'grantforge-console')
  if (!consoleApp) throw new Error('the console application is missing')
  const resources = await call<{ id: string; code: string }[]>('GET', `/api/v1/applications/${consoleApp.id}/resources`)
  const allow = (codes: string[]) => resources.filter(resource => codes.includes(resource.code))
    .map(resource => ({ resourceId: resource.id, effect: 'ALLOW' }))
  await call('PUT', `/api/v1/roles/${auditors}/grants`, { applicationId: consoleApp.id,
    changes: allow(['system.user', 'system.user.btn.edit', 'system.audit', 'data.audit']) })
  await call('PUT', `/api/v1/roles/${operator}/grants`, { applicationId: consoleApp.id, changes: allow(['system.session', 'system.audit']) })
  await call('POST', `/api/v1/roles/${auditors}/data-policies`, { entityCode: 'user', action: 'READ', effect: 'ALLOW', scope: 'TENANT', orgUnitIds: [] })
  await call('POST', `/api/v1/roles/${auditors}/data-policies`, { entityCode: 'user', action: 'UPDATE', effect: 'ALLOW', scope: 'SELF', orgUnitIds: [] })
  await call('POST', `/api/v1/roles/${auditors}/data-policies`, { entityCode: 'user', action: 'EXPORT', effect: 'ALLOW',
    scope: 'CUSTOM_ORGS', orgUnitIds: [rnd] })
  const entities = await call<{ entities: { code: string; securedFields: { code: string }[] }[] }>('GET', '/api/v1/data-entities')
  const userFields = entities.entities.find(entity => entity.code === 'user')?.securedFields.map(field => field.code) ?? []
  const fieldPolicy = (fieldCode: string, readMode: string, maskStrategy?: string, writeMode = 'READONLY') =>
    userFields.includes(fieldCode) ? [{ entityCode: 'user', fieldCode, readMode, maskStrategy, writeMode }] : []
  await call('PUT', `/api/v1/roles/${auditors}/field-policies`, { policies: [
    ...fieldPolicy('email', 'MASKED', 'EMAIL'), ...fieldPolicy('lastLoginAt', 'HIDDEN')] })
  const assign = (roleId: string, subjectType: string, subjectId: string, extra: object = {}) =>
    call('POST', `/api/v1/roles/${roleId}/assignments`, { subjectType, subjectId, ...extra })
  await assign(auditors, 'USER', account('sunli'))
  await assign(auditors, 'POSITION', financeManager, {})
  await assign(operator, 'GROUP', oncall)
  await assign(analyst, 'ORG_UNIT', sales, { includeSubUnits: true })
  await assign(pay, 'USER', account('chenjing'))
  await assign(pay, 'USER', account('lina'))
  await assign(approve, 'USER', account('lina'), { validTo: '2026-12-31T00:00:00Z' })

  // Governance: incompatible roles, requests and a review.
  await call('POST', '/api/v1/sod-constraints', { code: 'pay-approve', name: '付款与审批分离', description: '同一人不能既发起又审批付款',
    mode: 'REPORT', enabled: true, maxRoles: 1, roleIds: [pay, approve] })
  await call('POST', '/api/v1/sod-constraints', { code: 'audit-ops', name: '审计与运维分离', mode: 'ENFORCE', enabled: true, maxRoles: 1,
    roleIds: [auditors, operator] })
  await call('PUT', '/api/v1/requestable-roles', { roles: [{ roleId: analyst, maxDays: 30 }, { roleId: auditors, maxDays: 7 }] })
  await call('POST', '/api/v1/me/access-requests', { roleId: analyst, days: 14, reason: '准备季度经营分析报告' })
  const review = await call<Id>('POST', '/api/v1/access-reviews', { name: '财务角色季度复核', description: '每季度复核财务相关角色的持有人',
    roleIds: [pay, approve], durationDays: 14, intervalDays: 90, enabled: true, unreviewed: 'KEEP' })
  await call('POST', `/api/v1/access-reviews/${review.id}/start`)

  // Platform: another tenant, a directory, an application with an OAuth client.
  await call('POST', '/api/v1/tenants', { code: 'xinghe-east', name: '星河科技（华东）', adminUsername: 'east-admin',
    adminDisplayName: '华东管理员', adminPassword: 'east admin password 2026' })
  await call('POST', '/api/v1/identity-sources', { type: 'LDAP', code: 'corp-ldap', name: '公司目录', enabled: true, provisioning: true,
    secret: 'directory bind secret', ldap: { url: 'ldap://ldap.xinghe.example:389', baseDn: 'ou=people,dc=xinghe,dc=example',
      bindDn: 'cn=grantforge,dc=xinghe,dc=example', userFilter: '(&(objectClass=person)(uid={0}))', usernameAttribute: 'uid', idAttribute: 'entryUUID',
      displayNameAttribute: 'cn', emailAttribute: 'mail', disableMissing: true } })
  const shop = await call<Id>('POST', '/api/v1/applications', { code: 'shop', name: '星河商城', description: '面向客户的在线商城' })
  await call('POST', `/api/v1/applications/${shop.id}/clients`, { type: 'CONFIDENTIAL', settings: { name: '星河商城',
    redirectUris: ['https://shop.xinghe.example/login/callback'], scopes: ['openid', 'profile', 'permissions'],
    grants: ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'] } })

  // External data: a service of the example plugin, a policy, an agent that reports.
  const service = await call<Id>('POST', '/api/v1/services', { name: 'warehouse', label: '数据仓库', serviceType: 'example', enabled: true,
    description: '经营分析数据仓库', values: { url: 'example://warehouse', timeout: '30', password: 'example' } })
  seeded.serviceId = service.id
  const item = (accessTypes: string[], users: string[], groups: string[] = []) => ({ accessTypes, users, groups, roles: [], conditions: [] })
  const values = (...list: string[]) => ({ values: list, excludes: false, recursive: false })
  await call('POST', `/api/v1/services/${service.id}/policies`, { name: '销售库只读', description: '销售数据仅供查询', type: 'ACCESS',
    enabled: true, labels: ['销售'], document: { resources: { database: values('sales'), table: values('*') },
      allow: [item(['select'], ['admin', 'wangfang'])], allowExceptions: [], deny: [], denyExceptions: [], validity: [] } })
  await call('POST', `/api/v1/services/${service.id}/policies`, { name: '人事库限制', type: 'ACCESS', enabled: true, labels: ['人事'],
    document: { resources: { database: values('hr'), table: values('salaries') }, allow: [item(['select'], ['lina'])], allowExceptions: [],
      deny: [item(['select', 'update'], ['zhaolei'])], denyExceptions: [], validity: [] } })
  const issued = await call<{ secret: string }>('POST', `/api/v1/services/${service.id}/agent-tokens`, { name: 'warehouse-cluster' })
  const agent = { Authorization: `Bearer ${issued.secret}` }
  const beat = await (await page.request.post('/api/v1/agent/heartbeat', { headers: agent, data: { instance: 'warehouse-node-1', host: 'dw-01' } }))
    .json() as { policyVersion: number }
  const snapshot = await (await page.request.get('/api/v1/agent/policies', { headers: agent })).json() as { policies: { id: number; name: string }[] }
  await page.request.post('/api/v1/agent/heartbeat', { headers: agent, data: { instance: 'warehouse-node-1', appliedPolicyVersion: beat.policyVersion } })
  const policy = snapshot.policies.find(entry => entry.name === '销售库只读')
  const at = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString()
  const reported = await page.request.post('/api/v1/agent/access-events', { headers: agent, data: { instance: 'warehouse-node-1', events: [
    { eventId: 'demo-1', occurredAt: at(42), user: 'wangfang', resource: 'sales.orders', accessType: 'select', outcome: 'ALLOWED',
      policyId: policy?.id, policyVersion: beat.policyVersion },
    { eventId: 'demo-2', occurredAt: at(31), user: 'admin', resource: 'sales.customers', accessType: 'select', outcome: 'ALLOWED',
      policyId: policy?.id, policyVersion: beat.policyVersion },
    { eventId: 'demo-3', occurredAt: at(17), user: 'zhaolei', resource: 'hr.salaries', accessType: 'select', outcome: 'DENIED' },
    { eventId: 'demo-4', occurredAt: at(5), user: 'liuyang', resource: 'ops.hosts', accessType: 'update', outcome: 'DENIED' },
  ] } })
  expect(reported.ok()).toBeTruthy()
})

test('the sign-in page', async ({ page }) => {
  await page.goto('/#/auth/login')
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await shoot(page, 'login')
})

test.describe('signed in', () => {
  test.beforeEach(async ({ page }) => signIn(page))

  const pages: [string, string, string | RegExp | undefined][] = [
    ['dashboard', 'dashboard', '工作空间概览'],
    ['account', 'account', undefined],
    ['sessions', 'admin/sessions', undefined],
    ['users', 'admin/users', undefined],
    ['groups', 'admin/groups', undefined],
    ['positions', 'admin/positions', undefined],
    ['transfer', 'admin/transfer', undefined],
    ['identity-sources', 'admin/identity-sources', undefined],
    ['roles', 'admin/roles', undefined],
    ['audit', 'admin/audit', undefined],
    ['sod', 'admin/sod', undefined],
    ['requests', 'requests', undefined],
    ['access-approvals', 'admin/access-requests', undefined],
    ['access-reviews', 'admin/access-reviews', undefined],
    ['tenants', 'platform/tenants', undefined],
    ['resources', 'platform/resources', undefined],
    ['apis', 'platform/apis', undefined],
    ['health', 'platform/health', undefined],
    ['oauth', 'platform/oauth', undefined],
    ['plugins', 'platform/plugins', undefined],
    ['services', 'data/services', undefined],
    ['agents', 'data/agents', undefined],
    ['access-audit', 'data/access-audit', undefined],
  ]
  for (const [name, route, heading] of pages) {
    test(`the ${name} page`, async ({ page }) => {
      await open(page, route, heading)
      await shoot(page, name)
    })
  }

  test('the organization with a department chosen', async ({ page }) => {
    await open(page, 'admin/org')
    await page.getByRole('treeitem', { name: /研发中心/ }).first().click()
    await expect(page.getByRole('heading', { name: '研发中心' })).toBeVisible()
    await shoot(page, 'org')
  })

  test('the policies of a service', async ({ page }) => {
    await open(page, `data/policies?service=${seeded.serviceId}`)
    await expect(page.locator('[data-policy="销售库只读"]')).toBeVisible()
    await shoot(page, 'policies')
  })

  test('the grants of a role', async ({ page }) => {
    await open(page, 'admin/roles')
    await page.getByRole('row').filter({ hasText: 'auditors' }).getByRole('button', { name: '为 审计员 授权' }).click()
    const grants = page.getByRole('dialog', { name: '审计员 的授权' })
    await grants.getByRole('switch', { name: '只看有授权的' }).click()
    await expect(grants.locator('[data-resource="system.user"]')).toBeVisible()
    await shoot(page, 'role-grants')
  })

  test('the data permissions of a role', async ({ page }) => {
    await open(page, 'admin/roles')
    await page.getByRole('row').filter({ hasText: 'auditors' }).getByRole('button', { name: '设置 审计员 的数据权限' }).click()
    await expect(page.getByRole('dialog', { name: '数据权限：审计员' }).locator('[data-policy="user:READ"]')).toBeVisible()
    await shoot(page, 'role-data')
  })

  test('the field permissions of a role', async ({ page }) => {
    await open(page, 'admin/roles')
    await page.getByRole('row').filter({ hasText: 'auditors' }).getByRole('button', { name: '设置 审计员 的字段权限' }).click()
    await expect(page.getByRole('dialog', { name: '字段权限：审计员' }).locator('[data-field]').first()).toBeVisible()
    await shoot(page, 'role-fields')
  })

  test('the effective permissions of a user', async ({ page }) => {
    await open(page, 'admin/users')
    await page.getByRole('button', { name: '查看 孙丽 的有效权限' }).click()
    const effective = page.getByRole('dialog', { name: '有效权限：孙丽' })
    await expect(effective.locator('[data-role="auditors"]')).toBeVisible()
    await shoot(page, 'user-permissions')
  })
})
