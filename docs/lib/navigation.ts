// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { stripLocale } from '@/lib/i18n'
import type { Locale } from '@/lib/i18n'

/** One page of the documentation, by its path below content/ without the .md extension. */
export interface NavPage {
  slug: string
  title: string
  en: string
}

/** One group of pages inside a part of the documentation. */
export interface NavGroup {
  title: string
  en: string
  pages: NavPage[]
}

/** A part of the documentation with its own sidebar. */
export interface NavSection {
  id: string
  title: string
  en: string
  description: string
  descriptionEn: string
  groups: NavGroup[]
}

/** Every page in reading order. The sidebar, the pager and the content check all follow this list. */
export const sections: NavSection[] = [
  {
    id: 'start',
    title: '快速开始',
    en: 'Quick start',
    description: '了解 GrantForge，安装、初始化并完成第一次授权。',
    descriptionEn: 'Understand GrantForge, install it, finish setup and grant your first role.',
    groups: [
      { title: '入门', en: 'Getting started', pages: [
        { slug: 'start/introduction', title: '产品介绍', en: 'Introduction' },
        { slug: 'start/quick-start', title: '五分钟上手', en: 'Set up in five minutes' },
        { slug: 'start/concepts', title: '核心概念', en: 'Core concepts' },
      ] },
      { title: '部署', en: 'Deployment', pages: [
        { slug: 'start/installation', title: '安装发行包', en: 'Install the release' },
        { slug: 'start/docker', title: 'Docker、Compose 与 Helm', en: 'Docker, Compose and Helm' },
        { slug: 'start/databases', title: '数据库', en: 'Databases' },
        { slug: 'start/configuration', title: '配置参考', en: 'Configuration reference' },
        { slug: 'start/upgrade', title: '升级与旧版本迁移', en: 'Upgrading and legacy migration' },
      ] },
    ],
  },
  {
    id: 'guide',
    title: '使用指南',
    en: 'User guide',
    description: '按控制台菜单讲解每项功能的用法。',
    descriptionEn: 'Every console feature, menu by menu.',
    groups: [
      { title: '账号', en: 'Account', pages: [
        { slug: 'guide/console', title: '控制台导览', en: 'Console tour' },
        { slug: 'guide/account', title: '登录、账号与两步验证', en: 'Sign-in, accounts and two-factor authentication' },
      ] },
      { title: '身份与组织', en: 'Identity & organization', pages: [
        { slug: 'guide/users', title: '用户', en: 'Users' },
        { slug: 'guide/organization', title: '部门、用户组与岗位', en: 'Departments, groups and positions' },
        { slug: 'guide/transfer', title: '批量导入导出', en: 'Bulk import and export' },
        { slug: 'guide/identity-sources', title: '身份源（LDAP 与 OIDC）', en: 'Identity sources (LDAP and OIDC)' },
      ] },
      { title: '访问控制', en: 'Access control', pages: [
        { slug: 'guide/roles', title: '角色与授权', en: 'Roles and grants' },
        { slug: 'guide/data-permissions', title: '数据权限', en: 'Data permissions' },
        { slug: 'guide/field-permissions', title: '字段权限', en: 'Field permissions' },
        { slug: 'guide/explain', title: '权限解释、模拟与审计', en: 'Explanations, simulation and audit' },
        { slug: 'guide/sod', title: '职责分离', en: 'Separation of duty' },
        { slug: 'guide/access-requests', title: '权限申请与审批', en: 'Access requests and approvals' },
        { slug: 'guide/access-reviews', title: '定期权限复核', en: 'Periodic access reviews' },
      ] },
      { title: '平台', en: 'Platform', pages: [
        { slug: 'guide/tenants', title: '租户', en: 'Tenants' },
        { slug: 'guide/catalog', title: '资源目录与 API 目录', en: 'Resource and API catalog' },
      ] },
    ],
  },
  {
    id: 'external',
    title: '插件与代理',
    en: 'Plug-ins and agents',
    description: '用插件管理 HDFS、Hive 等外部数据系统的权限，并让代理在目标系统内执行策略。',
    descriptionEn: 'Manage permissions for external data systems with plug-ins, and enforce policies with agents inside them.',
    groups: [
      { title: '管理与使用', en: 'Manage and use', pages: [
        { slug: 'guide/data-services', title: '数据服务、策略与代理', en: 'Data services, policies and agents' },
        { slug: 'guide/hdfs-agent', title: 'HDFS NameNode 代理', en: 'HDFS NameNode agent' },
      ] },
      { title: '开发', en: 'Development', pages: [
        { slug: 'architecture/plugins', title: '插件与服务类型', en: 'Plug-ins and service types' },
      ] },
    ],
  },
  {
    id: 'integration',
    title: '应用接入',
    en: 'Integrating applications',
    description: '让你的应用用 GrantForge 登录用户并判断权限。',
    descriptionEn: 'Sign users in through GrantForge and evaluate permissions in your application.',
    groups: [
      { title: '接入', en: 'Integration', pages: [
        { slug: 'integration/overview', title: '接入概览', en: 'Integration overview' },
        { slug: 'integration/oauth', title: 'OAuth 2.1 与 OpenID Connect', en: 'OAuth 2.1 and OpenID Connect' },
        { slug: 'integration/open-api', title: '权限查询开放 API', en: 'Permission open API' },
        { slug: 'integration/java', title: 'Java SDK（Spring Boot）', en: 'Java SDK (Spring Boot)' },
        { slug: 'integration/javascript', title: 'JavaScript SDK', en: 'JavaScript SDK' },
        { slug: 'integration/samples', title: '示例应用', en: 'Sample applications' },
      ] },
    ],
  },
  {
    id: 'architecture',
    title: '技术文档',
    en: 'Architecture',
    description: '架构、权限模型与安全设计，以及参与开发需要知道的一切。',
    descriptionEn: 'Architecture, the permission model and security design, plus everything contributors need.',
    groups: [
      { title: '设计', en: 'Design', pages: [
        { slug: 'architecture/overview', title: '架构总览', en: 'Architecture overview' },
        { slug: 'architecture/permission-model', title: '权限模型', en: 'Permission model' },
        { slug: 'architecture/multi-tenancy', title: '多租户与数据隔离', en: 'Multi-tenancy and data isolation' },
        { slug: 'architecture/security', title: '安全设计', en: 'Security design' },
        { slug: 'architecture/performance', title: '性能与基准', en: 'Performance and benchmarks' },
      ] },
      { title: '参考', en: 'Reference', pages: [
        { slug: 'architecture/api', title: 'REST API 参考', en: 'REST API reference' },
        { slug: 'architecture/errors', title: '错误码', en: 'Error codes' },
        { slug: 'architecture/development', title: '开发、测试与 CI', en: 'Development, testing and CI' },
      ] },
    ],
  },
  {
    id: 'changelog',
    title: '发布日志',
    en: 'Releases',
    description: '各版本的变化。',
    descriptionEn: 'What changed in every version.',
    groups: [
      { title: '版本', en: 'Versions', pages: [
        { slug: 'changelog/rebuild', title: '2026.0.0（重构版）', en: '2026.0.0 (rebuild)' },
        { slug: 'changelog/1.0.6', title: '1.0.6', en: '1.0.6' },
        { slug: 'changelog/1.0.5', title: '1.0.5', en: '1.0.5' },
        { slug: 'changelog/1.0.4', title: '1.0.4', en: '1.0.4' },
        { slug: 'changelog/1.0.3', title: '1.0.3', en: '1.0.3' },
        { slug: 'changelog/1.0.2', title: '1.0.2', en: '1.0.2' },
        { slug: 'changelog/1.0.1', title: '1.0.1', en: '1.0.1' },
        { slug: 'changelog/1.0.0', title: '1.0.0', en: '1.0.0' },
      ] },
    ],
  },
]

/** Every page in reading order. */
export const pages: NavPage[] = sections.flatMap(section => section.groups.flatMap(group => group.pages))

/** Returns the section a page belongs to; English slugs resolve to the same page as their Chinese twin. */
export function sectionOf(slug: string): NavSection | undefined {
  const key = stripLocale(slug)
  return sections.find(section => section.groups.some(group => group.pages.some(page => page.slug === key)))
}

/** Returns the pages before and after a page, for the pager below it. */
export function neighbours(slug: string): { previous?: NavPage; next?: NavPage } {
  const index = pages.findIndex(page => page.slug === stripLocale(slug))
  return index < 0 ? {} : { previous: pages[index - 1], next: pages[index + 1] }
}

/** The title of a page or a group in one locale. */
export function titleOf(title: string, en: string, locale: Locale): string {
  return locale === 'en' ? en : title
}
