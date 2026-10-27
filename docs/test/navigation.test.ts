// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { LOCALES, pageHref } from '@/lib/i18n'
import { categories, categoryOf, descriptionOf, groupsOf, neighbours, pages, pagesOf, sections, sectionOf, titleOf, trailOf } from '@/lib/navigation'

describe('navigation', () => {
  it('lists every page once, bilingual and owned by its section', () => {
    const slugs = pages.map(page => page.slug)
    expect(new Set(slugs).size).toBe(slugs.length)
    for (const section of sections) {
      // Navigation owns each page once, even when a published URL stays after moving to another section.
      for (const page of pagesOf(section)) {
        expect(sections.filter(candidate => pagesOf(candidate).includes(page)), page.slug)
          .toEqual([section])
        expect(sectionOf(page.slug)?.id, page.slug).toBe(section.id)
        expect(page.en.trim(), page.slug).not.toBe('')
      }
      expect(section.en.trim(), section.id).not.toBe('')
    }
  })

  it('puts every section in exactly one named category', () => {
    expect(categories.map(category => category.id)).toEqual(['user', 'plugins', 'developer', 'releases'])
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
        expect(sectionOf(pagesOf(section)[0]!.slug)?.id, section.id).toBe(section.id)
      }
    }
    expect(categoryOf('/en/guide/roles/')?.id).toBe('user')
    expect(categoryOf('reference/api')?.id).toBe('developer')
    expect(categoryOf('changelog/rebuild')?.id).toBe('releases')
    expect(categoryOf('changelog/2026.1.0')?.id).toBe('releases')
    expect(categoryOf('nowhere')).toBeUndefined()
  })

  it('gives product plugins their own tab while preserving agent URLs in every language', () => {
    const catalog = categories.find(category => category.id === 'plugins')!
    const slugs = catalog.sections.flatMap(pagesOf).map(page => page.slug)
    expect(slugs).toContain('plugins/hdfs')
    expect(slugs).toContain('external/hdfs-agent')
    expect(slugs).toContain('plugins/zookeeper')
    expect(slugs).toContain('external/zookeeper-agent')
    expect(slugs).toContain('plugins/mysql')
    expect(slugs).toContain('external/mysql-agent')
    expect(slugs).toContain('plugins/hive')
    expect(slugs).toContain('external/hive-agent')
    expect(slugs).toContain('plugins/kafka')
    expect(slugs).toContain('external/kafka-agent')
    expect(slugs).toContain('plugins/hbase')
    expect(slugs).toContain('external/hbase-agent')
    expect(slugs).toContain('plugins/trino')
    expect(slugs).toContain('external/trino-agent')
    expect(slugs).toContain('plugins/yarn')
    expect(slugs).toContain('external/yarn-agent')
    expect(slugs).toContain('plugins/pulsar')
    expect(slugs).toContain('external/pulsar-agent')
    expect(slugs).toContain('plugins/solr')
    expect(slugs).toContain('external/solr-agent')
    expect(slugs).toContain('plugins/clickhouse')
    expect(slugs).toContain('external/clickhouse-agent')
    expect(slugs).toContain('plugins/postgresql')
    expect(slugs).toContain('external/postgresql-agent')
    expect(slugs).toContain('plugins/opensearch')
    expect(slugs).toContain('external/opensearch-agent')
    expect(slugs.some(slug => slug.includes('example'))).toBe(false)
    expect(categoryOf('develop/plugins')?.id).toBe('developer')
    expect(categoryOf('external/data-services')?.id).toBe('developer')
    for (const locale of LOCALES) {
      expect(categoryOf(pageHref('plugins/hdfs', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/hdfs-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/hdfs-agent', locale))?.id, locale).toBe('plugins-infrastructure')
      expect(categoryOf(pageHref('plugins/zookeeper', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/zookeeper-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/zookeeper-agent', locale))?.id, locale).toBe('plugins-infrastructure')
      expect(categoryOf(pageHref('plugins/mysql', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/mysql-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/mysql-agent', locale))?.id, locale).toBe('plugins-databases')
      expect(categoryOf(pageHref('plugins/hive', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/hive-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/hive-agent', locale))?.id, locale).toBe('plugins-query-engines')
      expect(categoryOf(pageHref('plugins/kafka', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/kafka-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/kafka-agent', locale))?.id, locale).toBe('plugins-messaging')
      expect(categoryOf(pageHref('plugins/hbase', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/hbase-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/hbase-agent', locale))?.id, locale).toBe('plugins-databases')
      expect(categoryOf(pageHref('plugins/trino', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/trino-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/trino-agent', locale))?.id, locale).toBe('plugins-query-engines')
      expect(categoryOf(pageHref('plugins/yarn', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/yarn-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/yarn-agent', locale))?.id, locale).toBe('plugins-infrastructure')
      expect(categoryOf(pageHref('plugins/pulsar', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/pulsar-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/pulsar-agent', locale))?.id, locale).toBe('plugins-messaging')
      expect(categoryOf(pageHref('plugins/solr', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/solr-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/solr-agent', locale))?.id, locale).toBe('plugins-search')
      expect(categoryOf(pageHref('plugins/clickhouse', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/clickhouse-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/clickhouse-agent', locale))?.id, locale).toBe('plugins-databases')
      expect(categoryOf(pageHref('plugins/postgresql', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/postgresql-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/postgresql-agent', locale))?.id, locale).toBe('plugins-databases')
      expect(categoryOf(pageHref('plugins/opensearch', locale))?.id, locale).toBe('plugins')
      expect(categoryOf(pageHref('external/opensearch-agent', locale))?.id, locale).toBe('plugins')
      expect(sectionOf(pageHref('external/opensearch-agent', locale))?.id, locale).toBe('plugins-search')
    }
  })

  it('names every page, group, section and category in Russian, Traditional Chinese, Korean, Japanese, German, French, Spanish, Portuguese and Italian', () => {
    for (const category of categories) {
      expect(category.ru?.trim(), category.id).toBeTruthy()
      expect(category.tw?.trim(), category.id).toBeTruthy()
      expect(category.ko?.trim(), category.id).toBeTruthy()
      expect(category.ja?.trim(), category.id).toBeTruthy()
      expect(category.de?.trim(), category.id).toBeTruthy()
      expect(category.fr?.trim(), category.id).toBeTruthy()
      expect(category.es?.trim(), category.id).toBeTruthy()
      expect(category.pt?.trim(), category.id).toBeTruthy()
      expect(category.it?.trim(), category.id).toBeTruthy()
      expect(category.descriptionRu?.trim(), category.id).toBeTruthy()
      expect(category.descriptionTw?.trim(), category.id).toBeTruthy()
      expect(category.descriptionKo?.trim(), category.id).toBeTruthy()
      expect(category.descriptionJa?.trim(), category.id).toBeTruthy()
      expect(category.descriptionDe?.trim(), category.id).toBeTruthy()
      expect(category.descriptionFr?.trim(), category.id).toBeTruthy()
      expect(category.descriptionEs?.trim(), category.id).toBeTruthy()
      expect(category.descriptionPt?.trim(), category.id).toBeTruthy()
      expect(category.descriptionIt?.trim(), category.id).toBeTruthy()
      for (const section of category.sections) {
        expect(section.ru?.trim(), section.id).toBeTruthy()
        expect(section.tw?.trim(), section.id).toBeTruthy()
        expect(section.ko?.trim(), section.id).toBeTruthy()
        expect(section.ja?.trim(), section.id).toBeTruthy()
        expect(section.de?.trim(), section.id).toBeTruthy()
        expect(section.fr?.trim(), section.id).toBeTruthy()
        expect(section.es?.trim(), section.id).toBeTruthy()
        expect(section.pt?.trim(), section.id).toBeTruthy()
        expect(section.it?.trim(), section.id).toBeTruthy()
        expect(section.descriptionRu?.trim(), section.id).toBeTruthy()
        expect(section.descriptionTw?.trim(), section.id).toBeTruthy()
        expect(section.descriptionKo?.trim(), section.id).toBeTruthy()
        expect(section.descriptionJa?.trim(), section.id).toBeTruthy()
        expect(section.descriptionDe?.trim(), section.id).toBeTruthy()
        expect(section.descriptionFr?.trim(), section.id).toBeTruthy()
        expect(section.descriptionEs?.trim(), section.id).toBeTruthy()
        expect(section.descriptionPt?.trim(), section.id).toBeTruthy()
        expect(section.descriptionIt?.trim(), section.id).toBeTruthy()
        for (const group of groupsOf(section)) {
          expect(group.ru?.trim(), group.title).toBeTruthy()
          expect(group.tw?.trim(), group.title).toBeTruthy()
          expect(group.ko?.trim(), group.title).toBeTruthy()
          expect(group.ja?.trim(), group.title).toBeTruthy()
          expect(group.de?.trim(), group.title).toBeTruthy()
          expect(group.fr?.trim(), group.title).toBeTruthy()
          expect(group.es?.trim(), group.title).toBeTruthy()
          expect(group.pt?.trim(), group.title).toBeTruthy()
          expect(group.it?.trim(), group.title).toBeTruthy()
        }
        for (const page of pagesOf(section)) {
          expect(page.ru?.trim(), page.slug).toBeTruthy()
          expect(page.tw?.trim(), page.slug).toBeTruthy()
          expect(page.ko?.trim(), page.slug).toBeTruthy()
          expect(page.ja?.trim(), page.slug).toBeTruthy()
          expect(page.de?.trim(), page.slug).toBeTruthy()
          expect(page.fr?.trim(), page.slug).toBeTruthy()
          expect(page.es?.trim(), page.slug).toBeTruthy()
          expect(page.pt?.trim(), page.slug).toBeTruthy()
          expect(page.it?.trim(), page.slug).toBeTruthy()
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
    expect(titleOf(guide, 'de')).toBe('Handbuch')
    expect(descriptionOf(guide, 'de')).toBe('Jede Funktion der Konsole, Menü für Menü.')
    expect(titleOf(guide, 'fr')).toBe('Guide d’utilisation')
    expect(descriptionOf(guide, 'fr')).toBe('Chaque fonction de la console, menu par menu.')
    expect(titleOf(guide, 'es')).toBe('Guía de uso')
    expect(descriptionOf(guide, 'es')).toBe('Cada función de la consola, menú a menú.')
    expect(titleOf(guide, 'pt-br')).toBe('Guia de uso')
    expect(descriptionOf(guide, 'pt-br')).toBe('Cada função do console, menu a menu.')
    expect(titleOf(guide, 'it')).toBe('Guida all’uso')
    expect(descriptionOf(guide, 'it')).toBe('Ogni funzione della console, menu per menu.')
    // A label nobody translated falls back to English, and a description to the Chinese or English text.
    expect(titleOf({ title: '标题', en: 'Title' }, 'ko')).toBe('Title')
    expect(descriptionOf({ description: '说明', descriptionEn: 'Description' }, 'ko')).toBe('Description')
    expect(titleOf({ title: '标题', en: 'Title' }, 'ja')).toBe('Title')
    expect(descriptionOf({ description: '说明', descriptionEn: 'Description' }, 'ja')).toBe('Description')
    expect(titleOf({ title: '标题', en: 'Title' }, 'de')).toBe('Title')
    expect(descriptionOf({ description: '说明', descriptionEn: 'Description' }, 'de')).toBe('Description')
    expect(titleOf({ title: '标题', en: 'Title' }, 'fr')).toBe('Title')
    expect(descriptionOf({ description: '说明', descriptionEn: 'Description' }, 'fr')).toBe('Description')
    expect(titleOf({ title: '标题', en: 'Title' }, 'es')).toBe('Title')
    expect(descriptionOf({ description: '说明', descriptionEn: 'Description' }, 'es')).toBe('Description')
    expect(titleOf({ title: '标题', en: 'Title' }, 'pt-br')).toBe('Title')
    expect(descriptionOf({ description: '说明', descriptionEn: 'Description' }, 'pt-br')).toBe('Description')
    expect(titleOf({ title: '标题', en: 'Title' }, 'it')).toBe('Title')
    expect(descriptionOf({ description: '说明', descriptionEn: 'Description' }, 'it')).toBe('Description')
  })

  it('sorts the plug-ins by the kind of system, each system a folded entry with its plug-in and agent', () => {
    const catalog = categories.find(category => category.id === 'plugins')!
    expect(catalog.sections.map(section => section.id)).toEqual(
      ['plugins-overview', 'plugins-databases', 'plugins-query-engines', 'plugins-messaging', 'plugins-search', 'plugins-infrastructure'])
    // The tab opens on the overview, which the trail names by the tab itself.
    expect(catalog.sections.flatMap(pagesOf)[0]!.slug).toBe('plugins/overview')
    expect(trailOf('/en/plugins/overview/').map(node => node.en)).toEqual(['Plug-ins'])
    expect(catalog.sections[0]!.groups).toEqual([])
    for (const section of catalog.sections) {
      for (const system of groupsOf(section).filter(group => group.folded)) {
        expect(system.pages.map(page => page.slug.split('/')[0]), system.en).toEqual(['plugins', 'external'])
        expect(system.groups, system.en).toBeUndefined()
        for (const page of system.pages) {
          for (const locale of LOCALES) expect(titleOf(page.short!, locale).trim(), `${page.slug} ${locale}`).not.toBe('')
        }
      }
    }
    expect(groupsOf(catalog.sections[1]!).filter(group => group.folded).map(group => group.en))
      .toEqual(['MySQL', 'PostgreSQL', 'ClickHouse', 'Apache HBase'])
    expect(trailOf('/en/plugins/mysql/').map(node => node.en)).toEqual(['Databases', 'Relational'])
    expect(trailOf('external/kafka-agent').map(node => node.en)).toEqual(['Messaging and streaming'])
    expect(trailOf('guide/roles').map(node => node.en)).toEqual(['User guide', 'Access control'])
    expect(trailOf('nowhere')).toEqual([])
  })

  it('finds the section of a page', () => {
    expect(sectionOf('guide/roles')?.id).toBe('guide')
    expect(sectionOf('nowhere')).toBeUndefined()
  })

  it('links each page to the ones around it, across sections', () => {
    expect(neighbours(pages[0]!.slug).previous).toBeUndefined()
    const lastOfStart = pagesOf(sections[0]!).at(-1)!.slug
    expect(neighbours(lastOfStart).next?.slug).toBe(pagesOf(sections[1]!)[0]!.slug)
    expect(neighbours(pages.at(-1)!.slug).next).toBeUndefined()
    expect(neighbours('nowhere')).toEqual({})
  })
})
