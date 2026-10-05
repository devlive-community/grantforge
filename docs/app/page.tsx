// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import Link from 'next/link'
import { sections } from '@/lib/navigation'

const features = [
  { title: '用户与组织', text: '多租户、部门树、用户组与岗位，CSV 批量导入导出，接入 LDAP/AD 与 OIDC 身份源。', href: '/guide/users/' },
  { title: '功能授权', text: '菜单、页面、按钮与 API 统一建模为资源，角色可继承，授权前就能看到影响范围。', href: '/guide/roles/' },
  { title: '数据与字段权限', text: '按条件限定可见的行，按角色隐藏、脱敏或只读字段，业务代码只需一行接入。', href: '/guide/data-permissions/' },
  { title: '可解释与审计', text: '回答“他为什么能/不能”，模拟授权变更，全量审计可筛选导出。', href: '/guide/explain/' },
  { title: '治理', text: '职责分离、权限申请与限时授权、定期复核，两步验证与敏感操作二次确认。', href: '/guide/sod/' },
  { title: '应用接入', text: 'OAuth 2.1 / OpenID Connect、权限查询开放 API、Spring Boot Starter 与 JavaScript SDK。', href: '/integration/overview/' },
]

export default function Home() {
  return (
    <main>
      <section className="relative overflow-hidden bg-[#101828] text-white">
        <div className="absolute -right-40 top-10 size-[640px] rounded-full bg-indigo-600/20 blur-3xl" />
        <div className="absolute -left-40 bottom-0 size-[520px] rounded-full bg-teal-600/10 blur-3xl" />
        <div className="relative mx-auto grid max-w-[90rem] items-center gap-14 px-5 py-20 lg:grid-cols-[1fr_1.15fr] lg:py-28">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full border border-indigo-400/20 bg-indigo-400/10 px-3 py-1.5 text-xs text-indigo-200"><span className="size-1.5 rounded-full bg-teal-400" />开源 · MIT · Java 17 / Spring Boot 4</span>
            <h1 className="mt-7 text-4xl font-semibold leading-tight tracking-tight sm:text-5xl">每一项授权，<br /><span className="text-indigo-300">都有清晰的边界。</span></h1>
            <p className="mt-6 max-w-xl text-base leading-8 text-slate-300">GrantForge 是面向开发者的统一权限平台：管理用户与组织，给角色授予菜单、按钮、API、数据行与字段，再通过标准协议让你的应用直接使用这些权限。</p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Link href="/start/quick-start/" className="rounded-xl bg-brand px-5 py-3 text-sm font-medium text-white shadow-lg shadow-indigo-900/40 transition hover:bg-indigo-500">五分钟上手</Link>
              <Link href="/start/introduction/" className="rounded-xl border border-white/15 px-5 py-3 text-sm font-medium text-white transition hover:bg-white/5">了解产品</Link>
              <a href="https://github.com/devlive-community/grantforge" target="_blank" rel="noreferrer" className="rounded-xl px-5 py-3 text-sm font-medium text-slate-300 transition hover:text-white">GitHub →</a>
            </div>
          </div>
          <img src="/screenshots/dashboard.png" alt="GrantForge 控制台的工作空间概览" className="w-full rounded-2xl border border-white/10 shadow-2xl shadow-black/50" />
        </div>
      </section>
      <section className="mx-auto max-w-[90rem] px-5 py-20">
        <p className="eyebrow text-brand">能力</p>
        <h2 className="mt-3 text-3xl font-semibold tracking-tight">从登录到每一行数据</h2>
        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {features.map(feature => (
            <Link key={feature.title} href={feature.href} className="panel group p-6 transition hover:border-brand/40 hover:shadow-lg hover:shadow-brand/5">
              <h3 className="font-semibold">{feature.title}</h3>
              <p className="mt-2 text-sm leading-6 text-muted">{feature.text}</p>
              <span className="mt-4 inline-block text-sm font-medium text-brand opacity-0 transition group-hover:opacity-100">阅读文档 →</span>
            </Link>
          ))}
        </div>
      </section>
      <section className="border-y border-line bg-surface">
        <div className="mx-auto grid max-w-[90rem] gap-8 px-5 py-16 md:grid-cols-2 lg:grid-cols-5">
          {sections.map(section => (
            <div key={section.id}>
              <h3 className="font-semibold">{section.title}</h3>
              <p className="mt-2 text-sm leading-6 text-muted">{section.description}</p>
              <ul className="mt-4 space-y-1.5 text-sm">
                {section.groups.flatMap(group => group.pages).slice(0, 5).map(page => <li key={page.slug}><Link href={`/${page.slug}/`} className="text-muted hover:text-brand">{page.title}</Link></li>)}
              </ul>
            </div>
          ))}
        </div>
      </section>
      <footer className="mx-auto flex max-w-[90rem] flex-wrap justify-between gap-4 px-5 py-10 text-xs text-muted">
        <span>© 2026 Devlive Community · 以 MIT 许可证开源</span>
        <a href="https://github.com/devlive-community/grantforge" target="_blank" rel="noreferrer" className="hover:text-brand">github.com/devlive-community/grantforge</a>
      </footer>
    </main>
  )
}
