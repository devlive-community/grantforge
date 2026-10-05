// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { errorCodes, expandGenerated, operations } from '@/lib/reference'

function repository(): string {
  const root = mkdtempSync(join(tmpdir(), 'repo-'))
  const java = join(root, 'core/grantforge-demo/src/main/java/demo')
  const i18n = join(root, 'core/grantforge-demo/src/main/resources/i18n')
  const api = join(root, 'core/grantforge-web/src/api')
  for (const dir of [java, i18n, api]) mkdirSync(dir, { recursive: true })
  writeFileSync(join(java, 'DemoErrorCode.java'), 'enum DemoErrorCode {\n    LATER("GF-DEMO-002", 409, "error.demo.later"),\n'
    + '    FIRST("GF-DEMO-001", 404, "error.demo.first");\n}\n')
  writeFileSync(join(i18n, 'messages_zh_CN.properties'), 'error.demo.first=找不到 | 了\n')
  writeFileSync(join(api, 'openapi.json'), JSON.stringify({ paths: {
    '/api/v1/users': { get: { tags: ['user-controller'], 'x-permission': 'system.user.read' }, post: { tags: ['user-controller'], 'x-permission': 'authenticated' } },
    '/api/v1/setup': { post: { tags: ['setup-controller'] } },
    '/api/v1/new': { get: { tags: ['new-controller'], 'x-permission': 'public' } },
  } }))
  return root
}

describe('reference', () => {
  it('reads the error codes with their Chinese messages, in code order', () => {
    expect(errorCodes(repository())).toEqual([
      { code: 'GF-DEMO-001', status: 404, name: 'FIRST', message: '找不到 | 了', module: 'demo' },
      { code: 'GF-DEMO-002', status: 409, name: 'LATER', message: '', module: 'demo' },
    ])
  })

  it('reads the operations and who may call them', () => {
    expect(operations(repository()).map(op => `${op.method} ${op.path} ${op.access}`)).toEqual([
      'GET /api/v1/new public', 'POST /api/v1/setup public', 'GET /api/v1/users system.user.read', 'POST /api/v1/users authenticated'])
  })

  it('expands the generated sections, known areas first', () => {
    const page = expandGenerated('# API\n\n{{generated:api}}\n\n{{generated:errors}}', repository())
    expect(page.indexOf('### 初始化与注册')).toBeLessThan(page.indexOf('### 用户'))
    expect(page.indexOf('### 用户')).toBeLessThan(page.indexOf('### new-controller'))
    expect(page).toContain('| `GET` | `/api/v1/users` | `system.user.read` |')
    expect(page).toContain('| `POST` | `/api/v1/users` | 登录即可 |')
    expect(page).toContain('| `GF-DEMO-001` | 404 | 找不到 \\| 了 |')
    expect(page).toContain('| `GF-DEMO-002` | 409 | LATER |')
    expect(page).not.toContain('{{generated')
  })

  it('covers the real contract and error codes', () => {
    expect(operations().length).toBeGreaterThan(100)
    const codes = errorCodes()
    expect(codes.some(entry => entry.code === 'GF-SECURITY-002' && entry.message !== '')).toBe(true)
    expect(new Set(codes.map(entry => entry.code)).size).toBe(codes.length)
  })
})
