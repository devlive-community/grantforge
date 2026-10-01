// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { afterEach, describe, expect, it, vi } from 'vitest'
import { currentLocale, detectLocale, setLocale, translate } from './index'

describe('i18n', () => {
  afterEach(() => { setLocale('zh-CN') })

  it('prefers a saved choice, then the browser language', () => {
    expect(detectLocale('en-US', 'zh-CN')).toBe('en-US')
    expect(detectLocale(null, 'zh-TW')).toBe('zh-CN')
    expect(detectLocale('fr-FR', 'en-GB')).toBe('en-US')
    expect(detectLocale(null, undefined)).toBe('en-US')
  })

  it('switches, remembers and exposes the language', () => {
    setLocale('en-US')
    expect(currentLocale()).toBe('en-US')
    expect(document.documentElement.lang).toBe('en-US')
    expect(localStorage.getItem('GrantForgeLocale')).toBe('en-US')
    expect(translate('status.locked')).toBe('Locked')
    expect(translate('errors.status', { status: 502 })).toBe('Request failed (502)')
    setLocale('zh-CN')
    expect(translate('status.locked')).toBe('已锁定')
  })

  it('still switches when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota') })
    setLocale('en-US')
    expect(currentLocale()).toBe('en-US')
  })
})
