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
  ru?: string
}

/** One group of pages inside a part of the documentation. */
export interface NavGroup {
  title: string
  en: string
  ru?: string
  pages: NavPage[]
}

/** A part of the documentation with its own sidebar. */
export interface NavSection {
  id: string
  title: string
  en: string
  ru?: string
  description: string
  descriptionEn: string
  descriptionRu?: string
  groups: NavGroup[]
}

/** A top-level part of the documentation: one tab in the header, with a sidebar of its own sections. */
export interface NavCategory {
  id: string
  title: string
  en: string
  ru?: string
  description: string
  descriptionEn: string
  descriptionRu?: string
  sections: NavSection[]
}

const start: NavSection = {
  id: 'start',
  title: '快速开始',
  en: 'Quick start',
  ru: 'Начало работы',
  description: '了解 GrantForge，五分钟跑起来并掌握核心概念。',
  descriptionEn: 'Understand GrantForge, run it in five minutes and learn the core concepts.',
  descriptionRu: 'Познакомьтесь с GrantForge, запустите его за пять минут и разберитесь с ключевыми понятиями.',
  groups: [
    { title: '入门', en: 'Getting started', ru: 'Первые шаги', pages: [
      { slug: 'start/introduction', title: '产品介绍', en: 'Introduction', ru: 'Введение' },
      { slug: 'start/quick-start', title: '五分钟上手', en: 'Set up in five minutes', ru: 'Быстрый старт за пять минут' },
      { slug: 'start/concepts', title: '核心概念', en: 'Core concepts', ru: 'Основные понятия' },
    ] },
  ],
}

const guide: NavSection = {
  id: 'guide',
  title: '使用指南',
  en: 'User guide',
  ru: 'Руководство пользователя',
  description: '按控制台菜单讲解每项功能的用法。',
  descriptionEn: 'Every console feature, menu by menu.',
  descriptionRu: 'Каждая функция консоли, меню за меню.',
  groups: [
    { title: '账号', en: 'Account', ru: 'Учётная запись', pages: [
      { slug: 'guide/console', title: '控制台导览', en: 'Console tour', ru: 'Обзор консоли' },
      { slug: 'guide/account', title: '登录、账号与两步验证', en: 'Sign-in, accounts and two-factor authentication', ru: 'Вход, учётная запись и двухфакторная проверка' },
    ] },
    { title: '身份与组织', en: 'Identity & organization', ru: 'Идентификация и организация', pages: [
      { slug: 'guide/users', title: '用户', en: 'Users', ru: 'Пользователи' },
      { slug: 'guide/organization', title: '部门、用户组与岗位', en: 'Departments, groups and positions', ru: 'Подразделения, группы и должности' },
      { slug: 'guide/transfer', title: '批量导入导出', en: 'Bulk import and export', ru: 'Массовый импорт и экспорт' },
      { slug: 'guide/identity-sources', title: '身份源（LDAP 与 OIDC）', en: 'Identity sources (LDAP and OIDC)', ru: 'Источники идентификации (LDAP и OIDC)' },
    ] },
    { title: '访问控制', en: 'Access control', ru: 'Управление доступом', pages: [
      { slug: 'guide/roles', title: '角色与授权', en: 'Roles and grants', ru: 'Роли и полномочия' },
      { slug: 'guide/data-permissions', title: '数据权限', en: 'Data permissions', ru: 'Права на данные' },
      { slug: 'guide/field-permissions', title: '字段权限', en: 'Field permissions', ru: 'Права на поля' },
      { slug: 'guide/explain', title: '权限解释、模拟与审计', en: 'Explanations, simulation and audit', ru: 'Объяснение, моделирование и аудит прав' },
    ] },
    { title: '治理', en: 'Governance', ru: 'Жизненный цикл доступа', pages: [
      { slug: 'guide/sod', title: '职责分离', en: 'Separation of duty', ru: 'Разделение обязанностей' },
      { slug: 'guide/access-requests', title: '权限申请与审批', en: 'Access requests and approvals', ru: 'Запросы доступа и согласования' },
      { slug: 'guide/access-reviews', title: '定期权限复核', en: 'Periodic access reviews', ru: 'Периодическая проверка прав доступа' },
    ] },
    { title: '平台', en: 'Platform', ru: 'Платформа', pages: [
      { slug: 'guide/tenants', title: '租户', en: 'Tenants', ru: 'Тенанты' },
      { slug: 'guide/catalog', title: '资源目录与 API 目录', en: 'Resource and API catalog', ru: 'Каталог ресурсов и каталог API' },
    ] },
  ],
}

const deploy: NavSection = {
  id: 'deploy',
  title: '部署与运维',
  en: 'Deployment',
  ru: 'Развёртывание и эксплуатация',
  description: '用发行包、容器或 Helm 部署，选择数据库并完成升级。',
  descriptionEn: 'Deploy from the release, containers or Helm, pick a database and upgrade.',
  descriptionRu: 'Установка из дистрибутива, контейнеров или Helm, выбор базы данных и обновление.',
  groups: [
    { title: '安装与运行', en: 'Install and run', ru: 'Установка и запуск', pages: [
      { slug: 'deploy/installation', title: '安装发行包', en: 'Install the release', ru: 'Установка дистрибутива' },
      { slug: 'deploy/docker', title: 'Docker、Compose 与 Helm', en: 'Docker, Compose and Helm', ru: 'Docker, Compose и Helm' },
      { slug: 'deploy/databases', title: '数据库', en: 'Databases', ru: 'Базы данных' },
    ] },
    { title: '升级', en: 'Upgrade', ru: 'Обновление', pages: [
      { slug: 'deploy/upgrade', title: '升级与旧版本迁移', en: 'Upgrading and legacy migration', ru: 'Обновление и миграция со старых версий' },
    ] },
  ],
}

const integration: NavSection = {
  id: 'integration',
  title: '应用接入',
  en: 'Integrating applications',
  ru: 'Интеграция приложений',
  description: '让你的应用用 GrantForge 登录用户并判断权限。',
  descriptionEn: 'Sign users in through GrantForge and evaluate permissions in your application.',
  descriptionRu: 'Вход пользователей через GrantForge и проверка прав в вашем приложении.',
  groups: [
    { title: '协议', en: 'Protocols', ru: 'Протоколы', pages: [
      { slug: 'integration/overview', title: '接入概览', en: 'Integration overview', ru: 'Обзор интеграции' },
      { slug: 'integration/oauth', title: 'OAuth 2.1 与 OpenID Connect', en: 'OAuth 2.1 and OpenID Connect', ru: 'OAuth 2.1 и OpenID Connect' },
      { slug: 'integration/open-api', title: '权限查询开放 API', en: 'Permission open API', ru: 'Открытый API запросов прав доступа' },
    ] },
    { title: 'SDK 与示例', en: 'SDKs and samples', ru: 'SDK и примеры', pages: [
      { slug: 'integration/java', title: 'Java SDK（Spring Boot）', en: 'Java SDK (Spring Boot)', ru: 'Java SDK (Spring Boot)' },
      { slug: 'integration/javascript', title: 'JavaScript SDK', en: 'JavaScript SDK', ru: 'JavaScript SDK' },
      { slug: 'integration/samples', title: '示例应用', en: 'Sample applications', ru: 'Примеры приложений' },
    ] },
  ],
}

const external: NavSection = {
  id: 'external',
  title: '外部系统权限',
  en: 'External systems',
  ru: 'Права на внешние системы',
  description: '管理 HDFS、Hive 等外部数据系统的权限，并让代理在目标系统内执行策略。',
  descriptionEn: 'Manage permissions for external data systems and enforce policies with agents inside them.',
  descriptionRu: 'Права на внешние системы данных и принуждение политикам агентами внутри них.',
  groups: [
    { title: '管理与部署', en: 'Manage and deploy', ru: 'Управление и развёртывание', pages: [
      { slug: 'external/data-services', title: '数据服务、策略与代理', en: 'Data services, policies and agents', ru: 'Сервисы данных, политики и агенты' },
      { slug: 'external/hdfs-agent', title: 'HDFS NameNode 代理', en: 'HDFS NameNode agent', ru: 'Агент HDFS NameNode' },
    ] },
  ],
}

const architecture: NavSection = {
  id: 'architecture',
  title: '架构与安全',
  en: 'Architecture',
  ru: 'Архитектура и безопасность',
  description: '模块划分、权限模型、多租户隔离、安全设计与性能基准。',
  descriptionEn: 'Modules, the permission model, tenant isolation, security design and benchmarks.',
  descriptionRu: 'Модули, модель прав, изоляция тенантов, проектирование безопасности и бенчмарки.',
  groups: [
    { title: '设计', en: 'Design', ru: 'Проектирование', pages: [
      { slug: 'architecture/overview', title: '架构总览', en: 'Architecture overview', ru: 'Обзор архитектуры' },
      { slug: 'architecture/permission-model', title: '权限模型', en: 'Permission model', ru: 'Модель прав доступа' },
      { slug: 'architecture/multi-tenancy', title: '多租户与数据隔离', en: 'Multi-tenancy and data isolation', ru: 'Мультитенантность и изоляция данных' },
      { slug: 'architecture/security', title: '安全设计', en: 'Security design', ru: 'Проектирование безопасности' },
      { slug: 'architecture/performance', title: '性能与基准', en: 'Performance and benchmarks', ru: 'Производительность и бенчмарки' },
    ] },
  ],
}

const reference: NavSection = {
  id: 'reference',
  title: '参考',
  en: 'Reference',
  ru: 'Справочник',
  description: '配置项、REST 接口与错误码。',
  descriptionEn: 'Configuration, REST endpoints and error codes.',
  descriptionRu: 'Конфигурация, REST-интерфейсы и коды ошибок.',
  groups: [
    { title: '配置', en: 'Configuration', ru: 'Конфигурация', pages: [
      { slug: 'reference/configuration', title: '配置参考', en: 'Configuration reference', ru: 'Справочник конфигурации' },
    ] },
    { title: '契约', en: 'Contracts', ru: 'Контракты', pages: [
      { slug: 'reference/api', title: 'REST API 参考', en: 'REST API reference', ru: 'Справочник REST API' },
      { slug: 'reference/errors', title: '错误码', en: 'Error codes', ru: 'Коды ошибок' },
    ] },
  ],
}

const develop: NavSection = {
  id: 'develop',
  title: '开发者',
  en: 'Developers',
  ru: 'Разработчикам',
  description: '在本地构建、测试与提交代码，并开发服务类型插件。',
  descriptionEn: 'Build, test and contribute locally, and develop service type plug-ins.',
  descriptionRu: 'Локальная сборка, тесты и контрибьюшены, а также разработка плагинов типов сервисов.',
  groups: [
    { title: '服务端', en: 'Server', ru: 'Сервер', pages: [
      { slug: 'develop/development', title: '开发、测试与 CI', en: 'Development, testing and CI', ru: 'Разработка, тестирование и CI' },
    ] },
    { title: '插件', en: 'Plug-ins', ru: 'Плагины', pages: [
      { slug: 'develop/plugins', title: '插件与服务类型', en: 'Plug-ins and service types', ru: 'Плагины и типы сервисов' },
    ] },
  ],
}

const changelog: NavSection = {
  id: 'changelog',
  title: '发布日志',
  en: 'Releases',
  ru: 'Журнал выпусков',
  description: '各版本的变化。',
  descriptionEn: 'What changed in every version.',
  descriptionRu: 'Что изменилось в каждом выпуске.',
  groups: [
    { title: '版本', en: 'Versions', ru: 'Версии', pages: [
      { slug: 'changelog/rebuild', title: '2026.0.0（重构版）', en: '2026.0.0 (rebuild)', ru: '2026.0.0 (переработанная версия)' },
      { slug: 'changelog/1.0.6', title: '1.0.6', en: '1.0.6', ru: '1.0.6' },
      { slug: 'changelog/1.0.5', title: '1.0.5', en: '1.0.5', ru: '1.0.5' },
      { slug: 'changelog/1.0.4', title: '1.0.4', en: '1.0.4', ru: '1.0.4' },
      { slug: 'changelog/1.0.3', title: '1.0.3', en: '1.0.3', ru: '1.0.3' },
      { slug: 'changelog/1.0.2', title: '1.0.2', en: '1.0.2', ru: '1.0.2' },
      { slug: 'changelog/1.0.1', title: '1.0.1', en: '1.0.1', ru: '1.0.1' },
      { slug: 'changelog/1.0.0', title: '1.0.0', en: '1.0.0', ru: '1.0.0' },
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
    ru: 'Пользовательская документация',
    description: '从五分钟上手到控制台的每一项功能，再到部署与升级。',
    descriptionEn: 'From a five-minute start to every console feature, then deployment and upgrades.',
    descriptionRu: 'От быстрого старта до каждой функции консоли, затем развёртывание и обновление.',
    sections: [start, guide, deploy],
  },
  {
    id: 'developer',
    title: '开发文档',
    en: 'Developer guide',
    ru: 'Документация для разработчиков',
    description: '接入应用、扩展外部数据系统、理解架构与权限模型，并参与开发。',
    descriptionEn: 'Integrate applications, extend to external data systems, understand the architecture and contribute.',
    descriptionRu: 'Интеграция приложений, внешние системы данных, архитектура и модель прав, участие в разработке.',
    sections: [integration, external, architecture, reference, develop],
  },
  {
    id: 'releases',
    title: '发布日志',
    en: 'Releases',
    ru: 'Журнал выпусков',
    description: '各版本的变化。',
    descriptionEn: 'What changed in every version.',
    descriptionRu: 'Что изменилось в каждом выпуске.',
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
/** The title of a page, a group or a section in one locale; Russian falls back to English until it has one. */
export function titleOf(title: string, en: string, locale: Locale, ru?: string): string {
  if (locale === 'zh') return title
  if (locale === 'ru') return ru ?? en
  return en
}
