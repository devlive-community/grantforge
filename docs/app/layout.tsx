// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Metadata } from 'next'
import type { ReactNode } from 'react'
import LocaleGate from '@/components/LocaleGate'
import SiteHeader from '@/components/SiteHeader'
import './globals.css'

export const metadata: Metadata = {
  title: { default: 'GrantForge 文档', template: '%s · GrantForge 文档' },
  description: 'GrantForge 是开源的统一权限平台：用户与组织、角色与功能授权、数据与字段权限、应用接入与标准协议。',
  icons: { icon: '/images/grantforge-logo.png' },
}

// Applies the remembered or the system theme before the page paints, so it does not flash.
const theme = `try{var t=localStorage.getItem('GrantForgeDocsTheme');if(t==='dark'||(!t&&matchMedia('(prefers-color-scheme: dark)').matches))document.documentElement.classList.add('dark')}catch(e){}`

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <head><script dangerouslySetInnerHTML={{ __html: theme }} /></head>
      <body>
        <LocaleGate />
        <SiteHeader />
        {children}
      </body>
    </html>
  )
}
