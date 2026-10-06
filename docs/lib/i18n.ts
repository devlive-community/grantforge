// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * The documentation languages. Chinese pages keep their historic paths; the others sit under a prefix. Simplified
 * Chinese is the source of every translation and keeps the unprefixed URLs.
 */
export type Locale = 'zh' | 'zh-tw' | 'en' | 'ru' | 'ko' | 'ja' | 'de' | 'fr' | 'es'

/** The path segment of every page of a locale. Chinese has none, so its URLs never change. */
const PREFIX: Record<Locale, string> = { zh: '', 'zh-tw': 'zh-tw', en: 'en', ru: 'ru', ko: 'ko', ja: 'ja', de: 'de', fr: 'fr', es: 'es' }

/** The path segment every English page sits under. */
export const ENGLISH_PREFIX = PREFIX.en

/** The path segment every Russian page sits under. */
export const RUSSIAN_PREFIX = PREFIX.ru

/** The path segment every Traditional Chinese page sits under. */
export const TRADITIONAL_PREFIX = PREFIX['zh-tw']

/** The path segment every Korean page sits under. */
export const KOREAN_PREFIX = PREFIX.ko

/** The path segment every Japanese page sits under. */
export const JAPANESE_PREFIX = PREFIX.ja

/** The path segment every German page sits under. */
export const GERMAN_PREFIX = PREFIX.de

/** The path segment every French page sits under. */
export const FRENCH_PREFIX = PREFIX.fr

/** The path segment every Spanish page sits under. */
export const SPANISH_PREFIX = PREFIX.es

/** Every language of the site, in the order of the switcher. */
export const LOCALES: readonly Locale[] = ['zh', 'zh-tw', 'en', 'ru', 'ko', 'ja', 'de', 'fr', 'es']

/** The label of each language in the switcher, in the language itself. */
export const LOCALE_LABELS: Record<Locale, string> = { zh: '中文', 'zh-tw': '繁體', en: 'English', ru: 'Русский', ko: '한국어', ja: '日本語', de: 'Deutsch', fr: 'Français', es: 'Español' }

/** The BCP 47 tag of each language, for <html lang> and the switcher's links. */
export const LOCALE_HREFLANG: Record<Locale, string> = { zh: 'zh-CN', 'zh-tw': 'zh-TW', en: 'en', ru: 'ru', ko: 'ko', ja: 'ja', de: 'de', fr: 'fr', es: 'es' }

/** The path prefix a locale lives under ('' for Chinese, and the language itself for the rest). */
export function localePrefix(locale: Locale): string {
  return PREFIX[locale]
}

/** The browser storage key of the language the visitor picked explicitly. */
export const LOCALE_STORAGE_KEY = 'GrantForgeDocsLocale'

/** Reads the locale of a navigation slug or a pathname ('en/guide/roles' or '/ru/guide/roles/' -> that locale). */
export function localeOf(slug: string): Locale {
  const first = slug.split('/').find(part => part !== '')
  return LOCALES.find(locale => PREFIX[locale] !== '' && PREFIX[locale] === first) ?? 'zh'
}

/** Removes the locale prefix from a slug or pathname ('ru/guide/roles' -> 'guide/roles'). */
export function stripLocale(slug: string): string {
  const prefix = PREFIX[localeOf(slug)]
  if (!prefix) return slug
  const at = slug.indexOf(prefix)
  return at < 0 ? slug : slug.slice(at + prefix.length + 1)
}

/** The href of a page in one locale ('guide/roles' + 'ru' -> '/ru/guide/roles/'). */
export function pageHref(slug: string, locale: Locale): string {
  const clean = `/${stripLocale(slug).replace(/^\/+|\/+$/g, '')}/`
  const prefix = PREFIX[locale]
  return prefix ? `/${prefix}${clean}` : clean
}

/** The counterpart href of the current pathname in another locale, home page included. */
export function switchHref(pathname: string, locale: Locale): string {
  const current = PREFIX[localeOf(pathname)]
  let stripped = pathname
  if (current) {
    stripped = pathname.startsWith(`/${current}/`) ? pathname.slice(current.length + 1)
      : pathname === `/${current}` ? '/' : pathname
  }
  if (!stripped.startsWith('/')) stripped = `/${stripped}`
  const prefix = PREFIX[locale]
  if (!prefix) return stripped
  return stripped === '/' ? `/${prefix}/` : `/${prefix}${stripped}`
}

/** One interface string of the site shell, in every language. */
type Message =
  | 'search'
  | 'searchPlaceholder'
  | 'searchAria'
  | 'noResults'
  | 'onThisPage'
  | 'previous'
  | 'next'
  | 'editPage'
  | 'untranslated'
  | 'navAria'
  | 'languageAria'
  | 'notTranslatedYet'
  | 'theme'
  | 'notFoundTitle'
  | 'notFoundText'
  | 'notFoundHome'

const messages: Record<Message, Record<Locale, string>> = {
  search: { zh: '搜索文档', 'zh-tw': '搜尋文件', en: 'Search docs', ru: 'Поиск по документации', ko: '문서 검색', ja: 'ドキュメント検索', de: 'Dokumentation durchsuchen', fr: 'Rechercher dans la documentation', es: 'Buscar en la documentación' },
  searchPlaceholder: {
    zh: '搜索标题与正文，例如：数据权限',
    'zh-tw': '搜尋標題與內文，例如：資料權限',
    en: 'Search titles and pages, e.g. data permissions',
    ru: 'Поиск по заголовкам и тексту, например: права на данные',
    ko: '제목과 본문을 검색하세요. 예: 데이터 권한',
    ja: 'タイトルと本文を検索します。例: データ権限',
    de: 'Titel und Text durchsuchen, z. B. Datenberechtigungen',
    fr: 'Rechercher un titre ou un texte, par ex. autorisations sur les données',
    es: 'Buscar títulos y texto, p. ej. permisos sobre los datos',
  },
  searchAria: { zh: '搜索内容', 'zh-tw': '搜尋內容', en: 'Search', ru: 'Поиск', ko: '검색', ja: '検索', de: 'Suchen', fr: 'Rechercher', es: 'Buscar' },
  noResults: { zh: '没有找到相关内容', 'zh-tw': '沒有找到相關內容', en: 'Nothing matches this query', ru: 'Ничего не найдено', ko: '검색 결과가 없습니다', ja: '該当する内容が見つかりません', de: 'Keine Treffer für diese Suche', fr: 'Aucun résultat pour cette recherche', es: 'No hay resultados para esta búsqueda' },
  onThisPage: { zh: '本页目录', 'zh-tw': '本頁目錄', en: 'On this page', ru: 'На этой странице', ko: '이 페이지 목차', ja: 'このページの目次', de: 'Auf dieser Seite', fr: 'Sur cette page', es: 'En esta página' },
  previous: { zh: '上一篇', 'zh-tw': '上一篇', en: 'Previous', ru: 'Предыдущая', ko: '이전', ja: '前へ', de: 'Zurück', fr: 'Précédent', es: 'Anterior' },
  next: { zh: '下一篇', 'zh-tw': '下一篇', en: 'Next', ru: 'Следующая', ko: '다음', ja: '次へ', de: 'Weiter', fr: 'Suivant', es: 'Siguiente' },
  editPage: { zh: '在 GitHub 上编辑此页', 'zh-tw': '在 GitHub 上編輯此頁', en: 'Edit this page on GitHub', ru: 'Редактировать эту страницу на GitHub', ko: 'GitHub에서 이 페이지 편집', ja: 'GitHub でこのページを編集', de: 'Diese Seite auf GitHub bearbeiten', fr: 'Modifier cette page sur GitHub', es: 'Editar esta página en GitHub' },
  untranslated: {
    zh: '此页面还没有英文翻译，先显示中文原文。',
    'zh-tw': '此頁面還沒有繁體翻譯，先顯示簡體原文。',
    en: 'This page is not translated into English yet; the Chinese original is shown.',
    ru: 'Эта страница ещё не переведена на русский, показан китайский оригинал.',
    ko: '이 페이지는 아직 한국어로 번역되지 않아 중국어 원문을 표시합니다.',
    ja: 'このページはまだ日本語に翻訳されていないため、中国語の原文を表示しています。',
    de: 'Diese Seite ist noch nicht auf Deutsch übersetzt, daher wird das chinesische Original angezeigt.',
    fr: 'Cette page n’est pas encore traduite en français ; le texte original en chinois est affiché.',
    es: 'Esta página todavía no está traducida al español; se muestra el texto original en chino.',
  },
  navAria: { zh: '文档分类', 'zh-tw': '文件分類', en: 'Documentation sections', ru: 'Разделы документации', ko: '문서 분류', ja: 'ドキュメントの分類', de: 'Dokumentationsbereiche', fr: 'Sections de la documentation', es: 'Secciones de la documentación' },
  languageAria: { zh: '语言', 'zh-tw': '語言', en: 'Language', ru: 'Язык', ko: '언어', ja: '言語', de: 'Sprache', fr: 'Langue', es: 'Idioma' },
  notTranslatedYet: { zh: '尚未翻译', 'zh-tw': '尚未翻譯', en: 'Not translated yet', ru: 'Ещё не переведено', ko: '번역 예정', ja: '未翻訳', de: 'Noch nicht übersetzt', fr: 'Pas encore traduite', es: 'Sin traducir todavía' },
  theme: { zh: '切换深色/浅色主题', 'zh-tw': '切換深色/淺色主題', en: 'Switch the dark or light theme', ru: 'Переключить тёмную или светлую тему', ko: '다크/라이트 테마 전환', ja: 'ダーク/ライトテーマの切り替え', de: 'Zwischen dunklem und hellem Design wechseln', fr: 'Basculer entre le thème sombre et le thème clair', es: 'Cambiar entre el tema oscuro y el claro' },
  notFoundTitle: { zh: '没有这一页', 'zh-tw': '沒有這一頁', en: 'This page does not exist', ru: 'Такой страницы нет', ko: '페이지가 없습니다', ja: 'このページはありません', de: 'Diese Seite gibt es nicht', fr: 'Cette page n’existe pas', es: 'Esta página no existe' },
  notFoundText: {
    zh: '它可能已经移动了位置。试试顶部的搜索，或回到首页。',
    'zh-tw': '它可能已經移動了位置。試試頂部的搜尋，或回到首頁。',
    en: 'It may have moved. Try the search at the top, or go back to the home page.',
    ru: 'Возможно, она переехала. Попробуйте поиск сверху или вернитесь на главную.',
    ko: '페이지가 이동했을 수 있습니다. 위쪽 검색을 이용하거나 홈으로 돌아가세요.',
    ja: 'ページが移動した可能性があります。上部の検索を使うか、ホームに戻ってください。',
    de: 'Vielleicht wurde sie verschoben. Nutze die Suche oben oder kehre zur Startseite zurück.',
    fr: 'Elle a peut-être été déplacée. Essayez la recherche en haut ou revenez à la page d’accueil.',
    es: 'Puede que se haya movido. Prueba la búsqueda de arriba o vuelve a la página de inicio.',
  },
  notFoundHome: { zh: '回到首页', 'zh-tw': '回到首頁', en: 'Back to the home page', ru: 'На главную', ko: '홈으로', ja: 'ホームへ戻る', de: 'Zur Startseite', fr: 'Retour à l’accueil', es: 'Volver al inicio' },
}

/** The interface strings of one locale. */
export function uiOf(locale: Locale): Record<Message, string> {
  return Object.fromEntries((Object.keys(messages) as Message[]).map(key => [key, messages[key][locale]])) as Record<Message, string>
}
