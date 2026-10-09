// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { components } from '@/api/schema'

export type BrowsePage = components['schemas']['BrowsePageResponse']
export type BrowseEntry = BrowsePage['entries'][number]

/** The directories from the root down to a directory, for the breadcrumbs: the root shows its whole path. */
export function crumbs(root: string, directory: string): { name: string; path: string }[] {
  const trail = [{ name: root, path: root }]
  if (directory === root || !directory.startsWith(root === '/' ? '/' : `${root}/`)) return trail
  let path = root === '/' ? '' : root
  for (const name of directory.slice(path.length + 1).split('/').filter(Boolean)) {
    path = `${path}/${name}`
    trail.push({ name, path })
  }
  return trail
}

/** The directory above, or nothing at the root, which browsing may not leave. */
export function parentOf(root: string, directory: string): string | undefined {
  if (directory === root) return undefined
  const slash = directory.lastIndexOf('/')
  const parent = slash <= 0 ? '/' : directory.slice(0, slash)
  return parent.length < root.length ? root : parent
}

/** Directories first, each group in the order the service listed it, which stays stable as pages are added. */
export function ordered(entries: readonly BrowseEntry[]): BrowseEntry[] {
  return [...entries.filter(entry => entry.directory), ...entries.filter(entry => !entry.directory)]
}

/** A size for people: bytes up to a kibibyte, then one decimal in the largest unit that fits. */
export function sizeLabel(bytes?: number | null): string {
  if (bytes === undefined || bytes === null) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  let value = bytes, unit = 0
  while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++ }
  return unit === 0 ? `${value} B` : `${value.toFixed(1)} ${units[unit]}`
}
