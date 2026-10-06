// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { LOCALES, LOCALE_LABELS, localeOf, pageHref, stripLocale, switchHref, uiOf } from '@/lib/i18n'

describe('i18n', () => {
  it('reads the language of every path, with or without a prefix', () => {
    expect(localeOf('guide/roles')).toBe('zh')
    expect(localeOf('zh-tw/guide/roles')).toBe('zh-tw')
    expect(localeOf('/en/guide/roles/')).toBe('en')
    expect(localeOf('ru/guide/roles')).toBe('ru')
    expect(localeOf('ko/guide/roles')).toBe('ko')
    expect(stripLocale('zh-tw/guide/roles')).toBe('guide/roles')
    expect(stripLocale('en/guide/roles')).toBe('guide/roles')
    expect(stripLocale('/ru/guide/roles/')).toBe('guide/roles/')
    expect(stripLocale('ko/guide/roles')).toBe('guide/roles')
    expect(stripLocale('guide/roles')).toBe('guide/roles')
  })

  it('builds the link of a page in every language', () => {
    expect(pageHref('guide/roles', 'zh')).toBe('/guide/roles/')
    expect(pageHref('guide/roles', 'zh-tw')).toBe('/zh-tw/guide/roles/')
    expect(pageHref('guide/roles', 'en')).toBe('/en/guide/roles/')
    expect(pageHref('guide/roles', 'ru')).toBe('/ru/guide/roles/')
    expect(pageHref('guide/roles', 'ko')).toBe('/ko/guide/roles/')
    expect(pageHref('zh-tw/guide/roles', 'ru')).toBe('/ru/guide/roles/')
  })

  it('switches between any two languages, the home page included', () => {
    expect(switchHref('/zh-tw/guide/roles/', 'zh')).toBe('/guide/roles/')
    expect(switchHref('/zh-tw/guide/roles/', 'ru')).toBe('/ru/guide/roles/')
    expect(switchHref('/guide/roles/', 'zh-tw')).toBe('/zh-tw/guide/roles/')
    expect(switchHref('/', 'zh-tw')).toBe('/zh-tw/')
    expect(switchHref('/zh-tw/', 'zh')).toBe('/')
    expect(switchHref('/zh-tw', 'en')).toBe('/en/')
    expect(switchHref('/en/guide/roles/', 'zh')).toBe('/guide/roles/')
    expect(switchHref('/en/guide/roles/', 'ru')).toBe('/ru/guide/roles/')
    expect(switchHref('/guide/roles/', 'ru')).toBe('/ru/guide/roles/')
    expect(switchHref('/', 'en')).toBe('/en/')
    expect(switchHref('/ru/', 'zh')).toBe('/')
    expect(switchHref('/ru', 'en')).toBe('/en/')
    expect(switchHref('/ko/guide/roles/', 'zh')).toBe('/guide/roles/')
    expect(switchHref('/ko/guide/roles/', 'en')).toBe('/en/guide/roles/')
    expect(switchHref('/guide/roles/', 'ko')).toBe('/ko/guide/roles/')
    expect(switchHref('/', 'ko')).toBe('/ko/')
    expect(switchHref('/ko/', 'zh-tw')).toBe('/zh-tw/')
  })

  it('labels every language and translates every interface string', () => {
    for (const locale of LOCALES) {
      expect(LOCALE_LABELS[locale].trim(), locale).not.toBe('')
      for (const message of Object.values(uiOf(locale))) expect(message.trim(), locale).not.toBe('')
    }
    expect(uiOf('zh-tw').search).toBe('搜尋文件')
    expect(uiOf('ru').search).toBe('Поиск по документации')
    expect(uiOf('ko').search).toBe('문서 검색')
  })
})
