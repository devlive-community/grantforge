// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { components } from '@/api/schema'

export type Resource = components['schemas']['ResourceResponse']
export type ResourceType = Resource['type']

/** Message keys of the type names; literal, so the message checker sees every one in use. */
export const resourceTypeKeys = { MODULE: 'catalog.typeModule', MENU: 'catalog.typeMenu', PAGE: 'catalog.typePage', TAB: 'catalog.typeTab',
  ACTION: 'catalog.typeAction', API: 'catalog.typeApi', DATA_ENTITY: 'catalog.typeDataEntity', FIELD: 'catalog.typeField' } as const

/** Every resource type in display order. */
export const resourceTypes: readonly ResourceType[] = ['MODULE', 'MENU', 'PAGE', 'TAB', 'ACTION', 'API', 'DATA_ENTITY', 'FIELD']

/** Where each type may sit, mirroring the server's ResourceType.allowsParent; `null` is the top level. */
const parents: Readonly<Record<ResourceType, readonly (ResourceType | null)[]>> = {
  MODULE: [null, 'MODULE'],
  MENU: [null, 'MODULE', 'MENU'],
  PAGE: [null, 'MODULE', 'MENU'],
  TAB: ['PAGE', 'TAB'],
  ACTION: ['PAGE', 'TAB'],
  API: [null, 'MODULE'],
  DATA_ENTITY: [null, 'MODULE'],
  FIELD: ['DATA_ENTITY'],
}

/** Whether a resource of `type` may sit below a parent of `parent` type (or at the top level). */
export function allowsParent(type: ResourceType, parent: ResourceType | null): boolean {
  return parents[type].includes(parent)
}

/** The types that may sit below a parent of `parent` type, or at the top level for `null`. */
export function childTypes(parent: ResourceType | null): ResourceType[] {
  return resourceTypes.filter(type => allowsParent(type, parent))
}

/** Whether resources of the type have a console route (menus, pages and tabs). */
export function hasRoute(type: ResourceType): boolean {
  return type === 'MENU' || type === 'PAGE' || type === 'TAB'
}

/** Whether users without the permission see resources of the type hidden or disabled (console elements). */
export function hasDenyMode(type: ResourceType): boolean {
  return hasRoute(type) || type === 'ACTION'
}

/** Whether `ancestor` is `id` or one of its ancestors in the tree. */
export function isWithin(resources: readonly Resource[], id: string, ancestor: string): boolean {
  const byId = new Map(resources.map(resource => [resource.id, resource]))
  for (let current = byId.get(id); current; current = current.parentId ? byId.get(current.parentId) : undefined) {
    if (current.id === ancestor) return true
  }
  return false
}

/** Types that may depend on others, mirroring the server's ResourceDependency.DEPENDENTS. */
export const dependentTypes: readonly ResourceType[] = ['MENU', 'PAGE', 'TAB', 'ACTION']

/** Types that may be depended on, mirroring the server's ResourceDependency.TARGETS. */
export const targetTypes: readonly ResourceType[] = ['API', 'PAGE', 'TAB', 'ACTION']

/** A dependency between two resources, as the graph needs it. */
export interface Edge { resourceId: string; dependsOnId: string; kind: 'REQUIRED' | 'OPTIONAL' }

/** A node of a dependency drawing: its column (negative: what needs the root, positive: what it needs) and row. */
export interface PlacedNode { id: string; column: number; row: number }

/**
 * Lays out everything around `root` in columns: what it needs (transitively) to the right by distance, what needs
 * it to the left, the root in column 0. Each resource appears once, at its shortest distance.
 */
export function layoutDependencies(root: string, edges: readonly Edge[]): PlacedNode[] {
  const placed = new Map<string, number>([[root, 0]])
  const walk = (direction: 1 | -1) => {
    let frontier = [root]
    for (let column = direction; frontier.length; column += direction) {
      const next: string[] = []
      for (const id of frontier) {
        for (const edge of edges) {
          const [from, to] = direction === 1 ? [edge.resourceId, edge.dependsOnId] : [edge.dependsOnId, edge.resourceId]
          if (from === id && !placed.has(to)) { placed.set(to, column); next.push(to) }
        }
      }
      frontier = next
    }
  }
  walk(1)
  walk(-1)
  const rows = new Map<number, number>()
  return [...placed].map(([id, column]) => {
    const row = rows.get(column) ?? 0
    rows.set(column, row + 1)
    return { id, column, row }
  })
}
