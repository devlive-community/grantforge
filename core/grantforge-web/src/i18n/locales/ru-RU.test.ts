// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import ruRU from './ru-RU'
import zhCN from './zh-CN'
import { flatten } from '../../../tests/unit/messages'

const placeholders = (message: string) => [...message.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort()

describe('ru-RU messages', () => {
  const russian = flatten(ruRU)
  const chinese = flatten(zhCN)

  it('translates exactly the zh-CN keys', () => {
    expect(Object.keys(russian).sort()).toEqual(Object.keys(chinese).sort())
  })

  it('only uses placeholders the zh-CN message provides', () => {
    // A translation may leave out a parameter (Russian "Имя" for "{kind}名称") but never invent one.
    for (const [key, message] of Object.entries(russian)) {
      expect(placeholders(chinese[key] ?? ''), key).toEqual(expect.arrayContaining(placeholders(message)))
    }
  })

  it('has no empty messages', () => {
    expect(Object.entries(russian).filter(([, message]) => !message.trim())).toEqual([])
  })
})
