// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Landing from '@/components/Landing'

export const metadata: Metadata = {
  title: { absolute: 'GrantForge ドキュメント' },
  description: 'GrantForge は開発者向けの統合権限プラットフォームです。ユーザーと組織を管理し、ロールに機能・データ・フィールドの権限を付与し、標準プロトコルでアプリケーションにその権限を使わせます。',
}

export default function JapaneseHome() {
  return <Landing locale="ja" />
}
