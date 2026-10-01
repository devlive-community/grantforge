// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import manifest from './manifest.json'

/**
 * One resource of the console's permission manifest. The server creates every entry as a built-in resource of
 * the console application at start-up and links it to the API permissions (`apis`) and other entries
 * (`requires`) it needs; roles grant the entries.
 */
export interface ManifestEntry {
  code: string
  type: 'MODULE' | 'MENU' | 'PAGE' | 'TAB' | 'ACTION'
  name: string
  nameKey?: string
  route?: string
  apis?: string[]
  requires?: string[]
  children?: ManifestEntry[]
}

/** The manifest's top-level entries. */
export const manifestEntries: readonly ManifestEntry[] = manifest.resources as ManifestEntry[]

/** Every entry, parents before their children. */
export function flattenManifest(entries: readonly ManifestEntry[] = manifestEntries): ManifestEntry[] {
  return entries.flatMap(entry => [entry, ...flattenManifest(entry.children ?? [])])
}

/**
 * Console pages and the resource code that grants each one. Pages missing here, such as the dashboard, are
 * open to every signed-in user. Hiding a page is a convenience; the server still checks every API call.
 */
export const pageResources: Readonly<Record<string, string>> = Object.fromEntries(flattenManifest()
  .filter(entry => entry.type === 'PAGE' && entry.route).map(entry => [entry.route, entry.code]))

/** Returns the resource code that grants the page at `path`, or undefined when every signed-in user may open it. */
export function pageResource(path: string): string | undefined {
  return Object.hasOwn(pageResources, path) ? pageResources[path] : undefined
}
