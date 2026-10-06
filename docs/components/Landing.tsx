// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import Link from 'next/link'
import { pageHref } from '@/lib/i18n'
import type { Locale } from '@/lib/i18n'
import { sections, titleOf } from '@/lib/navigation'

const copy = {
  zh: {
    badge: '开源 · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>每一项授权，<br /><span className="text-indigo-300">都有清晰的边界。</span></>,
    heroText: 'GrantForge 是面向开发者的统一权限平台：管理用户与组织，给角色授予菜单、按钮、API、数据行与字段，再通过标准协议让你的应用直接使用这些权限。',
    quickStart: '五分钟上手',
    learnMore: '了解产品',
    capabilities: '能力',
    capabilitiesTitle: '从登录到每一行数据',
    readDocs: '阅读文档 →',
    license: '© 2026 Devlive Community · 以 MIT 许可证开源',
    dashboardAlt: 'GrantForge 控制台的工作空间概览',
  },
  en: {
    badge: 'Open source · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>Every grant,<br /><span className="text-indigo-300">with a clear boundary.</span></>,
    heroText: 'GrantForge is a unified permission platform for developers: manage users and organization, grant menus, buttons, APIs, data rows and fields to roles, and let your application use those permissions over standard protocols.',
    quickStart: 'Set up in five minutes',
    learnMore: 'Learn more',
    capabilities: 'Capabilities',
    capabilitiesTitle: 'From sign-in to every data row',
    readDocs: 'Read the docs →',
    license: '© 2026 Devlive Community · open source under the MIT license',
    dashboardAlt: 'The workspace overview of the GrantForge console',
  },
} as const

const features = [
  {
    title: { zh: '用户与组织', en: 'Users & organization' },
    text: { zh: '多租户、部门树、用户组与岗位，CSV 批量导入导出，接入 LDAP/AD 与 OIDC 身份源。', en: 'Multi-tenancy, a department tree, groups and positions, CSV bulk import/export, and LDAP/AD or OIDC identity sources.' },
    href: '/guide/users/',
  },
  {
    title: { zh: '功能授权', en: 'Functional authorization' },
    text: { zh: '菜单、页面、按钮与 API 统一建模为资源，角色可继承，授权前就能看到影响范围。', en: 'Menus, pages, buttons and APIs are modeled as one resource catalog, roles inherit, and impact is visible before granting.' },
    href: '/guide/roles/',
  },
  {
    title: { zh: '数据与字段权限', en: 'Data & field permissions' },
    text: { zh: '按条件限定可见的行，按角色隐藏、脱敏或只读字段，业务代码只需一行接入。', en: 'Conditions bound the visible rows; fields are hidden, masked or read-only per role, with one line of business code.' },
    href: '/guide/data-permissions/',
  },
  {
    title: { zh: '可解释与审计', en: 'Explainability & audit' },
    text: { zh: '回答“他为什么能/不能”，模拟授权变更，全量审计可筛选导出。', en: 'Answers why somebody can or cannot, simulates grant changes, and filters and exports the full audit trail.' },
    href: '/guide/explain/',
  },
  {
    title: { zh: '治理', en: 'Governance' },
    text: { zh: '职责分离、权限申请与限时授权、定期复核，两步验证与敏感操作二次确认。', en: 'Separation of duty, access requests with expiring grants, periodic reviews, two-factor authentication and step-up verification.' },
    href: '/guide/sod/',
  },
  {
    title: { zh: '应用接入', en: 'Application integration' },
    text: { zh: 'OAuth 2.1 / OpenID Connect、权限查询开放 API、Spring Boot Starter 与 JavaScript SDK。', en: 'OAuth 2.1 / OpenID Connect, a permission open API, the Spring Boot starter and the JavaScript SDK.' },
    href: '/integration/overview/',
  },
] as const

/** The landing page, in the language of its route: / for Chinese and /en/ for English. */
export default function Landing({ locale }: { locale: Locale }) {
  const text = copy[locale]
  return (
    <main>
      <section className="relative overflow-hidden bg-[#101828] text-white">
        <div className="absolute -right-40 top-10 size-[640px] rounded-full bg-indigo-600/20 blur-3xl" />
        <div className="absolute -left-40 bottom-0 size-[520px] rounded-full bg-teal-600/10 blur-3xl" />
        <div className="relative mx-auto grid max-w-[90rem] items-center gap-14 px-5 py-20 lg:grid-cols-[1fr_1.15fr] lg:py-28">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full border border-indigo-400/20 bg-indigo-400/10 px-3 py-1.5 text-xs text-indigo-200"><span className="size-1.5 rounded-full bg-teal-400" />{text.badge}</span>
            <h1 className="mt-7 text-4xl font-semibold leading-tight tracking-tight sm:text-5xl">{text.heroTitle}</h1>
            <p className="mt-6 max-w-xl text-base leading-8 text-slate-300">{text.heroText}</p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Link href={pageHref('start/quick-start', locale)} className="rounded-xl bg-brand px-5 py-3 text-sm font-medium text-white shadow-lg shadow-indigo-900/40 transition hover:bg-indigo-500">{text.quickStart}</Link>
              <Link href={pageHref('start/introduction', locale)} className="rounded-xl border border-white/15 px-5 py-3 text-sm font-medium text-white transition hover:bg-white/5">{text.learnMore}</Link>
              <a href="https://github.com/devlive-community/grantforge" target="_blank" rel="noreferrer" className="rounded-xl px-5 py-3 text-sm font-medium text-slate-300 transition hover:text-white">GitHub →</a>
            </div>
          </div>
          <img src="/screenshots/dashboard.png" alt={text.dashboardAlt} className="w-full rounded-2xl border border-white/10 shadow-2xl shadow-black/50" />
        </div>
      </section>
      <section className="mx-auto max-w-[90rem] px-5 py-20">
        <p className="eyebrow text-brand">{text.capabilities}</p>
        <h2 className="mt-3 text-3xl font-semibold tracking-tight">{text.capabilitiesTitle}</h2>
        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {features.map(feature => (
            <Link key={feature.href} href={pageHref(feature.href, locale)} className="panel group p-6 transition hover:border-brand/40 hover:shadow-lg hover:shadow-brand/5">
              <h3 className="font-semibold">{feature.title[locale]}</h3>
              <p className="mt-2 text-sm leading-6 text-muted">{feature.text[locale]}</p>
              <span className="mt-4 inline-block text-sm font-medium text-brand opacity-0 transition group-hover:opacity-100">{text.readDocs}</span>
            </Link>
          ))}
        </div>
      </section>
      <section className="border-y border-line bg-surface">
        <div className="mx-auto grid max-w-[90rem] gap-8 px-5 py-16 md:grid-cols-2 lg:grid-cols-5">
          {sections.map(section => (
            <div key={section.id}>
              <h3 className="font-semibold">{titleOf(section.title, section.en, locale)}</h3>
              <p className="mt-2 text-sm leading-6 text-muted">{locale === 'en' ? section.descriptionEn : section.description}</p>
              <ul className="mt-4 space-y-1.5 text-sm">
                {section.groups.flatMap(group => group.pages).slice(0, 5).map(page => (
                  <li key={page.slug}><Link href={pageHref(page.slug, locale)} className="text-muted hover:text-brand">{titleOf(page.title, page.en, locale)}</Link></li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>
      <footer className="mx-auto flex max-w-[90rem] flex-wrap justify-between gap-4 px-5 py-10 text-xs text-muted">
        <span>{text.license}</span>
        <a href="https://github.com/devlive-community/grantforge" target="_blank" rel="noreferrer" className="hover:text-brand">github.com/devlive-community/grantforge</a>
      </footer>
    </main>
  )
}
