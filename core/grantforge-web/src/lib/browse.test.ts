// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import { crumbs, ordered, parentOf, sizeLabel, type BrowseEntry } from './browse'

const entry = (name: string, directory: boolean): BrowseEntry => ({ name, value: `/${name}`, directory })

describe('browsing', () => {
  it('builds the breadcrumbs from the root down', () => {
    expect(crumbs('/', '/')).toEqual([{ name: '/', path: '/' }])
    expect(crumbs('/', '/user/alice')).toEqual([{ name: '/', path: '/' }, { name: 'user', path: '/user' }, { name: 'alice', path: '/user/alice' }])
    expect(crumbs('/data', '/data/sales/2026')).toEqual([{ name: '/data', path: '/data' }, { name: 'sales', path: '/data/sales' },
      { name: '2026', path: '/data/sales/2026' }])
    // A directory outside the root, which the server never returns, shows only the root.
    expect(crumbs('/data', '/database')).toEqual([{ name: '/data', path: '/data' }])
  })

  it('goes up but never above the root', () => {
    expect(parentOf('/', '/')).toBeUndefined()
    expect(parentOf('/', '/user')).toBe('/')
    expect(parentOf('/', '/user/alice')).toBe('/user')
    expect(parentOf('/data', '/data')).toBeUndefined()
    expect(parentOf('/data', '/data/sales')).toBe('/data')
  })

  it('puts directories first and keeps the listed order', () => {
    expect(ordered([entry('b.txt', false), entry('x', true), entry('a.txt', false), entry('c', true)]).map(item => item.name))
      .toEqual(['x', 'c', 'b.txt', 'a.txt'])
  })

  it('writes sizes for people', () => {
    expect(sizeLabel(undefined)).toBe('—')
    expect(sizeLabel(null)).toBe('—')
    expect(sizeLabel(0)).toBe('0 B')
    expect(sizeLabel(1023)).toBe('1023 B')
    expect(sizeLabel(1536)).toBe('1.5 KB')
    expect(sizeLabel(5 * 1024 ** 3)).toBe('5.0 GB')
  })
})
