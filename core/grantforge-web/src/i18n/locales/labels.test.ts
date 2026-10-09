// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { LOCALES } from '../index'
import { LOCALE_LABELS } from './labels'

describe('language labels', () => {
  it('names every language the console ships', () => {
    // The picker reads the label of each entry of LOCALES, so a language without one is a blank menu item.
    expect(Object.keys(LOCALE_LABELS).sort()).toEqual([...LOCALES].sort())
  })

  it('gives every language a name of its own', () => {
    const labels = LOCALES.map(locale => LOCALE_LABELS[locale])
    expect(labels.every(label => label.trim().length > 0)).toBe(true)
    expect(new Set(labels).size).toBe(labels.length)
  })
})
