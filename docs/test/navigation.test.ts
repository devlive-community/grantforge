// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { neighbours, pages, sectionOf, sections } from '@/lib/navigation'

describe('navigation', () => {
  it('lists every page once, bilingual and owned by its section', () => {
    const slugs = pages.map(page => page.slug)
    expect(new Set(slugs).size).toBe(slugs.length)
    for (const section of sections) {
      // A page keeps its URL when it moves between sections (the plug-in guide lives in the
      // plug-ins part under architecture/), so ownership is asserted through sectionOf.
      for (const page of section.groups.flatMap(group => group.pages)) {
        expect(sectionOf(page.slug)?.id, page.slug).toBe(section.id)
        expect(page.en.trim(), page.slug).not.toBe('')
      }
      expect(section.en.trim(), section.id).not.toBe('')
    }
  })

  it('finds the section of a page', () => {
    expect(sectionOf('guide/roles')?.id).toBe('guide')
    expect(sectionOf('nowhere')).toBeUndefined()
  })

  it('links each page to the ones around it, across sections', () => {
    expect(neighbours(pages[0]!.slug).previous).toBeUndefined()
    const lastOfStart = sections[0]!.groups.at(-1)!.pages.at(-1)!.slug
    expect(neighbours(lastOfStart).next?.slug).toBe(sections[1]!.groups[0]!.pages[0]!.slug)
    expect(neighbours(pages.at(-1)!.slug).next).toBeUndefined()
    expect(neighbours('nowhere')).toEqual({})
  })
})
