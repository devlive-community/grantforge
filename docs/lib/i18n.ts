// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/** The documentation languages. Chinese pages keep their historic paths; the others sit under a prefix. */
export type Locale = 'zh' | 'en' | 'ru'

/** The path segment of every page of a locale. Chinese has none, so its URLs never change. */
const PREFIX: Record<Locale, string> = { zh: '', en: 'en', ru: 'ru' }

/** The path segment every English page sits under. */
export const ENGLISH_PREFIX = PREFIX.en

/** The path segment every Russian page sits under. */
export const RUSSIAN_PREFIX = PREFIX.ru

/** Every language of the site, in the order of the switcher. */
export const LOCALES: readonly Locale[] = ['zh', 'en', 'ru']

/** The label of each language in the switcher. */
export const LOCALE_LABELS: Record<Locale, string> = { zh: '中文', en: 'EN', ru: 'RU' }

/** The hreflang value of each language. */
export const LOCALE_HREFLANG: Record<Locale, string> = { zh: 'zh-CN', en: 'en', ru: 'ru' }

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

const messages: Record<Message, Record<Locale, string>> = {
  search: { zh: '搜索文档', en: 'Search docs', ru: 'Поиск по документации' },
  searchPlaceholder: {
    zh: '搜索标题与正文，例如：数据权限',
    en: 'Search titles and pages, e.g. data permissions',
    ru: 'Поиск по заголовкам и тексту, например: права на данные',
  },
  searchAria: { zh: '搜索内容', en: 'Search', ru: 'Поиск' },
  noResults: { zh: '没有找到相关内容', en: 'Nothing matches this query', ru: 'Ничего не найдено' },
  onThisPage: { zh: '本页目录', en: 'On this page', ru: 'На этой странице' },
  previous: { zh: '上一篇', en: 'Previous', ru: 'Предыдущая' },
  next: { zh: '下一篇', en: 'Next', ru: 'Следующая' },
  editPage: { zh: '在 GitHub 上编辑此页', en: 'Edit this page on GitHub', ru: 'Редактировать эту страницу на GitHub' },
  untranslated: {
    zh: '此页面还没有英文翻译，先显示中文原文。',
    en: 'This page is not translated into English yet; the Chinese original is shown.',
    ru: 'Эта страница ещё не переведена на русский, показан китайский оригинал.',
  },
  navAria: { zh: '文档分类', en: 'Documentation sections', ru: 'Разделы документации' },
  languageAria: { zh: '语言', en: 'Language', ru: 'Язык' },
  notTranslatedYet: { zh: '尚未翻译', en: 'Not translated yet', ru: 'Ещё не переведено' },
}

/** The interface strings of one locale. */
export function uiOf(locale: Locale): Record<Message, string> {
  return Object.fromEntries((Object.keys(messages) as Message[]).map(key => [key, messages[key][locale]])) as Record<Message, string>
}
