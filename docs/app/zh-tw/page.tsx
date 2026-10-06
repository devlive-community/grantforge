// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import Landing from '@/components/Landing'

export const metadata: Metadata = {
  title: { absolute: 'GrantForge 文件' },
  description: 'GrantForge 是面向開發者的統一權限平台：管理使用者與組織，為角色授予選單、按鈕、API、資料列與欄位，再透過標準協定讓你的應用程式直接使用這些權限。',
}

export default function TraditionalChineseHome() {
  return <Landing locale="zh-tw" />
}
