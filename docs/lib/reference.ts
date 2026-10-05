// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

/** The repository root, one level above the docs site. */
export const REPOSITORY = join(process.cwd(), '..')

/** One error the API can answer with, as its enum declares it. */
export interface ErrorEntry {
  code: string
  status: number
  name: string
  message: string
  module: string
}

/** One operation of the REST API, as the OpenAPI contract lists it. */
export interface Operation {
  method: string
  path: string
  access: string
  tag: string
}

function files(dir: string, accept: (path: string) => boolean): string[] {
  return readdirSync(dir).flatMap(name => {
    if (name === 'node_modules' || name === 'target' || name.startsWith('.')) return []
    const path = join(dir, name)
    return statSync(path).isDirectory() ? files(path, accept) : accept(path) ? [path] : []
  })
}

/** Reads the Chinese messages of every module, by message key. */
export function messages(root: string = REPOSITORY): Map<string, string> {
  const found = new Map<string, string>()
  for (const file of files(join(root, 'core'), path => /src\/main\/resources\/i18n\/[a-z]+_zh_CN\.properties$/.test(path))) {
    for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
      const match = /^([a-z0-9.-]+)=(.*)$/.exec(line)
      if (match?.[1] && match[2] !== undefined) found.set(match[1], match[2])
    }
  }
  return found
}

/** Lists every error code of the server, from the ErrorCode enums of its modules. */
export function errorCodes(root: string = REPOSITORY): ErrorEntry[] {
  const text = messages(root)
  const entries: ErrorEntry[] = []
  for (const file of files(join(root, 'core'), path => /src\/main\/java\/.+ErrorCode\.java$/.test(path))) {
    const source = readFileSync(file, 'utf8')
    const module = relative(join(root, 'core'), file).split('/')[0]?.replace(/^grantforge-/, '') ?? ''
    for (const match of source.matchAll(/^\s+([A-Z][A-Z0-9_]*)\("(GF-[A-Z]+-\d{3})",\s*(\d{3}),\s*"([a-z0-9.-]+)"\)/gm)) {
      const [, name = '', code = '', status = '0', key = ''] = match
      entries.push({ code, status: Number(status), name, message: text.get(key) ?? '', module })
    }
  }
  return entries.sort((a, b) => a.code.localeCompare(b.code))
}

/** Lists the operations of the REST API from the contract the console is generated from. */
export function operations(root: string = REPOSITORY): Operation[] {
  const contract = JSON.parse(readFileSync(join(root, 'core/grantforge-web/src/api/openapi.json'), 'utf8')) as {
    paths: Record<string, Record<string, { tags?: string[]; 'x-permission'?: string }>>
  }
  const found: Operation[] = []
  for (const [path, methods] of Object.entries(contract.paths)) {
    for (const [method, operation] of Object.entries(methods)) {
      found.push({ method: method.toUpperCase(), path, access: operation['x-permission'] ?? 'public', tag: operation.tags?.[0] ?? 'other' })
    }
  }
  return found.sort((a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method))
}

/** What a controller of the contract is about, in reading order; unknown ones follow under their own name. */
const AREAS: [string, string][] = [
  ['setup-controller', '初始化与注册'], ['auth-controller', '登录与会话'], ['federated-controller', '联合登录'], ['mfa-controller', '两步验证'],
  ['me-controller', '当前用户'], ['session-controller', '在线会话'], ['user-controller', '用户'], ['org-controller', '组织架构'],
  ['group-controller', '用户组'], ['position-controller', '岗位'], ['transfer-controller', '导入导出'], ['identity-source-controller', '身份源'],
  ['tenant-controller', '租户'], ['role-controller', '角色'], ['role-grant-controller', '角色授权'], ['role-inheritance-controller', '角色继承'],
  ['role-assignment-controller', '角色分配'], ['impact-controller', '影响分析'], ['data-policy-controller', '数据权限'],
  ['field-policy-controller', '字段权限'], ['authorization-controller', '权限解释与模拟'], ['sod-controller', '职责分离'],
  ['access-request-controller', '权限申请'], ['access-review-controller', '权限复核'], ['audit-event-controller', '审计日志'],
  ['application-controller', '应用'], ['resource-controller', '资源'], ['dependency-controller', '资源依赖'],
  ['field-usage-controller', '字段出现的接口'], ['api-endpoint-controller', 'API 目录'], ['health-controller', '目录体检'],
  ['client-controller', 'OAuth 客户端'], ['o-auth-controller', '授权服务器'], ['open-authorization-controller', '开放 API：权限'],
  ['open-data-controller', '开放 API：数据'], ['plugin-controller', '插件'], ['service-controller', '数据服务'], ['policy-controller', '策略'],
  ['agent-administration-controller', '代理管理'], ['agent-controller', '代理接口'], ['access-event-controller', '访问审计'],
]

const ACCESS: Record<string, string> = { public: '公开', authenticated: '登录即可' }

function cell(text: string): string {
  return text.replace(/\|/g, '\\|')
}

/** Renders the API reference as Markdown tables, one per area. */
export function apiMarkdown(root: string = REPOSITORY): string {
  const byTag = new Map<string, Operation[]>()
  for (const operation of operations(root)) byTag.set(operation.tag, [...byTag.get(operation.tag) ?? [], operation])
  const known = new Map(AREAS)
  const order = [...AREAS.map(([tag]) => tag).filter(tag => byTag.has(tag)), ...[...byTag.keys()].filter(tag => !known.has(tag)).sort()]
  return order.map(tag => {
    const rows = (byTag.get(tag) ?? []).map(op => `| \`${op.method}\` | \`${cell(op.path)}\` | ${ACCESS[op.access] ?? `\`${op.access}\``} |`)
    return `### ${known.get(tag) ?? tag}\n\n| 方法 | 路径 | 访问要求 |\n| --- | --- | --- |\n${rows.join('\n')}`
  }).join('\n\n')
}

/** Renders the error codes as Markdown tables, one per module. */
export function errorsMarkdown(root: string = REPOSITORY): string {
  const byModule = new Map<string, ErrorEntry[]>()
  for (const entry of errorCodes(root)) byModule.set(entry.module, [...byModule.get(entry.module) ?? [], entry])
  return [...byModule.entries()].map(([module, entries]) =>
    `### ${module}\n\n| 错误码 | HTTP | 含义 |\n| --- | --- | --- |\n${entries.map(entry =>
      `| \`${entry.code}\` | ${entry.status} | ${cell(entry.message || entry.name)} |`).join('\n')}`).join('\n\n')
}

/** Replaces the markers of generated sections in a page's Markdown. */
export function expandGenerated(markdown: string, root: string = REPOSITORY): string {
  return markdown
    .replace('{{generated:api}}', () => apiMarkdown(root))
    .replace('{{generated:errors}}', () => errorsMarkdown(root))
}
