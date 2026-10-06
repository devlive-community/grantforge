// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { stripLocale } from '@/lib/i18n'
import type { Locale } from '@/lib/i18n'

/** A label in the source language, Chinese, with its translations. */
export interface Labelled {
  title: string
  en: string
  ru?: string
  tw?: string
  ko?: string
  ja?: string
}

/** A longer text in the source language, Chinese, with its translations. */
export interface Described {
  description: string
  descriptionEn: string
  descriptionRu?: string
  descriptionTw?: string
  descriptionKo?: string
  descriptionJa?: string
}

/** One page of the documentation, by its path below content/ without the .md extension. */
export interface NavPage extends Labelled {
  slug: string
}

/** One group of pages inside a part of the documentation. */
export interface NavGroup extends Labelled {
  pages: NavPage[]
}

/** A part of the documentation with its own sidebar. */
export interface NavSection extends Labelled, Described {
  id: string
  groups: NavGroup[]
}

/** A top-level part of the documentation: one tab in the header, with a sidebar of its own sections. */
export interface NavCategory extends Labelled, Described {
  id: string
  sections: NavSection[]
}

const start: NavSection = {
  id: 'start',
  title: '快速开始',
  en: 'Quick start',
  ru: 'Начало работы',
  tw: '快速開始',
  ko: '빠른 시작',
  ja: 'クイックスタート',
  description: '了解 GrantForge，五分钟跑起来并掌握核心概念。',
  descriptionEn: 'Understand GrantForge, run it in five minutes and learn the core concepts.',
  descriptionRu: 'Познакомьтесь с GrantForge, запустите его за пять минут и разберитесь с ключевыми понятиями.',
  descriptionTw: '了解 GrantForge，五分鐘跑起來並掌握核心概念。',
  descriptionKo: 'GrantForge를 이해하고 5분 만에 실행하며 핵심 개념을 익힙니다.',
  descriptionJa: 'GrantForge を知り、5分で動かして中心的な概念を身につけます。',
  groups: [
    { title: '入门', en: 'Getting started', ru: 'Первые шаги', tw: '入門', ko: '시작하기', ja: 'はじめに', pages: [
      { slug: 'start/introduction', title: '产品介绍', en: 'Introduction', ru: 'Введение', tw: '產品介紹', ko: '제품 소개', ja: '製品紹介' },
      { slug: 'start/quick-start', title: '五分钟上手', en: 'Set up in five minutes', ru: 'Быстрый старт за пять минут', tw: '五分鐘上手', ko: '5분 만에 시작하기', ja: '5分でセットアップ' },
      { slug: 'start/concepts', title: '核心概念', en: 'Core concepts', ru: 'Основные понятия', tw: '核心概念', ko: '핵심 개념', ja: '中心概念' },
    ] },
  ],
}

const guide: NavSection = {
  id: 'guide',
  title: '使用指南',
  en: 'User guide',
  ru: 'Руководство пользователя',
  tw: '使用指南',
  ko: '사용 가이드',
  ja: 'ユーザーガイド',
  description: '按控制台菜单讲解每项功能的用法。',
  descriptionEn: 'Every console feature, menu by menu.',
  descriptionRu: 'Каждая функция консоли, меню за меню.',
  descriptionTw: '按主控台選單講解每項功能的用法。',
  descriptionKo: '콘솔 메뉴별로 모든 기능의 사용 방법을 설명합니다.',
  descriptionJa: 'コンソールのメニューごとに、すべての機能の使い方を説明します。',
  groups: [
    { title: '账号', en: 'Account', ru: 'Учётная запись', tw: '帳號', ko: '계정', ja: 'アカウント', pages: [
      { slug: 'guide/console', title: '控制台导览', en: 'Console tour', ru: 'Обзор консоли', tw: '主控台導覽', ko: '콘솔 둘러보기', ja: 'コンソール案内' },
      { slug: 'guide/account', title: '登录、账号与两步验证', en: 'Sign-in, accounts and two-factor authentication', ru: 'Вход, учётная запись и двухфакторная проверка', tw: '登入、帳號與兩步驟驗證', ko: '로그인, 계정과 2단계 인증', ja: 'ログイン、アカウントと二段階認証' },
    ] },
    { title: '身份与组织', en: 'Identity & organization', ru: 'Идентификация и организация', tw: '身分與組織', ko: '신원과 조직', ja: 'アイデンティティと組織', pages: [
      { slug: 'guide/users', title: '用户', en: 'Users', ru: 'Пользователи', tw: '使用者', ko: '사용자', ja: 'ユーザー' },
      { slug: 'guide/organization', title: '部门、用户组与岗位', en: 'Departments, groups and positions', ru: 'Подразделения, группы и должности', tw: '部門、使用者群組與職位', ko: '부서, 사용자 그룹과 직위', ja: '部門、グループと職位' },
      { slug: 'guide/transfer', title: '批量导入导出', en: 'Bulk import and export', ru: 'Массовый импорт и экспорт', tw: '批次匯入匯出', ko: '대량 가져오기와 내보내기', ja: '一括インポートとエクスポート' },
      { slug: 'guide/identity-sources', title: '身份源（LDAP 与 OIDC）', en: 'Identity sources (LDAP and OIDC)', ru: 'Источники идентификации (LDAP и OIDC)', tw: '身分來源（LDAP 與 OIDC）', ko: '신원 소스(LDAP와 OIDC)', ja: 'アイデンティティソース（LDAP と OIDC）' },
    ] },
    { title: '访问控制', en: 'Access control', ru: 'Управление доступом', tw: '存取控制', ko: '접근 제어', ja: 'アクセス制御', pages: [
      { slug: 'guide/roles', title: '角色与授权', en: 'Roles and grants', ru: 'Роли и полномочия', tw: '角色與授權', ko: '역할과 권한 부여', ja: 'ロールと権限付与' },
      { slug: 'guide/data-permissions', title: '数据权限', en: 'Data permissions', ru: 'Права на данные', tw: '資料權限', ko: '데이터 권한', ja: 'データ権限' },
      { slug: 'guide/field-permissions', title: '字段权限', en: 'Field permissions', ru: 'Права на поля', tw: '欄位權限', ko: '필드 권한', ja: 'フィールド権限' },
      { slug: 'guide/explain', title: '权限解释、模拟与审计', en: 'Explanations, simulation and audit', ru: 'Объяснение, моделирование и аудит прав', tw: '權限解釋、模擬與稽核', ko: '권한 설명, 시뮬레이션과 감사', ja: '権限の説明、シミュレーションと監査' },
    ] },
    { title: '治理', en: 'Governance', ru: 'Жизненный цикл доступа', tw: '治理', ko: '거버넌스', ja: 'ガバナンス', pages: [
      { slug: 'guide/sod', title: '职责分离', en: 'Separation of duty', ru: 'Разделение обязанностей', tw: '職責分離', ko: '직무 분리', ja: '職務分離' },
      { slug: 'guide/access-requests', title: '权限申请与审批', en: 'Access requests and approvals', ru: 'Запросы доступа и согласования', tw: '權限申請與審批', ko: '권한 요청과 승인', ja: '権限申請と承認' },
      { slug: 'guide/access-reviews', title: '定期权限复核', en: 'Periodic access reviews', ru: 'Периодическая проверка прав доступа', tw: '定期權限覆核', ko: '정기 권한 검토', ja: '定期的な権限確認' },
    ] },
    { title: '平台', en: 'Platform', ru: 'Платформа', tw: '平台', ko: '플랫폼', ja: 'プラットフォーム', pages: [
      { slug: 'guide/tenants', title: '租户', en: 'Tenants', ru: 'Тенанты', tw: '租戶', ko: '테넌트', ja: 'テナント' },
      { slug: 'guide/catalog', title: '资源目录与 API 目录', en: 'Resource and API catalog', ru: 'Каталог ресурсов и каталог API', tw: '資源目錄與 API 目錄', ko: '리소스 카탈로그와 API 카탈로그', ja: 'リソースカタログと API カタログ' },
    ] },
  ],
}

const deploy: NavSection = {
  id: 'deploy',
  title: '部署与运维',
  en: 'Deployment',
  ru: 'Развёртывание и эксплуатация',
  tw: '部署與維運',
  ko: '배포와 운영',
  ja: 'デプロイと運用',
  description: '用发行包、容器或 Helm 部署，选择数据库并完成升级。',
  descriptionEn: 'Deploy from the release, containers or Helm, pick a database and upgrade.',
  descriptionRu: 'Установка из дистрибутива, контейнеров или Helm, выбор базы данных и обновление.',
  descriptionTw: '用發行包、容器或 Helm 部署，選擇資料庫並完成升級。',
  descriptionKo: '릴리스 패키지, 컨테이너 또는 Helm으로 배포하고 데이터베이스를 선택한 뒤 업그레이드합니다.',
  descriptionJa: 'リリースパッケージ、コンテナ、Helm でデプロイし、データベースを選び、アップグレードします。',
  groups: [
    { title: '安装与运行', en: 'Install and run', ru: 'Установка и запуск', tw: '安裝與執行', ko: '설치와 실행', ja: 'インストールと実行', pages: [
      { slug: 'deploy/installation', title: '安装发行包', en: 'Install the release', ru: 'Установка дистрибутива', tw: '安裝發行包', ko: '릴리스 패키지 설치', ja: 'リリースパッケージのインストール' },
      { slug: 'deploy/docker', title: 'Docker、Compose 与 Helm', en: 'Docker, Compose and Helm', ru: 'Docker, Compose и Helm', tw: 'Docker、Compose 與 Helm', ko: 'Docker, Compose와 Helm', ja: 'Docker、Compose と Helm' },
      { slug: 'deploy/databases', title: '数据库', en: 'Databases', ru: 'Базы данных', tw: '資料庫', ko: '데이터베이스', ja: 'データベース' },
    ] },
    { title: '升级', en: 'Upgrade', ru: 'Обновление', tw: '升級', ko: '업그레이드', ja: 'アップグレード', pages: [
      { slug: 'deploy/upgrade', title: '升级与旧版本迁移', en: 'Upgrading and legacy migration', ru: 'Обновление и миграция со старых версий', tw: '升級與舊版本遷移', ko: '업그레이드와 이전 버전 마이그레이션', ja: 'アップグレードと旧バージョンの移行' },
    ] },
  ],
}

const integration: NavSection = {
  id: 'integration',
  title: '应用接入',
  en: 'Integrating applications',
  ru: 'Интеграция приложений',
  tw: '應用程式接入',
  ko: '애플리케이션 연동',
  ja: 'アプリケーション連携',
  description: '让你的应用用 GrantForge 登录用户并判断权限。',
  descriptionEn: 'Sign users in through GrantForge and evaluate permissions in your application.',
  descriptionRu: 'Вход пользователей через GrantForge и проверка прав в вашем приложении.',
  descriptionTw: '讓你的應用程式用 GrantForge 登入使用者並判斷權限。',
  descriptionKo: '애플리케이션이 GrantForge로 사용자를 로그인하고 권한을 판단하게 합니다.',
  descriptionJa: '自分のアプリケーションから GrantForge でユーザーをログインさせ、権限を判定します。',
  groups: [
    { title: '协议', en: 'Protocols', ru: 'Протоколы', tw: '協定', ko: '프로토콜', ja: 'プロトコル', pages: [
      { slug: 'integration/overview', title: '接入概览', en: 'Integration overview', ru: 'Обзор интеграции', tw: '接入概覽', ko: '연동 개요', ja: '連携の概要' },
      { slug: 'integration/oauth', title: 'OAuth 2.1 与 OpenID Connect', en: 'OAuth 2.1 and OpenID Connect', ru: 'OAuth 2.1 и OpenID Connect', tw: 'OAuth 2.1 與 OpenID Connect', ko: 'OAuth 2.1과 OpenID Connect', ja: 'OAuth 2.1 と OpenID Connect' },
      { slug: 'integration/open-api', title: '权限查询开放 API', en: 'Permission open API', ru: 'Открытый API запросов прав доступа', tw: '權限查詢開放 API', ko: '권한 조회 오픈 API', ja: '権限照会オープン API' },
    ] },
    { title: 'SDK 与示例', en: 'SDKs and samples', ru: 'SDK и примеры', tw: 'SDK 與範例', ko: 'SDK와 예제', ja: 'SDK とサンプル', pages: [
      { slug: 'integration/java', title: 'Java SDK（Spring Boot）', en: 'Java SDK (Spring Boot)', ru: 'Java SDK (Spring Boot)', tw: 'Java SDK（Spring Boot）', ko: 'Java SDK(Spring Boot)', ja: 'Java SDK（Spring Boot）' },
      { slug: 'integration/javascript', title: 'JavaScript SDK', en: 'JavaScript SDK', ru: 'JavaScript SDK', tw: 'JavaScript SDK', ko: 'JavaScript SDK', ja: 'JavaScript SDK' },
      { slug: 'integration/samples', title: '示例应用', en: 'Sample applications', ru: 'Примеры приложений', tw: '範例應用程式', ko: '예제 애플리케이션', ja: 'サンプルアプリケーション' },
    ] },
  ],
}

const external: NavSection = {
  id: 'external',
  title: '外部系统权限',
  en: 'External systems',
  ru: 'Права на внешние системы',
  tw: '外部系統權限',
  ko: '외부 시스템 권한',
  ja: '外部システムの権限',
  description: '管理 HDFS、Hive 等外部数据系统的权限，并让代理在目标系统内执行策略。',
  descriptionEn: 'Manage permissions for external data systems and enforce policies with agents inside them.',
  descriptionRu: 'Права на внешние системы данных и принуждение политикам агентами внутри них.',
  descriptionTw: '管理 HDFS、Hive 等外部資料系統的權限，並讓代理在目標系統內執行策略。',
  descriptionKo: 'HDFS, Hive 같은 외부 데이터 시스템의 권한을 관리하고 에이전트가 대상 시스템 안에서 정책을 집행하게 합니다.',
  descriptionJa: 'HDFS や Hive など外部データシステムの権限を管理し、エージェントで対象システム内でポリシーを適用します。',
  groups: [
    { title: '管理与部署', en: 'Manage and deploy', ru: 'Управление и развёртывание', tw: '管理與部署', ko: '관리와 배포', ja: '管理とデプロイ', pages: [
      { slug: 'external/data-services', title: '数据服务、策略与代理', en: 'Data services, policies and agents', ru: 'Сервисы данных, политики и агенты', tw: '資料服務、策略與代理', ko: '데이터 서비스, 정책과 에이전트', ja: 'データサービス、ポリシーとエージェント' },
      { slug: 'external/hdfs-agent', title: 'HDFS NameNode 代理', en: 'HDFS NameNode agent', ru: 'Агент HDFS NameNode', tw: 'HDFS NameNode 代理', ko: 'HDFS NameNode 에이전트', ja: 'HDFS NameNode エージェント' },
    ] },
  ],
}

const architecture: NavSection = {
  id: 'architecture',
  title: '架构与安全',
  en: 'Architecture',
  ru: 'Архитектура и безопасность',
  tw: '架構與安全',
  ko: '아키텍처와 보안',
  ja: 'アーキテクチャとセキュリティ',
  description: '模块划分、权限模型、多租户隔离、安全设计与性能基准。',
  descriptionEn: 'Modules, the permission model, tenant isolation, security design and benchmarks.',
  descriptionRu: 'Модули, модель прав, изоляция тенантов, проектирование безопасности и бенчмарки.',
  descriptionTw: '模組劃分、權限模型、多租戶隔離、安全設計與效能基準。',
  descriptionKo: '모듈 구성, 권한 모델, 멀티 테넌시 격리, 보안 설계와 성능 벤치마크.',
  descriptionJa: 'モジュール構成、権限モデル、テナント分離、セキュリティ設計と性能ベンチマーク。',
  groups: [
    { title: '设计', en: 'Design', ru: 'Проектирование', tw: '設計', ko: '설계', ja: '設計', pages: [
      { slug: 'architecture/overview', title: '架构总览', en: 'Architecture overview', ru: 'Обзор архитектуры', tw: '架構總覽', ko: '아키텍처 개요', ja: 'アーキテクチャ概要' },
      { slug: 'architecture/permission-model', title: '权限模型', en: 'Permission model', ru: 'Модель прав доступа', tw: '權限模型', ko: '권한 모델', ja: '権限モデル' },
      { slug: 'architecture/multi-tenancy', title: '多租户与数据隔离', en: 'Multi-tenancy and data isolation', ru: 'Мультитенантность и изоляция данных', tw: '多租戶與資料隔離', ko: '멀티 테넌시와 데이터 격리', ja: 'マルチテナンシーとデータ分離' },
      { slug: 'architecture/security', title: '安全设计', en: 'Security design', ru: 'Проектирование безопасности', tw: '安全設計', ko: '보안 설계', ja: 'セキュリティ設計' },
      { slug: 'architecture/performance', title: '性能与基准', en: 'Performance and benchmarks', ru: 'Производительность и бенчмарки', tw: '效能與基準', ko: '성능과 벤치마크', ja: '性能とベンチマーク' },
    ] },
  ],
}

const reference: NavSection = {
  id: 'reference',
  title: '参考',
  en: 'Reference',
  ru: 'Справочник',
  tw: '參考',
  ko: '참조',
  ja: 'リファレンス',
  description: '配置项、REST 接口与错误码。',
  descriptionEn: 'Configuration, REST endpoints and error codes.',
  descriptionRu: 'Конфигурация, REST-интерфейсы и коды ошибок.',
  descriptionTw: '設定項、REST 介面與錯誤碼。',
  descriptionKo: '설정 항목, REST 인터페이스와 오류 코드.',
  descriptionJa: '設定項目、REST エンドポイントとエラーコード。',
  groups: [
    { title: '配置', en: 'Configuration', ru: 'Конфигурация', tw: '設定', ko: '설정', ja: '設定', pages: [
      { slug: 'reference/configuration', title: '配置参考', en: 'Configuration reference', ru: 'Справочник конфигурации', tw: '設定參考', ko: '설정 참조', ja: '設定リファレンス' },
    ] },
    { title: '契约', en: 'Contracts', ru: 'Контракты', tw: '契約', ko: '계약', ja: 'コントラクト', pages: [
      { slug: 'reference/api', title: 'REST API 参考', en: 'REST API reference', ru: 'Справочник REST API', tw: 'REST API 參考', ko: 'REST API 참조', ja: 'REST API リファレンス' },
      { slug: 'reference/errors', title: '错误码', en: 'Error codes', ru: 'Коды ошибок', tw: '錯誤碼', ko: '오류 코드', ja: 'エラーコード' },
    ] },
  ],
}

const develop: NavSection = {
  id: 'develop',
  title: '开发者',
  en: 'Developers',
  ru: 'Разработчикам',
  tw: '開發者',
  ko: '개발자',
  ja: '開発者',
  description: '在本地构建、测试与提交代码，并开发服务类型插件。',
  descriptionEn: 'Build, test and contribute locally, and develop service type plug-ins.',
  descriptionRu: 'Локальная сборка, тесты и контрибьюшены, а также разработка плагинов типов сервисов.',
  descriptionTw: '在本機建置、測試與提交程式碼，並開發服務類型外掛。',
  descriptionKo: '로컬에서 빌드하고 테스트하며 코드를 기여하고, 서비스 타입 플러그인을 개발합니다.',
  descriptionJa: 'ローカルでのビルド、テスト、コード貢献、サービスタイププラグインの開発。',
  groups: [
    { title: '服务端', en: 'Server', ru: 'Сервер', tw: '伺服端', ko: '서버', ja: 'サーバー', pages: [
      { slug: 'develop/development', title: '开发、测试与 CI', en: 'Development, testing and CI', ru: 'Разработка, тестирование и CI', tw: '開發、測試與 CI', ko: '개발, 테스트와 CI', ja: '開発、テストと CI' },
    ] },
    { title: '插件', en: 'Plug-ins', ru: 'Плагины', tw: '外掛', ko: '플러그인', ja: 'プラグイン', pages: [
      { slug: 'develop/plugins', title: '插件与服务类型', en: 'Plug-ins and service types', ru: 'Плагины и типы сервисов', tw: '外掛與服務類型', ko: '플러그인과 서비스 타입', ja: 'プラグインとサービスタイプ' },
    ] },
  ],
}

const changelog: NavSection = {
  id: 'changelog',
  title: '发布日志',
  en: 'Releases',
  ru: 'Журнал выпусков',
  tw: '發布日誌',
  ko: '릴리스 노트',
  ja: 'リリースノート',
  description: '各版本的变化。',
  descriptionEn: 'What changed in every version.',
  descriptionRu: 'Что изменилось в каждом выпуске.',
  descriptionTw: '各版本的變化。',
  descriptionKo: '각 버전의 변경 사항.',
  descriptionJa: '各バージョンでの変更点。',
  groups: [
    { title: '版本', en: 'Versions', ru: 'Версии', tw: '版本', ko: '버전', ja: 'バージョン', pages: [
      { slug: 'changelog/rebuild', title: '2026.0.0（重构版）', en: '2026.0.0 (rebuild)', ru: '2026.0.0 (переработанная версия)', tw: '2026.0.0（重構版）', ko: '2026.0.0(재구축)', ja: '2026.0.0（再構築版）' },
      { slug: 'changelog/1.0.6', title: '1.0.6', en: '1.0.6', ru: '1.0.6', tw: '1.0.6', ko: '1.0.6', ja: '1.0.6' },
      { slug: 'changelog/1.0.5', title: '1.0.5', en: '1.0.5', ru: '1.0.5', tw: '1.0.5', ko: '1.0.5', ja: '1.0.5' },
      { slug: 'changelog/1.0.4', title: '1.0.4', en: '1.0.4', ru: '1.0.4', tw: '1.0.4', ko: '1.0.4', ja: '1.0.4' },
      { slug: 'changelog/1.0.3', title: '1.0.3', en: '1.0.3', ru: '1.0.3', tw: '1.0.3', ko: '1.0.3', ja: '1.0.3' },
      { slug: 'changelog/1.0.2', title: '1.0.2', en: '1.0.2', ru: '1.0.2', tw: '1.0.2', ko: '1.0.2', ja: '1.0.2' },
      { slug: 'changelog/1.0.1', title: '1.0.1', en: '1.0.1', ru: '1.0.1', tw: '1.0.1', ko: '1.0.1', ja: '1.0.1' },
      { slug: 'changelog/1.0.0', title: '1.0.0', en: '1.0.0', ru: '1.0.0', tw: '1.0.0', ko: '1.0.0', ja: '1.0.0' },
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
    ko: '사용자 문서',
    ja: 'ユーザードキュメント',
    description: '从五分钟上手到控制台的每一项功能，再到部署与升级。',
    descriptionEn: 'From a five-minute start to every console feature, then deployment and upgrades.',
    descriptionRu: 'От быстрого старта до каждой функции консоли, затем развёртывание и обновление.',
    descriptionTw: '從五分鐘上手到主控台的每一項功能，再到部署與升級。',
    descriptionKo: '5분 만에 시작하는 것부터 콘솔의 모든 기능, 배포와 업그레이드까지.',
    descriptionJa: '5分で始めるところから、コンソールのすべての機能、配置とアップグレードまで。',
    sections: [start, guide, deploy],
  },
  {
    id: 'developer',
    title: '开发文档',
    en: 'Developer guide',
    ru: 'Документация для разработчиков',
    tw: '開發文件',
    ko: '개발자 문서',
    ja: '開発者ドキュメント',
    description: '接入应用、扩展外部数据系统、理解架构与权限模型，并参与开发。',
    descriptionEn: 'Integrate applications, extend to external data systems, understand the architecture and contribute.',
    descriptionRu: 'Интеграция приложений, внешние системы данных, архитектура и модель прав, участие в разработке.',
    descriptionTw: '接入應用程式、擴充外部資料系統、理解架構與權限模型，並參與開發。',
    descriptionKo: '애플리케이션 연동, 외부 데이터 시스템 확장, 아키텍처와 권한 모델 이해, 개발 참여.',
    descriptionJa: 'アプリケーションの連携、外部データシステムへの拡張、アーキテクチャと権限モデルの理解、開発への参加。',
    sections: [integration, external, architecture, reference, develop],
  },
  {
    id: 'releases',
    title: '发布日志',
    en: 'Releases',
    ru: 'Журнал выпусков',
    tw: '發布日誌',
    ko: '릴리스 노트',
    ja: 'リリースノート',
    description: '各版本的变化。',
    descriptionEn: 'What changed in every version.',
    descriptionRu: 'Что изменилось в каждом выпуске.',
    descriptionTw: '各版本的變化。',
    descriptionKo: '각 버전의 변경 사항.',
    descriptionJa: '各バージョンの変更点。',
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

/** The label of a page, a group or a section in one locale; the translations fall back to English and Chinese. */
export function titleOf(node: Labelled, locale: Locale): string {
  if (locale === 'ru') return node.ru ?? node.en
  if (locale === 'zh-tw') return node.tw ?? node.title
  if (locale === 'ko') return node.ko ?? node.en
  if (locale === 'ja') return node.ja ?? node.en
  return locale === 'zh' ? node.title : node.en
}

/** The description of a section or a category in one locale, with the same fallbacks as its label. */
export function descriptionOf(node: Described, locale: Locale): string {
  if (locale === 'ru') return node.descriptionRu ?? node.descriptionEn
  if (locale === 'zh-tw') return node.descriptionTw ?? node.description
  if (locale === 'ko') return node.descriptionKo ?? node.descriptionEn
  if (locale === 'ja') return node.descriptionJa ?? node.descriptionEn
  return locale === 'zh' ? node.description : node.descriptionEn
}
