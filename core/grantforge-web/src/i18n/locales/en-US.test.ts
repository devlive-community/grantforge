// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import enUS from './en-US'
import zhCN from './zh-CN'
import { flatten } from '../../../tests/unit/messages'

const placeholders = (message: string) => [...message.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort()

describe('en-US messages', () => {
  const english = flatten(enUS)
  const chinese = flatten(zhCN)

  it('translates exactly the zh-CN keys', () => {
    expect(Object.keys(english).sort()).toEqual(Object.keys(chinese).sort())
  })

  it('keeps every placeholder of the zh-CN message', () => {
    for (const [key, message] of Object.entries(chinese)) {
      expect(placeholders(english[key] ?? ''), key).toEqual(placeholders(message))
    }
  })

  it('has no empty messages', () => {
    expect(Object.entries(english).filter(([, message]) => !message.trim())).toEqual([])
  })
})
