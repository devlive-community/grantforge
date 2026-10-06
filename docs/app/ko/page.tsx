// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Landing from '@/components/Landing'

export const metadata: Metadata = {
  title: { absolute: 'GrantForge 문서' },
  description: 'GrantForge는 개발자를 위한 통합 권한 플랫폼입니다. 사용자와 조직을 관리하고 역할에 기능·데이터·필드 권한을 부여하며, 표준 프로토콜로 애플리케이션이 그 권한을 사용하게 합니다.',
}

export default function KoreanHome() {
  return <Landing locale="ko" />
}
