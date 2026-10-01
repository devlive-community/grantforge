// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { flattenManifest, manifestEntries, pageResource, pageResources } from './manifest'
import router from '@/router'

describe('page resources', () => {
  it('names the resource that grants a managed page', () => expect(pageResource('/admin/users')).toBe('system.user'))
  it('leaves open pages and unknown paths without a resource', () => {
    expect(pageResource('/dashboard')).toBeUndefined()
    expect(pageResource('toString')).toBeUndefined()
  })

  it('derives a page resource for every page of the manifest', () => {
    expect(pageResources['/platform/apis']).toBe('platform.api')
    expect(Object.keys(pageResources)).toHaveLength(flattenManifest().filter(entry => entry.type === 'PAGE').length)
  })

  it('declares unique codes, routes that exist and buttons only on pages', () => {
    const entries = flattenManifest(manifestEntries)
    expect(new Set(entries.map(entry => entry.code)).size).toBe(entries.length)
    for (const entry of entries.filter(item => item.route)) expect(router.resolve(entry.route ?? '').name, entry.code).toBeTruthy()
    for (const page of entries.filter(item => item.type === 'PAGE')) {
      for (const child of page.children ?? []) expect(child.code.startsWith(`${page.code}.btn.`), child.code).toBe(true)
    }
  })
})
