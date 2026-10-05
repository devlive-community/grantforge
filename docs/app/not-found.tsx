// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import Link from 'next/link'

export default function NotFound() {
  return (
    <main className="mx-auto max-w-xl px-5 py-32 text-center">
      <p className="eyebrow text-brand">404</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight">没有这一页</h1>
      <p className="mt-3 text-muted">它可能已经移动了位置。试试顶部的搜索，或回到首页。</p>
      <Link href="/" className="mt-8 inline-block rounded-xl bg-brand px-5 py-2.5 text-sm font-medium text-white">回到首页</Link>
    </main>
  )
}
