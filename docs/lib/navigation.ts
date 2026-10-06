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

/** A top-level part of the documentation: one tab in the header, with a sidebar of its own sections. */
export interface NavCategory {
  id: string
  title: string
  en: string
  description: string
  descriptionEn: string
  sections: NavSection[]
}

const start: NavSection = {
  id: 'start',
  title: '快速开始',
  en: 'Quick start',
  description: '了解 GrantForge，五分钟跑起来并掌握核心概念。',
  descriptionEn: 'Understand GrantForge, run it in five minutes and learn the core concepts.',
  groups: [
    { title: '入门', en: 'Getting started', pages: [
      { slug: 'start/introduction', title: '产品介绍', en: 'Introduction' },
      { slug: 'start/quick-start', title: '五分钟上手', en: 'Set up in five minutes' },
      { slug: 'start/concepts', title: '核心概念', en: 'Core concepts' },
    ] },
  ],
}

const guide: NavSection = {
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
    ] },
    { title: '治理', en: 'Governance', pages: [
      { slug: 'guide/sod', title: '职责分离', en: 'Separation of duty' },
      { slug: 'guide/access-requests', title: '权限申请与审批', en: 'Access requests and approvals' },
      { slug: 'guide/access-reviews', title: '定期权限复核', en: 'Periodic access reviews' },
    ] },
    { title: '平台', en: 'Platform', pages: [
      { slug: 'guide/tenants', title: '租户', en: 'Tenants' },
      { slug: 'guide/catalog', title: '资源目录与 API 目录', en: 'Resource and API catalog' },
    ] },
  ],
}

const deploy: NavSection = {
  id: 'deploy',
  title: '部署与运维',
  en: 'Deployment',
  description: '用发行包、容器或 Helm 部署，选择数据库并完成升级。',
  descriptionEn: 'Deploy from the release, containers or Helm, pick a database and upgrade.',
  groups: [
    { title: '安装与运行', en: 'Install and run', pages: [
      { slug: 'deploy/installation', title: '安装发行包', en: 'Install the release' },
      { slug: 'deploy/docker', title: 'Docker、Compose 与 Helm', en: 'Docker, Compose and Helm' },
      { slug: 'deploy/databases', title: '数据库', en: 'Databases' },
    ] },
    { title: '升级', en: 'Upgrade', pages: [
      { slug: 'deploy/upgrade', title: '升级与旧版本迁移', en: 'Upgrading and legacy migration' },
    ] },
  ],
}

const integration: NavSection = {
  id: 'integration',
  title: '应用接入',
  en: 'Integrating applications',
  description: '让你的应用用 GrantForge 登录用户并判断权限。',
  descriptionEn: 'Sign users in through GrantForge and evaluate permissions in your application.',
  groups: [
    { title: '协议', en: 'Protocols', pages: [
      { slug: 'integration/overview', title: '接入概览', en: 'Integration overview' },
      { slug: 'integration/oauth', title: 'OAuth 2.1 与 OpenID Connect', en: 'OAuth 2.1 and OpenID Connect' },
      { slug: 'integration/open-api', title: '权限查询开放 API', en: 'Permission open API' },
    ] },
    { title: 'SDK 与示例', en: 'SDKs and samples', pages: [
      { slug: 'integration/java', title: 'Java SDK（Spring Boot）', en: 'Java SDK (Spring Boot)' },
      { slug: 'integration/javascript', title: 'JavaScript SDK', en: 'JavaScript SDK' },
      { slug: 'integration/samples', title: '示例应用', en: 'Sample applications' },
    ] },
  ],
}

const external: NavSection = {
  id: 'external',
  title: '外部系统权限',
  en: 'External systems',
  description: '管理 HDFS、Hive 等外部数据系统的权限，并让代理在目标系统内执行策略。',
  descriptionEn: 'Manage permissions for external data systems and enforce policies with agents inside them.',
  groups: [
    { title: '管理与部署', en: 'Manage and deploy', pages: [
      { slug: 'external/data-services', title: '数据服务、策略与代理', en: 'Data services, policies and agents' },
      { slug: 'external/hdfs-agent', title: 'HDFS NameNode 代理', en: 'HDFS NameNode agent' },
    ] },
  ],
}

const architecture: NavSection = {
  id: 'architecture',
  title: '架构与安全',
  en: 'Architecture',
  description: '模块划分、权限模型、多租户隔离、安全设计与性能基准。',
  descriptionEn: 'Modules, the permission model, tenant isolation, security design and benchmarks.',
  groups: [
    { title: '设计', en: 'Design', pages: [
      { slug: 'architecture/overview', title: '架构总览', en: 'Architecture overview' },
      { slug: 'architecture/permission-model', title: '权限模型', en: 'Permission model' },
      { slug: 'architecture/multi-tenancy', title: '多租户与数据隔离', en: 'Multi-tenancy and data isolation' },
      { slug: 'architecture/security', title: '安全设计', en: 'Security design' },
      { slug: 'architecture/performance', title: '性能与基准', en: 'Performance and benchmarks' },
    ] },
  ],
}

const reference: NavSection = {
  id: 'reference',
  title: '参考',
  en: 'Reference',
  description: '配置项、REST 接口与错误码。',
  descriptionEn: 'Configuration, REST endpoints and error codes.',
  groups: [
    { title: '配置', en: 'Configuration', pages: [
      { slug: 'reference/configuration', title: '配置参考', en: 'Configuration reference' },
    ] },
    { title: '契约', en: 'Contracts', pages: [
      { slug: 'reference/api', title: 'REST API 参考', en: 'REST API reference' },
      { slug: 'reference/errors', title: '错误码', en: 'Error codes' },
    ] },
  ],
}

const develop: NavSection = {
  id: 'develop',
  title: '开发者',
  en: 'Developers',
  description: '在本地构建、测试与提交代码，并开发服务类型插件。',
  descriptionEn: 'Build, test and contribute locally, and develop service type plug-ins.',
  groups: [
    { title: '服务端', en: 'Server', pages: [
      { slug: 'develop/development', title: '开发、测试与 CI', en: 'Development, testing and CI' },
    ] },
    { title: '插件', en: 'Plug-ins', pages: [
      { slug: 'develop/plugins', title: '插件与服务类型', en: 'Plug-ins and service types' },
    ] },
  ],
}

const changelog: NavSection = {
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
}

/**
 * The three parts of the documentation, one tab each in the header. Every section lives in exactly one
 * category, and the sidebar of a page shows all sections of the category it belongs to.
 */
export const categories: NavCategory[] = [
  {
    id: 'user',
    title: '用户文档',
    en: 'User guide',
    description: '从五分钟上手到控制台的每一项功能，再到部署与升级。',
    descriptionEn: 'From a five-minute start to every console feature, then deployment and upgrades.',
    sections: [start, guide, deploy],
  },
  {
    id: 'developer',
    title: '开发文档',
    en: 'Developer guide',
    description: '接入应用、扩展外部数据系统、理解架构与权限模型，并参与开发。',
    descriptionEn: 'Integrate applications, extend to external data systems, understand the architecture and contribute.',
    sections: [integration, external, architecture, reference, develop],
  },
  {
    id: 'releases',
    title: '发布日志',
    en: 'Releases',
    description: '各版本的变化。',
    descriptionEn: 'What changed in every version.',
    sections: [changelog],
  },
]

/** Every section in reading order: the sidebar, the pager and the content check all follow this list. */
export const sections: NavSection[] = categories.flatMap(category => category.sections)

/** Returns the category a page belongs to. */
export function categoryOf(slug: string): NavCategory | undefined {
  const section = sectionOf(slug)
  if (!section) return undefined
  return categories.find(category => category.sections.includes(section))
}

/** Every page in reading order. */
export const pages: NavPage[] = sections.flatMap(section => section.groups.flatMap(group => group.pages))

/** Returns the section a page belongs to; English slugs resolve to the same page as their Chinese twin. */
export function sectionOf(slug: string): NavSection | undefined {
  const key = stripLocale(slug).replace(/^\/+|\/+$/g, '')
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
