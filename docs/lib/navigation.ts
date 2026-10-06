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
  tw?: string
}

/** One group of pages inside a part of the documentation. */
export interface NavGroup {
  title: string
  en: string
  ru?: string
  tw?: string
  pages: NavPage[]
}

/** A part of the documentation with its own sidebar. */
export interface NavSection {
  id: string
  title: string
  en: string
  ru?: string
  tw?: string
  description: string
  descriptionEn: string
  descriptionRu?: string
  descriptionTw?: string
  groups: NavGroup[]
}

/** A top-level part of the documentation: one tab in the header, with a sidebar of its own sections. */
export interface NavCategory {
  id: string
  title: string
  en: string
  ru?: string
  tw?: string
  description: string
  descriptionEn: string
  descriptionRu?: string
  descriptionTw?: string
  sections: NavSection[]
}

const start: NavSection = {
  id: 'start',
  title: '快速开始',
  en: 'Quick start',
  ru: 'Начало работы',
  tw: '快速開始',
  description: '了解 GrantForge，五分钟跑起来并掌握核心概念。',
  descriptionEn: 'Understand GrantForge, run it in five minutes and learn the core concepts.',
  descriptionRu: 'Познакомьтесь с GrantForge, запустите его за пять минут и разберитесь с ключевыми понятиями.',
  descriptionTw: '了解 GrantForge，五分鐘跑起來並掌握核心概念。',
  groups: [
    { title: '入门', en: 'Getting started', ru: 'Первые шаги', tw: '入門', pages: [
      { slug: 'start/introduction', title: '产品介绍', en: 'Introduction', ru: 'Введение', tw: '產品介紹' },
      { slug: 'start/quick-start', title: '五分钟上手', en: 'Set up in five minutes', ru: 'Быстрый старт за пять минут', tw: '五分鐘上手' },
      { slug: 'start/concepts', title: '核心概念', en: 'Core concepts', ru: 'Основные понятия', tw: '核心概念' },
    ] },
  ],
}

const guide: NavSection = {
  id: 'guide',
  title: '使用指南',
  en: 'User guide',
  ru: 'Руководство пользователя',
  tw: '使用指南',
  description: '按控制台菜单讲解每项功能的用法。',
  descriptionEn: 'Every console feature, menu by menu.',
  descriptionRu: 'Каждая функция консоли, меню за меню.',
  descriptionTw: '按主控台選單講解每項功能的用法。',
  groups: [
    { title: '账号', en: 'Account', ru: 'Учётная запись', tw: '帳號', pages: [
      { slug: 'guide/console', title: '控制台导览', en: 'Console tour', ru: 'Обзор консоли', tw: '主控台導覽' },
      { slug: 'guide/account', title: '登录、账号与两步验证', en: 'Sign-in, accounts and two-factor authentication', ru: 'Вход, учётная запись и двухфакторная проверка', tw: '登入、帳號與兩步驟驗證' },
    ] },
    { title: '身份与组织', en: 'Identity & organization', ru: 'Идентификация и организация', tw: '身分與組織', pages: [
      { slug: 'guide/users', title: '用户', en: 'Users', ru: 'Пользователи', tw: '使用者' },
      { slug: 'guide/organization', title: '部门、用户组与岗位', en: 'Departments, groups and positions', ru: 'Подразделения, группы и должности', tw: '部門、使用者群組與職位' },
      { slug: 'guide/transfer', title: '批量导入导出', en: 'Bulk import and export', ru: 'Массовый импорт и экспорт', tw: '批次匯入匯出' },
      { slug: 'guide/identity-sources', title: '身份源（LDAP 与 OIDC）', en: 'Identity sources (LDAP and OIDC)', ru: 'Источники идентификации (LDAP и OIDC)', tw: '身分來源（LDAP 與 OIDC）' },
    ] },
    { title: '访问控制', en: 'Access control', ru: 'Управление доступом', tw: '存取控制', pages: [
      { slug: 'guide/roles', title: '角色与授权', en: 'Roles and grants', ru: 'Роли и полномочия', tw: '角色與授權' },
      { slug: 'guide/data-permissions', title: '数据权限', en: 'Data permissions', ru: 'Права на данные', tw: '資料權限' },
      { slug: 'guide/field-permissions', title: '字段权限', en: 'Field permissions', ru: 'Права на поля', tw: '欄位權限' },
      { slug: 'guide/explain', title: '权限解释、模拟与审计', en: 'Explanations, simulation and audit', ru: 'Объяснение, моделирование и аудит прав', tw: '權限解釋、模擬與稽核' },
    ] },
    { title: '治理', en: 'Governance', ru: 'Жизненный цикл доступа', tw: '治理', pages: [
      { slug: 'guide/sod', title: '职责分离', en: 'Separation of duty', ru: 'Разделение обязанностей', tw: '職責分離' },
      { slug: 'guide/access-requests', title: '权限申请与审批', en: 'Access requests and approvals', ru: 'Запросы доступа и согласования', tw: '權限申請與審批' },
      { slug: 'guide/access-reviews', title: '定期权限复核', en: 'Periodic access reviews', ru: 'Периодическая проверка прав доступа', tw: '定期權限覆核' },
    ] },
    { title: '平台', en: 'Platform', ru: 'Платформа', tw: '平台', pages: [
      { slug: 'guide/tenants', title: '租户', en: 'Tenants', ru: 'Тенанты', tw: '租戶' },
      { slug: 'guide/catalog', title: '资源目录与 API 目录', en: 'Resource and API catalog', ru: 'Каталог ресурсов и каталог API', tw: '資源目錄與 API 目錄' },
    ] },
  ],
}

const deploy: NavSection = {
  id: 'deploy',
  title: '部署与运维',
  en: 'Deployment',
  ru: 'Развёртывание и эксплуатация',
  tw: '部署與維運',
  description: '用发行包、容器或 Helm 部署，选择数据库并完成升级。',
  descriptionEn: 'Deploy from the release, containers or Helm, pick a database and upgrade.',
  descriptionRu: 'Установка из дистрибутива, контейнеров или Helm, выбор базы данных и обновление.',
  descriptionTw: '用發行包、容器或 Helm 部署，選擇資料庫並完成升級。',
  groups: [
    { title: '安装与运行', en: 'Install and run', ru: 'Установка и запуск', tw: '安裝與執行', pages: [
      { slug: 'deploy/installation', title: '安装发行包', en: 'Install the release', ru: 'Установка дистрибутива', tw: '安裝發行包' },
      { slug: 'deploy/docker', title: 'Docker、Compose 与 Helm', en: 'Docker, Compose and Helm', ru: 'Docker, Compose и Helm', tw: 'Docker、Compose 與 Helm' },
      { slug: 'deploy/databases', title: '数据库', en: 'Databases', ru: 'Базы данных', tw: '資料庫' },
    ] },
    { title: '升级', en: 'Upgrade', ru: 'Обновление', tw: '升級', pages: [
      { slug: 'deploy/upgrade', title: '升级与旧版本迁移', en: 'Upgrading and legacy migration', ru: 'Обновление и миграция со старых версий', tw: '升級與舊版本遷移' },
    ] },
  ],
}

const integration: NavSection = {
  id: 'integration',
  title: '应用接入',
  en: 'Integrating applications',
  ru: 'Интеграция приложений',
  tw: '應用程式接入',
  description: '让你的应用用 GrantForge 登录用户并判断权限。',
  descriptionEn: 'Sign users in through GrantForge and evaluate permissions in your application.',
  descriptionRu: 'Вход пользователей через GrantForge и проверка прав в вашем приложении.',
  descriptionTw: '讓你的應用程式用 GrantForge 登入使用者並判斷權限。',
  groups: [
    { title: '协议', en: 'Protocols', ru: 'Протоколы', tw: '協定', pages: [
      { slug: 'integration/overview', title: '接入概览', en: 'Integration overview', ru: 'Обзор интеграции', tw: '接入概覽' },
      { slug: 'integration/oauth', title: 'OAuth 2.1 与 OpenID Connect', en: 'OAuth 2.1 and OpenID Connect', ru: 'OAuth 2.1 и OpenID Connect', tw: 'OAuth 2.1 與 OpenID Connect' },
      { slug: 'integration/open-api', title: '权限查询开放 API', en: 'Permission open API', ru: 'Открытый API запросов прав доступа', tw: '權限查詢開放 API' },
    ] },
    { title: 'SDK 与示例', en: 'SDKs and samples', ru: 'SDK и примеры', tw: 'SDK 與範例', pages: [
      { slug: 'integration/java', title: 'Java SDK（Spring Boot）', en: 'Java SDK (Spring Boot)', ru: 'Java SDK (Spring Boot)', tw: 'Java SDK（Spring Boot）' },
      { slug: 'integration/javascript', title: 'JavaScript SDK', en: 'JavaScript SDK', ru: 'JavaScript SDK', tw: 'JavaScript SDK' },
      { slug: 'integration/samples', title: '示例应用', en: 'Sample applications', ru: 'Примеры приложений', tw: '範例應用程式' },
    ] },
  ],
}

const external: NavSection = {
  id: 'external',
  title: '外部系统权限',
  en: 'External systems',
  ru: 'Права на внешние системы',
  tw: '外部系統權限',
  description: '管理 HDFS、Hive 等外部数据系统的权限，并让代理在目标系统内执行策略。',
  descriptionEn: 'Manage permissions for external data systems and enforce policies with agents inside them.',
  descriptionRu: 'Права на внешние системы данных и принуждение политикам агентами внутри них.',
  descriptionTw: '管理 HDFS、Hive 等外部資料系統的權限，並讓代理在目標系統內執行策略。',
  groups: [
    { title: '管理与部署', en: 'Manage and deploy', ru: 'Управление и развёртывание', tw: '管理與部署', pages: [
      { slug: 'external/data-services', title: '数据服务、策略与代理', en: 'Data services, policies and agents', ru: 'Сервисы данных, политики и агенты', tw: '資料服務、策略與代理' },
      { slug: 'external/hdfs-agent', title: 'HDFS NameNode 代理', en: 'HDFS NameNode agent', ru: 'Агент HDFS NameNode', tw: 'HDFS NameNode 代理' },
    ] },
  ],
}

const architecture: NavSection = {
  id: 'architecture',
  title: '架构与安全',
  en: 'Architecture',
  ru: 'Архитектура и безопасность',
  tw: '架構與安全',
  description: '模块划分、权限模型、多租户隔离、安全设计与性能基准。',
  descriptionEn: 'Modules, the permission model, tenant isolation, security design and benchmarks.',
  descriptionRu: 'Модули, модель прав, изоляция тенантов, проектирование безопасности и бенчмарки.',
  descriptionTw: '模組劃分、權限模型、多租戶隔離、安全設計與效能基準。',
  groups: [
    { title: '设计', en: 'Design', ru: 'Проектирование', tw: '設計', pages: [
      { slug: 'architecture/overview', title: '架构总览', en: 'Architecture overview', ru: 'Обзор архитектуры', tw: '架構總覽' },
      { slug: 'architecture/permission-model', title: '权限模型', en: 'Permission model', ru: 'Модель прав доступа', tw: '權限模型' },
      { slug: 'architecture/multi-tenancy', title: '多租户与数据隔离', en: 'Multi-tenancy and data isolation', ru: 'Мультитенантность и изоляция данных', tw: '多租戶與資料隔離' },
      { slug: 'architecture/security', title: '安全设计', en: 'Security design', ru: 'Проектирование безопасности', tw: '安全設計' },
      { slug: 'architecture/performance', title: '性能与基准', en: 'Performance and benchmarks', ru: 'Производительность и бенчмарки', tw: '效能與基準' },
    ] },
  ],
}

const reference: NavSection = {
  id: 'reference',
  title: '参考',
  en: 'Reference',
  ru: 'Справочник',
  tw: '參考',
  description: '配置项、REST 接口与错误码。',
  descriptionEn: 'Configuration, REST endpoints and error codes.',
  descriptionRu: 'Конфигурация, REST-интерфейсы и коды ошибок.',
  descriptionTw: '設定項、REST 介面與錯誤碼。',
  groups: [
    { title: '配置', en: 'Configuration', ru: 'Конфигурация', tw: '設定', pages: [
      { slug: 'reference/configuration', title: '配置参考', en: 'Configuration reference', ru: 'Справочник конфигурации', tw: '設定參考' },
    ] },
    { title: '契约', en: 'Contracts', ru: 'Контракты', tw: '契約', pages: [
      { slug: 'reference/api', title: 'REST API 参考', en: 'REST API reference', ru: 'Справочник REST API', tw: 'REST API 參考' },
      { slug: 'reference/errors', title: '错误码', en: 'Error codes', ru: 'Коды ошибок', tw: '錯誤碼' },
    ] },
  ],
}

const develop: NavSection = {
  id: 'develop',
  title: '开发者',
  en: 'Developers',
  ru: 'Разработчикам',
  tw: '開發者',
  description: '在本地构建、测试与提交代码，并开发服务类型插件。',
  descriptionEn: 'Build, test and contribute locally, and develop service type plug-ins.',
  descriptionRu: 'Локальная сборка, тесты и контрибьюшены, а также разработка плагинов типов сервисов.',
  descriptionTw: '在本機建置、測試與提交程式碼，並開發服務類型外掛。',
  groups: [
    { title: '服务端', en: 'Server', ru: 'Сервер', tw: '伺服端', pages: [
      { slug: 'develop/development', title: '开发、测试与 CI', en: 'Development, testing and CI', ru: 'Разработка, тестирование и CI', tw: '開發、測試與 CI' },
    ] },
    { title: '插件', en: 'Plug-ins', ru: 'Плагины', tw: '外掛', pages: [
      { slug: 'develop/plugins', title: '插件与服务类型', en: 'Plug-ins and service types', ru: 'Плагины и типы сервисов', tw: '外掛與服務類型' },
    ] },
  ],
}

const changelog: NavSection = {
  id: 'changelog',
  title: '发布日志',
  en: 'Releases',
  ru: 'Журнал выпусков',
  tw: '發布日誌',
  description: '各版本的变化。',
  descriptionEn: 'What changed in every version.',
  descriptionRu: 'Что изменилось в каждом выпуске.',
  descriptionTw: '各版本的變化。',
  groups: [
    { title: '版本', en: 'Versions', ru: 'Версии', tw: '版本', pages: [
      { slug: 'changelog/rebuild', title: '2026.0.0（重构版）', en: '2026.0.0 (rebuild)', ru: '2026.0.0 (переработанная версия)', tw: '2026.0.0（重構版）' },
      { slug: 'changelog/1.0.6', title: '1.0.6', en: '1.0.6', ru: '1.0.6', tw: '1.0.6' },
      { slug: 'changelog/1.0.5', title: '1.0.5', en: '1.0.5', ru: '1.0.5', tw: '1.0.5' },
      { slug: 'changelog/1.0.4', title: '1.0.4', en: '1.0.4', ru: '1.0.4', tw: '1.0.4' },
      { slug: 'changelog/1.0.3', title: '1.0.3', en: '1.0.3', ru: '1.0.3', tw: '1.0.3' },
      { slug: 'changelog/1.0.2', title: '1.0.2', en: '1.0.2', ru: '1.0.2', tw: '1.0.2' },
      { slug: 'changelog/1.0.1', title: '1.0.1', en: '1.0.1', ru: '1.0.1', tw: '1.0.1' },
      { slug: 'changelog/1.0.0', title: '1.0.0', en: '1.0.0', ru: '1.0.0', tw: '1.0.0' },
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
    tw: '使用者文件',
    description: '从五分钟上手到控制台的每一项功能，再到部署与升级。',
    descriptionEn: 'From a five-minute start to every console feature, then deployment and upgrades.',
    descriptionRu: 'От быстрого старта до каждой функции консоли, затем развёртывание и обновление.',
    descriptionTw: '從五分鐘上手到主控台的每一項功能，再到部署與升級。',
    sections: [start, guide, deploy],
  },
  {
    id: 'developer',
    title: '开发文档',
    en: 'Developer guide',
    ru: 'Документация для разработчиков',
    tw: '開發文件',
    description: '接入应用、扩展外部数据系统、理解架构与权限模型，并参与开发。',
    descriptionEn: 'Integrate applications, extend to external data systems, understand the architecture and contribute.',
    descriptionRu: 'Интеграция приложений, внешние системы данных, архитектура и модель прав, участие в разработке.',
    descriptionTw: '接入應用程式、擴充外部資料系統、理解架構與權限模型，並參與開發。',
    sections: [integration, external, architecture, reference, develop],
  },
  {
    id: 'releases',
    title: '发布日志',
    en: 'Releases',
    ru: 'Журнал выпусков',
    tw: '發布日誌',
    description: '各版本的变化。',
    descriptionEn: 'What changed in every version.',
    descriptionRu: 'Что изменилось в каждом выпуске.',
    descriptionTw: '各版本的變化。',
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

/** The title of a page, a group or a section in one locale; Russian and Traditional Chinese fall back to English and Simplified Chinese. */
export function titleOf(title: string, en: string, locale: Locale, ru?: string, tw?: string): string {
  if (locale === 'zh') return title
  if (locale === 'ru') return ru ?? en
  if (locale === 'zh-tw') return tw ?? title
  return en
}
