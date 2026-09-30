// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { flatten } from '../../../tests/unit/messages'
import zhCN from './zh-CN'

describe('zh-CN messages', () => {
  it('has no empty messages', () => {
    expect(Object.entries(flatten(zhCN)).filter(([, message]) => !message.trim())).toEqual([])
  })
})
