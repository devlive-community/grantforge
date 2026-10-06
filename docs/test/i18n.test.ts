// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { LOCALES, LOCALE_LABELS, localeOf, pageHref, stripLocale, switchHref, uiOf } from '@/lib/i18n'

describe('i18n', () => {
  it('reads the language of every path, with or without a prefix', () => {
    expect(localeOf('guide/roles')).toBe('zh')
    expect(localeOf('/en/guide/roles/')).toBe('en')
    expect(localeOf('ru/guide/roles')).toBe('ru')
    expect(stripLocale('en/guide/roles')).toBe('guide/roles')
    expect(stripLocale('/ru/guide/roles/')).toBe('guide/roles/')
    expect(stripLocale('guide/roles')).toBe('guide/roles')
  })

  it('builds the link of a page in every language', () => {
    expect(pageHref('guide/roles', 'zh')).toBe('/guide/roles/')
    expect(pageHref('guide/roles', 'en')).toBe('/en/guide/roles/')
    expect(pageHref('guide/roles', 'ru')).toBe('/ru/guide/roles/')
    expect(pageHref('en/guide/roles', 'ru')).toBe('/ru/guide/roles/')
  })

  it('switches between any two languages, the home page included', () => {
    expect(switchHref('/en/guide/roles/', 'zh')).toBe('/guide/roles/')
    expect(switchHref('/en/guide/roles/', 'ru')).toBe('/ru/guide/roles/')
    expect(switchHref('/guide/roles/', 'ru')).toBe('/ru/guide/roles/')
    expect(switchHref('/', 'en')).toBe('/en/')
    expect(switchHref('/ru/', 'zh')).toBe('/')
    expect(switchHref('/ru', 'en')).toBe('/en/')
  })

  it('labels every language and translates every interface string', () => {
    for (const locale of LOCALES) {
      expect(LOCALE_LABELS[locale].trim(), locale).not.toBe('')
      for (const message of Object.values(uiOf(locale))) expect(message.trim(), locale).not.toBe('')
    }
    expect(uiOf('ru').search).toBe('Поиск по документации')
  })
})
