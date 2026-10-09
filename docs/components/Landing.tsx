// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import Link from 'next/link'
import { pageHref } from '@/lib/i18n'
import type { Locale } from '@/lib/i18n'
import { categories, descriptionOf, titleOf } from '@/lib/navigation'

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
  'zh-tw': {
    badge: '開源 · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>每一項授權，<br /><span className="text-indigo-300">都有清晰的邊界。</span></>,
    heroText: 'GrantForge 是面向開發者的統一權限平台：管理使用者與組織，為角色授予選單、按鈕、API、資料列與欄位，再透過標準協定讓你的應用程式直接使用這些權限。',
    quickStart: '五分鐘上手',
    learnMore: '了解產品',
    capabilities: '能力',
    capabilitiesTitle: '從登入到每一列資料',
    readDocs: '閱讀文件 →',
    license: '© 2026 Devlive Community · 以 MIT 授權條款開源',
    dashboardAlt: 'GrantForge 主控台的工作空間總覽',
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
  ru: {
    badge: 'Открытый исходный код · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>Каждое полномочие,<br /><span className="text-indigo-300">с ясными границами.</span></>,
    heroText: 'GrantForge — единая платформа управления доступом для разработчиков: управляйте пользователями и организацией, выдавайте ролям меню, кнопки, API, строки данных и поля, а приложение пусть использует эти права по стандартным протоколам.',
    quickStart: 'Быстрый старт за пять минут',
    learnMore: 'О продукте',
    capabilities: 'Возможности',
    capabilitiesTitle: 'От входа до каждой строки данных',
    readDocs: 'Читать документацию →',
    license: '© 2026 Devlive Community · открытый исходный код под лицензией MIT',
    dashboardAlt: 'Обзор рабочей области консоли GrantForge',
  },
  ko: {
    badge: '오픈 소스 · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>모든 권한 부여에<br /><span className="text-indigo-300">명확한 경계가 있습니다.</span></>,
    heroText: 'GrantForge는 개발자를 위한 통합 권한 플랫폼입니다. 사용자와 조직을 관리하고 역할에 메뉴, 버튼, API, 데이터 행과 필드를 부여하며, 표준 프로토콜로 애플리케이션이 그 권한을 그대로 사용하게 합니다.',
    quickStart: '5분 만에 시작하기',
    learnMore: '제품 알아보기',
    capabilities: '역량',
    capabilitiesTitle: '로그인부터 모든 데이터 행까지',
    readDocs: '문서 읽기 →',
    license: '© 2026 Devlive Community · MIT 라이선스로 공개된 오픈 소스',
    dashboardAlt: 'GrantForge 콘솔 작업 공간의 개요 화면',
  },
  ja: {
    badge: 'オープンソース · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>すべての権限付与に、<br /><span className="text-indigo-300">明確な境界があります。</span></>,
    heroText: 'GrantForge は開発者向けの統合権限プラットフォームです。ユーザーと組織を管理し、ロールにメニュー、ボタン、API、データ行、フィールドを付与し、標準プロトコルでアプリケーションにその権限を使わせます。',
    quickStart: '5分でセットアップ',
    learnMore: '製品について',
    capabilities: '機能',
    capabilitiesTitle: 'ログインからすべてのデータ行まで',
    readDocs: 'ドキュメントを読む →',
    license: '© 2026 Devlive Community · MIT ライセンスのオープンソース',
    dashboardAlt: 'GrantForge コンソールのワークスペース概要画面',
  },
  de: {
    badge: 'Open Source · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>Jede Berechtigung<br /><span className="text-indigo-300">mit einer klaren Grenze.</span></>,
    heroText: 'GrantForge ist eine einheitliche Berechtigungsplattform für Entwickler: Benutzer und Organisation verwalten, Rollen Menüs, Schaltflächen, APIs, Datenzeilen und Felder zuweisen und die eigene Anwendung diese Rechte über Standardprotokolle nutzen lassen.',
    quickStart: 'Start in fünf Minuten',
    learnMore: 'Produkt kennenlernen',
    capabilities: 'Fähigkeiten',
    capabilitiesTitle: 'Von der Anmeldung bis zur letzten Datenzeile',
    readDocs: 'Dokumentation lesen →',
    license: '© 2026 Devlive Community · Open Source unter der MIT-Lizenz',
    dashboardAlt: 'Die Arbeitsbereichsübersicht der GrantForge-Konsole',
  },
  fr: {
    badge: 'Open source · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>Chaque autorisation,<br /><span className="text-indigo-300">avec une frontière claire.</span></>,
    heroText: 'GrantForge est une plateforme unifiée d’autorisations pour les développeurs : gérez les utilisateurs et l’organisation, accordez aux rôles des menus, des boutons, des API, des lignes et des champs de données, puis laissez vos applications utiliser ces droits via des protocoles standard.',
    quickStart: 'Démarrer en cinq minutes',
    learnMore: 'Découvrir le produit',
    capabilities: 'Capacités',
    capabilitiesTitle: 'De la connexion à la dernière ligne de données',
    readDocs: 'Lire la documentation →',
    license: '© 2026 Devlive Community · open source sous licence MIT',
    dashboardAlt: 'La vue d’ensemble de l’espace de travail de la console GrantForge',
  },
  es: {
    badge: 'Código abierto · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>Cada permiso,<br /><span className="text-indigo-300">con un límite bien definido.</span></>,
    heroText: 'GrantForge es una plataforma unificada de permisos para desarrolladores: gestiona usuarios y organización, concede a los roles menús, botones, API, filas y campos de datos, y deja que tu aplicación use esos permisos mediante protocolos estándar.',
    quickStart: 'Puesta en marcha en cinco minutos',
    learnMore: 'Conocer el producto',
    capabilities: 'Capacidades',
    capabilitiesTitle: 'Del inicio de sesión a la última fila de datos',
    readDocs: 'Leer la documentación →',
    license: '© 2026 Devlive Community · código abierto bajo la licencia MIT',
    dashboardAlt: 'El resumen del espacio de trabajo de la consola de GrantForge',
  },
  'pt-br': {
    badge: 'Código aberto · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>Cada permissão,<br /><span className="text-indigo-300">com um limite bem definido.</span></>,
    heroText: 'O GrantForge é uma plataforma unificada de permissões para desenvolvedores: gerencie usuários e organização, conceda a papéis menus, botões, APIs, linhas e campos de dados, e deixe que sua aplicação use essas permissões por meio de protocolos padrão.',
    quickStart: 'Configuração em cinco minutos',
    learnMore: 'Conhecer o produto',
    capabilities: 'Capacidades',
    capabilitiesTitle: 'Do login à última linha de dados',
    readDocs: 'Ler a documentação →',
    license: '© 2026 Devlive Community · código aberto sob a licença MIT',
    dashboardAlt: 'O resumo do espaço de trabalho do console GrantForge',
  },
  it: {
    badge: 'Open source · MIT · Java 17 / Spring Boot 4',
    heroTitle: <>Ogni permesso,<br /><span className="text-indigo-300">con un confine chiaro.</span></>,
    heroText: 'GrantForge è una piattaforma unificata di permessi per sviluppatori: gestisci utenti e organizzazione, concedi ai ruoli menu, pulsanti, API, righe e campi di dati, e lascia che la tua applicazione usi questi permessi tramite protocolli standard.',
    quickStart: 'Configurazione in cinque minuti',
    learnMore: 'Scopri il prodotto',
    capabilities: 'Funzionalità',
    capabilitiesTitle: "Dall’accesso all’ultima riga di dati",
    readDocs: 'Leggi la documentazione →',
    license: '© 2026 Devlive Community · open source con licenza MIT',
    dashboardAlt: "La panoramica dell’area di lavoro della console GrantForge",
  },
} as const

const features = [
  {
    title: { zh: '用户与组织', 'zh-tw': '使用者與組織', en: 'Users & organization', ru: 'Пользователи и организация', ko: '사용자와 조직', ja: 'ユーザーと組織', de: 'Benutzer & Organisation', fr: 'Utilisateurs et organisation', es: 'Usuarios y organización', 'pt-br': 'Usuários e organização', it: 'Utenti e organizzazione' },
    text: { zh: '多租户、部门树、用户组与岗位，CSV 批量导入导出，接入 LDAP/AD 与 OIDC 身份源。', 'zh-tw': '多租戶、部門樹、使用者群組與職位，CSV 批次匯入匯出，接入 LDAP/AD 與 OIDC 身分來源。', en: 'Multi-tenancy, a department tree, groups and positions, CSV bulk import/export, and LDAP/AD or OIDC identity sources.', ru: 'Мультитенантность, дерево подразделений, группы и должности, массовый импорт и экспорт в CSV, источники идентификации LDAP/AD и OIDC.', ko: '멀티 테넌시, 부서 트리, 사용자 그룹과 직위, CSV 대량 가져오기·내보내기, LDAP/AD와 OIDC 신원 소스 연동.', ja: 'マルチテナンシー、部門ツリー、ユーザーグループと職位、CSV の一括インポート・エクスポート、LDAP/AD と OIDC のアイデンティティソース連携。', de: 'Mehrmandantenfähigkeit, ein Abteilungsbaum, Gruppen und Stellen, CSV-Massenimport und -export sowie die Anbindung von LDAP/AD- und OIDC-Identitätsquellen.', fr: 'Multi-locataires, arborescence des services, groupes et postes, import et export CSV en masse, et sources d’identité LDAP/AD ou OIDC.', es: 'Multi-inquilinos, árbol de departamentos, grupos y puestos, importación y exportación masiva en CSV, y fuentes de identidad LDAP/AD u OIDC.', 'pt-br': 'Multi-tenant, árvore de departamentos, grupos e cargos, importação e exportação em massa de CSV, e fontes de identidade LDAP/AD e OIDC.', it: 'Multi-tenant, albero dei dipartimenti, gruppi e posizioni, importazione ed esportazione in massa in CSV, e fonti di identità LDAP/AD e OIDC.' },
    href: '/guide/users/',
  },
  {
    title: { zh: '功能授权', 'zh-tw': '功能授權', en: 'Functional authorization', ru: 'Функциональная авторизация', ko: '기능 권한', ja: '機能の権限付与', de: 'Funktionsberechtigungen', fr: 'Autorisations fonctionnelles', es: 'Permisos funcionales', 'pt-br': 'Autorização funcional', it: 'Autorizzazione funzionale' },
    text: { zh: '菜单、页面、按钮与 API 统一建模为资源，角色可继承，授权前就能看到影响范围。', 'zh-tw': '選單、頁面、按鈕與 API 統一建模為資源，角色可繼承，授權前就能看到影響範圍。', en: 'Menus, pages, buttons and APIs are modeled as one resource catalog, roles inherit, and impact is visible before granting.', ru: 'Меню, страницы, кнопки и API описаны единым каталогом ресурсов, роли наследуются, а последствия видны до выдачи прав.', ko: '메뉴, 페이지, 버튼, API를 하나의 리소스 카탈로그로 모델링하고 역할은 상속되며, 권한을 부여하기 전에 영향 범위를 확인할 수 있습니다.', ja: 'メニュー、ページ、ボタン、API を一つのリソースカタログとしてモデル化し、ロールは継承でき、権限を付与する前に影響範囲を確認できます。', de: 'Menüs, Seiten, Schaltflächen und APIs bilden einen gemeinsamen Ressourcenkatalog, Rollen erben Rechte, und die Auswirkung ist vor der Vergabe sichtbar.', fr: 'Menus, pages, boutons et API sont modélisés dans un seul catalogue de ressources, les rôles héritent, et l’impact est visible avant l’attribution.', es: 'Los menús, páginas, botones y API se modelan en un único catálogo de recursos, los roles heredan, y el impacto se ve antes de conceder el permiso.', 'pt-br': 'Menus, páginas, botões e APIs são modelados como um único catálogo de recursos, os papéis herdam permissões, e o impacto fica visível antes da concessão.', it: 'Menu, pagine, pulsanti e API sono modellati in un unico catalogo di risorse, i ruoli ereditano, e l’impatto è visibile prima della concessione.' },
    href: '/guide/roles/',
  },
  {
    title: { zh: '数据与字段权限', 'zh-tw': '資料與欄位權限', en: 'Data & field permissions', ru: 'Права на данные и поля', ko: '데이터와 필드 권한', ja: 'データとフィールドの権限', de: 'Daten- und Feldberechtigungen', fr: 'Autorisations sur les données et les champs', es: 'Permisos sobre datos y campos', 'pt-br': 'Permissões de dados e campos', it: 'Permessi su dati e campi' },
    text: { zh: '按条件限定可见的行，按角色隐藏、脱敏或只读字段，业务代码只需一行接入。', 'zh-tw': '依條件限定可見的資料列，依角色隱藏、遮蔽或只讀欄位，業務程式碼只需一行接入。', en: 'Conditions bound the visible rows; fields are hidden, masked or read-only per role, with one line of business code.', ru: 'Условия ограничивают видимые строки, а поля скрываются, маскируются или открываются только на чтение в зависимости от роли.', ko: '조건으로 보이는 행을 제한하고 역할별로 필드를 숨기거나 마스킹하거나 읽기 전용으로 두며, 비즈니스 코드는 한 줄만 넣으면 됩니다.', ja: '条件で表示される行を制限し、ロールごとにフィールドを隠す・マスクする・読み取り専用にでき、業務コードは一行追加するだけです。', de: 'Bedingungen begrenzen die sichtbaren Zeilen, Felder werden pro Rolle versteckt, maskiert oder schreibgeschützt – mit einer Zeile Anwendungscode.', fr: 'Des conditions bornent les lignes visibles ; les champs sont masqués, caviardés ou en lecture seule selon le rôle, avec une seule ligne de code métier.', es: 'Las condiciones acotan las filas visibles; los campos se ocultan, se enmascaran o quedan en solo lectura según el rol, con una sola línea de código de negocio.', 'pt-br': 'Condições limitam as linhas visíveis; campos são ocultados, mascarados ou deixados em somente leitura por papel, com uma única linha de código de negócio.', it: 'Le condizioni limitano le righe visibili; i campi vengono nascosti, mascherati o resi in sola lettura per ruolo, con una sola riga di codice applicativo.' },
    href: '/guide/data-permissions/',
  },
  {
    title: { zh: '可解释与审计', 'zh-tw': '可解釋與稽核', en: 'Explainability & audit', ru: 'Объяснимость и аудит', ko: '설명 가능성과 감사', ja: '説明可能性と監査', de: 'Nachvollziehbarkeit & Audit', fr: 'Explicabilité et audit', es: 'Explicabilidad y auditoría', 'pt-br': 'Explicabilidade e auditoria', it: 'Spiegabilità e audit' },
    text: { zh: '回答“他为什么能/不能”，模拟授权变更，全量审计可筛选导出。', 'zh-tw': '回答「他為什麼能/不能」，模擬授權變更，全量稽核可篩選匯出。', en: 'Answers why somebody can or cannot, simulates grant changes, and filters and exports the full audit trail.', ru: 'Отвечает, почему пользователь может или не может действовать, моделирует изменения выдач и позволяет фильтровать и выгружать журнал аудита.', ko: '"왜 될까, 왜 안 될까"에 답하고 권한 변경을 시뮬레이션하며, 전체 감사 기록을 필터링해 내보냅니다.', ja: '「なぜできるか、なぜできないか」に答え、権限変更をシミュレーションし、監査記録全体を絞り込んで書き出せます。', de: 'Beantwortet, warum jemand etwas darf oder nicht darf, simuliert Änderungen an Berechtigungen und filtert und exportiert den vollständigen Audit-Verlauf.', fr: 'Explique pourquoi quelqu’un peut ou ne peut pas, simule les changements d’autorisation, et filtre et exporte l’intégralité du journal d’audit.', es: 'Responde por qué alguien puede o no puede, simula cambios de permisos, y filtra y exporta todo el registro de auditoría.', 'pt-br': 'Responde por que alguém pode ou não pode, simula mudanças de permissões, e filtra e exporta todo o registro de auditoria.', it: 'Risponde perché qualcuno può o non può, simula le modifiche ai permessi, e filtra ed esporta l’intero registro di audit.' },
    href: '/guide/explain/',
  },
  {
    title: { zh: '治理', 'zh-tw': '治理', en: 'Governance', ru: 'Управление жизненным циклом доступа', ko: '거버넌스', ja: 'ガバナンス', de: 'Governance', fr: 'Gouvernance', es: 'Gobernanza', 'pt-br': 'Governança', it: 'Governance' },
    text: { zh: '职责分离、权限申请与限时授权、定期复核，两步验证与敏感操作二次确认。', 'zh-tw': '職責分離、權限申請與限時授權、定期覆核，兩步驟驗證與敏感操作二次確認。', en: 'Separation of duty, access requests with expiring grants, periodic reviews, two-factor authentication and step-up verification.', ru: 'Разделение обязанностей, запросы доступа с истекающими выдачами, периодические проверки, двухфакторная проверка и подтверждение чувствительных операций.', ko: '직무 분리, 기한이 정해진 권한 요청, 정기 검토, 2단계 인증과 민감한 작업의 추가 확인.', ja: '職務分離、期限付きの権限申請、定期的な確認、二段階認証と重要な操作の追加確認。', de: 'Funktionstrennung, Berechtigungsanträge mit befristeten Rechten, regelmäßige Prüfungen, Zwei-Faktor-Authentifizierung und Bestätigung sensibler Aktionen.', fr: 'Séparation des tâches, demandes d’accès avec des autorisations à durée limitée, revues périodiques, authentification à deux facteurs et confirmation des opérations sensibles.', es: 'Separación de funciones, solicitudes de acceso con permisos temporales, revisiones periódicas, autenticación en dos pasos y confirmación de operaciones sensibles.', 'pt-br': 'Separação de funções, solicitações de acesso com permissões temporárias, revisões periódicas, autenticação de dois fatores e confirmação de operações sensíveis.', it: 'Separazione dei compiti, richieste di accesso con permessi a scadenza, revisioni periodiche, autenticazione a due fattori e conferma delle operazioni sensibili.' },
    href: '/guide/sod/',
  },
  {
    title: { zh: '应用接入', 'zh-tw': '應用程式接入', en: 'Application integration', ru: 'Интеграция приложений', ko: '애플리케이션 연동', ja: 'アプリケーション連携', de: 'Anwendungsanbindung', fr: 'Intégration d’applications', es: 'Integración de aplicaciones', 'pt-br': 'Integração de aplicações', it: 'Integrazione delle applicazioni' },
    text: { zh: 'OAuth 2.1 / OpenID Connect、权限查询开放 API、Spring Boot Starter 与 JavaScript SDK。', 'zh-tw': 'OAuth 2.1 / OpenID Connect、權限查詢開放 API、Spring Boot Starter 與 JavaScript SDK。', en: 'OAuth 2.1 / OpenID Connect, a permission open API, the Spring Boot starter and the JavaScript SDK.', ru: 'OAuth 2.1 / OpenID Connect, открытый API запросов прав доступа, Spring Boot Starter и JavaScript SDK.', ko: 'OAuth 2.1 / OpenID Connect, 권한 조회 오픈 API, Spring Boot Starter와 JavaScript SDK.', ja: 'OAuth 2.1 / OpenID Connect、権限照会のオープン API、Spring Boot Starter と JavaScript SDK。', de: 'OAuth 2.1 / OpenID Connect, eine offene API für Berechtigungsabfragen, das Spring Boot Starter und das JavaScript SDK.', fr: 'OAuth 2.1 / OpenID Connect, une API ouverte de consultation des autorisations, le Spring Boot starter et le SDK JavaScript.', es: 'OAuth 2.1 / OpenID Connect, una API abierta de consulta de permisos, el Spring Boot starter y el SDK de JavaScript.', 'pt-br': 'OAuth 2.1 / OpenID Connect, uma API aberta de consulta de permissões, o Spring Boot starter e o SDK JavaScript.', it: 'OAuth 2.1 / OpenID Connect, un’API aperta di consultazione dei permessi, lo Spring Boot starter e l’SDK JavaScript.' },
    href: '/integration/overview/',
  },
] as const

/** The landing page, in the language of its route: / for Chinese, /en/ for English, /fr/ for French, and so on. */
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
        <div className="mx-auto grid max-w-[90rem] gap-8 px-5 py-16 md:grid-cols-2 xl:grid-cols-4">
          {categories.map(category => (
            <div key={category.id}>
              <h3 className="font-semibold">{titleOf(category, locale)}</h3>
              <p className="mt-2 text-sm leading-6 text-muted">{descriptionOf(category, locale)}</p>
              <ul className="mt-4 space-y-1.5 text-sm">
                {category.sections.flatMap(section => section.groups.flatMap(group => group.pages)).slice(0, 5).map(page => (
                  <li key={page.slug}><Link href={pageHref(page.slug, locale)} className="text-muted hover:text-brand">{titleOf(page, locale)}</Link></li>
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
