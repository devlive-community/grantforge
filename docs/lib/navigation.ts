// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/** One page of the documentation, by its path below content/ without the .md extension. */
export interface NavPage {
  slug: string
  title: string
}

/** A part of the documentation with its own sidebar. */
export interface NavSection {
  id: string
  title: string
  description: string
  groups: { title: string; pages: NavPage[] }[]
}

/** Every page in reading order. The sidebar, the pager and the content check all follow this list. */
export const sections: NavSection[] = [
  {
    id: 'start',
    title: '快速开始',
    description: '了解 GrantForge，安装、初始化并完成第一次授权。',
    groups: [
      { title: '入门', pages: [
        { slug: 'start/introduction', title: '产品介绍' },
        { slug: 'start/quick-start', title: '五分钟上手' },
        { slug: 'start/concepts', title: '核心概念' },
      ] },
      { title: '部署', pages: [
        { slug: 'start/installation', title: '安装发行包' },
        { slug: 'start/docker', title: 'Docker、Compose 与 Helm' },
        { slug: 'start/databases', title: '数据库' },
        { slug: 'start/configuration', title: '配置参考' },
        { slug: 'start/upgrade', title: '升级与旧版本迁移' },
      ] },
    ],
  },
  {
    id: 'guide',
    title: '使用指南',
    description: '按控制台菜单讲解每项功能的用法。',
    groups: [
      { title: '账号', pages: [
        { slug: 'guide/console', title: '控制台导览' },
        { slug: 'guide/account', title: '登录、账号与两步验证' },
      ] },
      { title: '身份与组织', pages: [
        { slug: 'guide/users', title: '用户' },
        { slug: 'guide/organization', title: '部门、用户组与岗位' },
        { slug: 'guide/transfer', title: '批量导入导出' },
        { slug: 'guide/identity-sources', title: '身份源（LDAP 与 OIDC）' },
      ] },
      { title: '访问控制', pages: [
        { slug: 'guide/roles', title: '角色与授权' },
        { slug: 'guide/data-permissions', title: '数据权限' },
        { slug: 'guide/field-permissions', title: '字段权限' },
        { slug: 'guide/explain', title: '权限解释、模拟与审计' },
        { slug: 'guide/sod', title: '职责分离' },
        { slug: 'guide/access-requests', title: '权限申请与审批' },
        { slug: 'guide/access-reviews', title: '定期权限复核' },
      ] },
      { title: '平台', pages: [
        { slug: 'guide/tenants', title: '租户' },
        { slug: 'guide/catalog', title: '资源目录与 API 目录' },
        { slug: 'guide/data-services', title: '数据服务、策略与代理' },
        { slug: 'guide/hdfs-agent', title: 'HDFS NameNode 代理' },
      ] },
    ],
  },
  {
    id: 'integration',
    title: '应用接入',
    description: '让你的应用用 GrantForge 登录用户并判断权限。',
    groups: [
      { title: '接入', pages: [
        { slug: 'integration/overview', title: '接入概览' },
        { slug: 'integration/oauth', title: 'OAuth 2.1 与 OpenID Connect' },
        { slug: 'integration/open-api', title: '权限查询开放 API' },
        { slug: 'integration/java', title: 'Java SDK（Spring Boot）' },
        { slug: 'integration/javascript', title: 'JavaScript SDK' },
        { slug: 'integration/samples', title: '示例应用' },
      ] },
    ],
  },
  {
    id: 'architecture',
    title: '技术文档',
    description: '架构、权限模型与安全设计，以及参与开发需要知道的一切。',
    groups: [
      { title: '设计', pages: [
        { slug: 'architecture/overview', title: '架构总览' },
        { slug: 'architecture/permission-model', title: '权限模型' },
        { slug: 'architecture/multi-tenancy', title: '多租户与数据隔离' },
        { slug: 'architecture/security', title: '安全设计' },
        { slug: 'architecture/plugins', title: '插件与服务类型' },
        { slug: 'architecture/performance', title: '性能与基准' },
      ] },
      { title: '参考', pages: [
        { slug: 'architecture/api', title: 'REST API 参考' },
        { slug: 'architecture/errors', title: '错误码' },
        { slug: 'architecture/development', title: '开发、测试与 CI' },
      ] },
    ],
  },
  {
    id: 'changelog',
    title: '发布日志',
    description: '各版本的变化。',
    groups: [
      { title: '版本', pages: [
        { slug: 'changelog/rebuild', title: '2026.0.0（重构版）' },
        { slug: 'changelog/1.0.6', title: '1.0.6' },
        { slug: 'changelog/1.0.5', title: '1.0.5' },
        { slug: 'changelog/1.0.4', title: '1.0.4' },
        { slug: 'changelog/1.0.3', title: '1.0.3' },
        { slug: 'changelog/1.0.2', title: '1.0.2' },
        { slug: 'changelog/1.0.1', title: '1.0.1' },
        { slug: 'changelog/1.0.0', title: '1.0.0' },
      ] },
    ],
  },
]

/** Every page in reading order. */
export const pages: NavPage[] = sections.flatMap(section => section.groups.flatMap(group => group.pages))

/** Returns the section a page belongs to. */
export function sectionOf(slug: string): NavSection | undefined {
  return sections.find(section => section.groups.some(group => group.pages.some(page => page.slug === slug)))
}

/** Returns the pages before and after a page, for the pager below it. */
export function neighbours(slug: string): { previous?: NavPage; next?: NavPage } {
  const index = pages.findIndex(page => page.slug === slug)
  return index < 0 ? {} : { previous: pages[index - 1], next: pages[index + 1] }
}
