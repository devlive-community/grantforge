// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { components } from '@/api/schema'

export type Resource = components['schemas']['ResourceResponse']
export type ResourceType = Resource['type']

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
