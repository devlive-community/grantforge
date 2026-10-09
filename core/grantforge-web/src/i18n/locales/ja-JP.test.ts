// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import jaJP from './ja-JP'
import zhCN from './zh-CN'
import { flatten } from '../../../tests/unit/messages'

const placeholders = (message: string) => [...message.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort()

describe('ja-JP messages', () => {
  const japanese = flatten(jaJP)
  const chinese = flatten(zhCN)

  it('translates exactly the zh-CN keys', () => {
    expect(Object.keys(japanese).sort()).toEqual(Object.keys(chinese).sort())
  })

  it('only uses placeholders the zh-CN message provides', () => {
    // A translation may leave out a parameter (English "Name" for "{kind}名称") but never invent one.
    for (const [key, message] of Object.entries(japanese)) {
      expect(placeholders(chinese[key] ?? ''), key).toEqual(expect.arrayContaining(placeholders(message)))
    }
  })

  it('has no empty messages', () => {
    expect(Object.entries(japanese).filter(([, message]) => !message.trim())).toEqual([])
  })
})
