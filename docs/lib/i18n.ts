// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * The documentation languages. Chinese pages keep their historic paths; the others sit under a prefix. Simplified
 * Chinese is the source of every translation and keeps the unprefixed URLs.
 */
export type Locale = 'zh' | 'zh-tw' | 'en' | 'ru'

/** The path segment of every page of a locale. Chinese has none, so its URLs never change. */
const PREFIX: Record<Locale, string> = { zh: '', 'zh-tw': 'zh-tw', en: 'en', ru: 'ru' }

/** The path segment every English page sits under. */
export const ENGLISH_PREFIX = PREFIX.en

/** The path segment every Russian page sits under. */
export const RUSSIAN_PREFIX = PREFIX.ru

/** The path segment every Traditional Chinese page sits under. */
export const TRADITIONAL_PREFIX = PREFIX['zh-tw']

/** Every language of the site, in the order of the switcher. */
export const LOCALES: readonly Locale[] = ['zh', 'zh-tw', 'en', 'ru']

/** The label of each language in the switcher. */
export const LOCALE_LABELS: Record<Locale, string> = { zh: '中文', 'zh-tw': '繁體', en: 'EN', ru: 'RU' }

/** The BCP 47 tag of each language, for <html lang> and the switcher's links. */
export const LOCALE_HREFLANG: Record<Locale, string> = { zh: 'zh-CN', 'zh-tw': 'zh-TW', en: 'en', ru: 'ru' }

/** The path prefix a locale lives under ('' for Chinese, 'en' and 'ru' for the rest). */
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
  search: { zh: '搜索文档', 'zh-tw': '搜尋文件', en: 'Search docs', ru: 'Поиск по документации' },
  searchPlaceholder: {
    zh: '搜索标题与正文，例如：数据权限',
    'zh-tw': '搜尋標題與內文，例如：資料權限',
    en: 'Search titles and pages, e.g. data permissions',
    ru: 'Поиск по заголовкам и тексту, например: права на данные',
  },
  searchAria: { zh: '搜索内容', 'zh-tw': '搜尋內容', en: 'Search', ru: 'Поиск' },
  noResults: { zh: '没有找到相关内容', 'zh-tw': '沒有找到相關內容', en: 'Nothing matches this query', ru: 'Ничего не найдено' },
  onThisPage: { zh: '本页目录', 'zh-tw': '本頁目錄', en: 'On this page', ru: 'На этой странице' },
  previous: { zh: '上一篇', 'zh-tw': '上一篇', en: 'Previous', ru: 'Предыдущая' },
  next: { zh: '下一篇', 'zh-tw': '下一篇', en: 'Next', ru: 'Следующая' },
  editPage: { zh: '在 GitHub 上编辑此页', 'zh-tw': '在 GitHub 上編輯此頁', en: 'Edit this page on GitHub', ru: 'Редактировать эту страницу на GitHub' },
  untranslated: {
    zh: '此页面还没有英文翻译，先显示中文原文。',
    'zh-tw': '此頁面還沒有繁體翻譯，先顯示簡體原文。',
    en: 'This page is not translated into English yet; the Chinese original is shown.',
    ru: 'Эта страница ещё не переведена на русский, показан китайский оригинал.',
  },
  navAria: { zh: '文档分类', 'zh-tw': '文件分類', en: 'Documentation sections', ru: 'Разделы документации' },
  languageAria: { zh: '语言', 'zh-tw': '語言', en: 'Language', ru: 'Язык' },
  notTranslatedYet: { zh: '尚未翻译', 'zh-tw': '尚未翻譯', en: 'Not translated yet', ru: 'Ещё не переведено' },
  theme: { zh: '切换深色/浅色主题', 'zh-tw': '切換深色/淺色主題', en: 'Switch the dark or light theme', ru: 'Переключить тёмную или светлую тему' },
  notFoundTitle: { zh: '没有这一页', 'zh-tw': '沒有這一頁', en: 'This page does not exist', ru: 'Такой страницы нет' },
  notFoundText: {
    zh: '它可能已经移动了位置。试试顶部的搜索，或回到首页。',
    'zh-tw': '它可能已經移動了位置。試試頂部的搜尋，或回到首頁。',
    en: 'It may have moved. Try the search at the top, or go back to the home page.',
    ru: 'Возможно, она переехала. Попробуйте поиск сверху или вернитесь на главную.',
  },
  notFoundHome: { zh: '回到首页', 'zh-tw': '回到首頁', en: 'Back to the home page', ru: 'На главную' },
}

/** The interface strings of one locale. */
export function uiOf(locale: Locale): Record<Message, string> {
  return Object.fromEntries((Object.keys(messages) as Message[]).map(key => [key, messages[key][locale]])) as Record<Message, string>
}
