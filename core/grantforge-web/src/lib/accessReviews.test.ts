// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { decisionLabel, fallbackEffect, fallbackLabel, outcomeLabel, roundStatusLabel, subjectTypeLabel } from './accessReviews'

describe('access review labels', () => {
  it('names rounds, decisions and outcomes', () => {
    expect(['OPEN', 'COMPLETED', 'CANCELLED'].map(status => roundStatusLabel(status as 'OPEN'))).toEqual(['进行中', '已完成', '已取消'])
    expect(['PENDING', 'KEEP', 'REVOKE'].map(decision => decisionLabel(decision as 'KEEP'))).toEqual(['待处理', '保留', '撤销'])
    expect(['KEPT', 'REVOKED', 'GONE'].map(outcome => outcomeLabel(outcome as 'KEPT'))).toEqual(['已保留', '已移除', '已不存在'])
  })

  it('names the fallback and the subjects', () => {
    expect(fallbackLabel('REVOKE')).toBe('撤销')
    expect(fallbackEffect('KEEP')).toBe('保持不变')
    expect(fallbackEffect('REVOKE')).toBe('按撤销处理')
    expect(['USER', 'GROUP', 'ORG_UNIT', 'POSITION'].map(type => subjectTypeLabel(type as 'USER'))).toEqual(['用户', '用户组', '部门', '岗位'])
  })
})
