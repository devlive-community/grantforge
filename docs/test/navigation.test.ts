// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { categories, categoryOf, descriptionOf, neighbours, pages, sections, sectionOf, titleOf } from '@/lib/navigation'

describe('navigation', () => {
  it('lists every page once, bilingual and owned by its section', () => {
    const slugs = pages.map(page => page.slug)
    expect(new Set(slugs).size).toBe(slugs.length)
    for (const section of sections) {
      // A page's URL path belongs to exactly one part: the slug prefix and the sidebar section agree.
      for (const page of section.groups.flatMap(group => group.pages)) {
        expect(page.slug.startsWith(`${section.id}/`), page.slug).toBe(true)
        expect(sectionOf(page.slug)?.id, page.slug).toBe(section.id)
        expect(page.en.trim(), page.slug).not.toBe('')
      }
      expect(section.en.trim(), section.id).not.toBe('')
    }
  })

  it('puts every section in exactly one named category', () => {
    expect(categories.map(category => category.id)).toEqual(['user', 'developer', 'releases'])
    expect(new Set(categories.map(category => category.id)).size).toBe(categories.length)
    expect(categories.flatMap(category => category.sections).map(section => section.id))
      .toEqual(sections.map(section => section.id))
    for (const category of categories) {
      expect(category.title.trim()).not.toBe('')
      expect(category.en.trim()).not.toBe('')
      expect(category.description.trim()).not.toBe('')
      expect(category.sections.length).toBeGreaterThan(0)
      for (const section of category.sections) {
        expect(sections).toContain(section)
        expect(sectionOf(section.groups[0]!.pages[0]!.slug)?.id, section.id).toBe(section.id)
      }
    }
    expect(categoryOf('/en/guide/roles/')?.id).toBe('user')
    expect(categoryOf('reference/api')?.id).toBe('developer')
    expect(categoryOf('changelog/rebuild')?.id).toBe('releases')
    expect(categoryOf('nowhere')).toBeUndefined()
  })

  it('names every page, group, section and category in Russian, Traditional Chinese, Korean and Japanese', () => {
    for (const category of categories) {
      expect(category.ru?.trim(), category.id).toBeTruthy()
      expect(category.tw?.trim(), category.id).toBeTruthy()
      expect(category.ko?.trim(), category.id).toBeTruthy()
      expect(category.ja?.trim(), category.id).toBeTruthy()
      expect(category.descriptionRu?.trim(), category.id).toBeTruthy()
      expect(category.descriptionTw?.trim(), category.id).toBeTruthy()
      expect(category.descriptionKo?.trim(), category.id).toBeTruthy()
      expect(category.descriptionJa?.trim(), category.id).toBeTruthy()
      for (const section of category.sections) {
        expect(section.ru?.trim(), section.id).toBeTruthy()
        expect(section.tw?.trim(), section.id).toBeTruthy()
        expect(section.ko?.trim(), section.id).toBeTruthy()
        expect(section.ja?.trim(), section.id).toBeTruthy()
        expect(section.descriptionRu?.trim(), section.id).toBeTruthy()
        expect(section.descriptionTw?.trim(), section.id).toBeTruthy()
        expect(section.descriptionKo?.trim(), section.id).toBeTruthy()
        expect(section.descriptionJa?.trim(), section.id).toBeTruthy()
        for (const group of section.groups) {
          expect(group.ru?.trim(), group.title).toBeTruthy()
          expect(group.tw?.trim(), group.title).toBeTruthy()
          expect(group.ko?.trim(), group.title).toBeTruthy()
          expect(group.ja?.trim(), group.title).toBeTruthy()
        }
        for (const page of section.groups.flatMap(group => group.pages)) {
          expect(page.ru?.trim(), page.slug).toBeTruthy()
          expect(page.tw?.trim(), page.slug).toBeTruthy()
          expect(page.ko?.trim(), page.slug).toBeTruthy()
          expect(page.ja?.trim(), page.slug).toBeTruthy()
        }
      }
    }
  })

  it('gives every label and description its own translation in each language', () => {
    const guide = sectionOf('guide/roles')!
    expect(titleOf(guide, 'zh')).toBe('使用指南')
    expect(titleOf(guide, 'zh-tw')).toBe('使用指南')
    expect(titleOf(guide, 'en')).toBe('User guide')
    expect(titleOf(guide, 'ru')).toBe('Руководство пользователя')
    expect(titleOf(guide, 'ko')).toBe('사용 가이드')
    expect(descriptionOf(guide, 'ko')).toBe('콘솔 메뉴별로 모든 기능의 사용 방법을 설명합니다.')
    expect(titleOf(guide, 'ja')).toBe('ユーザーガイド')
    expect(descriptionOf(guide, 'ja')).toBe('コンソールのメニューごとに、すべての機能の使い方を説明します。')
    // A label nobody translated falls back to English, and a description to the Chinese or English text.
    expect(titleOf({ title: '标题', en: 'Title' }, 'ko')).toBe('Title')
    expect(descriptionOf({ description: '说明', descriptionEn: 'Description' }, 'ko')).toBe('Description')
    expect(titleOf({ title: '标题', en: 'Title' }, 'ja')).toBe('Title')
    expect(descriptionOf({ description: '说明', descriptionEn: 'Description' }, 'ja')).toBe('Description')
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
