// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/** The documentation languages. Chinese pages keep their historic paths; English lives under /en/. */
export type Locale = 'zh' | 'en'

/** The path segment every English page sits under. */
export const ENGLISH_PREFIX = 'en'

/** The browser storage key of the language the visitor picked explicitly. */
export const LOCALE_STORAGE_KEY = 'GrantForgeDocsLocale'

/** Reads the locale of a navigation slug or a pathname ('en/guide/roles' or '/en/guide/roles/' -> 'en'). */
export function localeOf(slug: string): Locale {
  const first = slug.split('/').find(part => part !== '')
  return first === ENGLISH_PREFIX ? 'en' : 'zh'
}

/** Removes the locale prefix from a slug or pathname ('en/guide/roles' -> 'guide/roles'). */
export function stripLocale(slug: string): string {
  return localeOf(slug) === 'en' ? slug.slice(slug.indexOf(ENGLISH_PREFIX) + ENGLISH_PREFIX.length + 1) : slug
}

/** The href of a page in one locale ('guide/roles' + 'en' -> '/en/guide/roles/'). */
export function pageHref(slug: string, locale: Locale): string {
  const clean = `/${stripLocale(slug).replace(/^\/+|\/+$/g, '')}/`
  return locale === 'en' ? `/${ENGLISH_PREFIX}${clean}` : clean
}

/** The counterpart href of the current pathname in the other locale, home page included. */
export function switchHref(pathname: string, locale: Locale): string {
  const stripped = pathname === `/${ENGLISH_PREFIX}` ? '/' : pathname.replace(/^\/en\/?/, '/')
  if (locale === 'en') return stripped === '/' ? '/en/' : `/${ENGLISH_PREFIX}${stripped}`
  return stripped
}

/** One interface string of the site shell, in both languages. */
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
  search: { zh: '搜索文档', en: 'Search docs' },
  searchPlaceholder: { zh: '搜索标题与正文，例如：数据权限', en: 'Search titles and pages, e.g. data permissions' },
  searchAria: { zh: '搜索内容', en: 'Search' },
  noResults: { zh: '没有找到相关内容', en: 'Nothing matches this query' },
  onThisPage: { zh: '本页目录', en: 'On this page' },
  previous: { zh: '上一篇', en: 'Previous' },
  next: { zh: '下一篇', en: 'Next' },
  editPage: { zh: '在 GitHub 上编辑此页', en: 'Edit this page on GitHub' },
  untranslated: {
    zh: '此页面还没有英文翻译，先显示中文原文。',
    en: 'This page is not translated into English yet; the Chinese original is shown.',
  },
  navAria: { zh: '文档分类', en: 'Documentation sections' },
  languageAria: { zh: '语言', en: 'Language' },
  notTranslatedYet: { zh: '尚未翻译', en: 'Not translated yet' },
}

/** The interface strings of one locale. */
export function uiOf(locale: Locale): Record<Message, string> {
  return Object.fromEntries((Object.keys(messages) as Message[]).map(key => [key, messages[key][locale]])) as Record<Message, string>
}
