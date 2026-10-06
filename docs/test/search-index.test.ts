// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { searchTokens } from '@/lib/search-index'

describe('search tokens', () => {
  it('keeps whole words for the languages that space them', () => {
    expect(searchTokens('Roles and grants')).toEqual(['roles', 'and', 'grants'])
    expect(searchTokens('Роли и полномочия')).toEqual(['роли', 'и', 'полномочия'])
    expect(searchTokens('역할과 권한 부여')).toEqual(['역할과', '권한', '부여'])
  })

  it('splits Chinese into single characters, because it has no spaces', () => {
    expect(searchTokens('角色与授权')).toEqual(['角', '色', '与', '授', '权'])
  })

  it('drops punctuation and keeps version numbers whole', () => {
    expect(searchTokens('직무 분리와 권한 요청을 설명합니다.')).toEqual(['직무', '분리와', '권한', '요청을', '설명합니다'])
    expect(searchTokens('그랜드포지 v1.0 배포, agents/grantforge-agent-hdfs 참고')).toEqual([
      '그랜드포지', 'v1.0', '배포', 'agents', 'grantforge-agent-hdfs', '참고',
    ])
    expect(searchTokens('…')).toEqual([])
  })

  it('is case-insensitive, so a query matches an indexed title', () => {
    expect(searchTokens('EN')).toEqual(searchTokens('en'))
    expect(searchTokens('한국어')).toEqual(['한국어'])
  })
})
