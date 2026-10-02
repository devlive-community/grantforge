// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { i18n } from '@/i18n'
import { dataLabels } from './dataLabels'

describe('data labels', () => {
  it('names known parts and keeps the server names of unknown ones', () => {
    const labels = dataLabels(i18n.global.t as (key: string) => string)
    expect(labels.scope('ORG_AND_CHILDREN')).toBe('本部门及下级')
    expect(labels.action('EXPORT')).toBe('导出')
    expect(labels.effect('DENY')).toBe('拒绝')
    expect(labels.effect('ALLOW')).toBe('允许')
    expect(labels.operator('starts_with')).toBe('开头是')
    expect(labels.operator('like')).toBe('like')
    expect(labels.variable('subject.orgUnitIds')).toBe('当前用户的部门')
    expect(labels.variable('subject.secret')).toBe('subject.secret')
    expect(labels.entity('user', 'Users')).toBe('用户')
    expect(labels.entity('invoice', 'Invoices')).toBe('Invoices')
    expect(labels.field('email', 'E-mail')).toBe('邮箱')
    expect(labels.field('amount', 'Amount')).toBe('Amount')
  })
})
