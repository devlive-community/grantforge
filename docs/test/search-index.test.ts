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
    expect(searchTokens('ロールと権限の付与')).toEqual(['ロールと', '権', '限', 'の', '付', '与'])
    // Accents are folded away, so a query typed without them still matches a French word written with them.
    expect(searchTokens('Rôles et autorisations')).toEqual(['roles', 'et', 'autorisations'])
    expect(searchTokens('rôles')).toEqual(searchTokens('roles'))
    expect(searchTokens('Guide d’utilisation')).toEqual(['guide', 'd', 'utilisation'])
  })

  it('splits Chinese into single characters, because it has no spaces', () => {
    expect(searchTokens('角色与授权')).toEqual(['角', '色', '与', '授', '权'])
  })

  it('splits Japanese Han characters like Chinese and keeps kana runs whole', () => {
    expect(searchTokens('テナントとユーザー')).toEqual(['テナントとユーザー'])
    expect(searchTokens('データ権限を設定する')).toEqual(['データ', '権', '限', 'を', '設', '定', 'する'])
    expect(searchTokens('HDFS NameNode エージェントの指標')).toEqual(['hdfs', 'namenode', 'エージェントの', '指', '標'])
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
    // Han characters keep their single-character tokens, exactly as in Chinese.
    expect(searchTokens('日本語')).toEqual(['日', '本', '語'])
  })
})
