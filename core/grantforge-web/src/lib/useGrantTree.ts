// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { computed, ref, type Ref } from 'vue'
import { displayName, type Resource } from './catalog'

export const grantable = new Set<Resource['type']>(['MENU', 'PAGE', 'TAB', 'ACTION', 'API'])
export const MAX_GRANT_CHANGES = 1000

/** Shared catalog traversal and top-level folding for role and direct grants. */
export function useGrantTree(resources: Ref<Resource[]>, query: Ref<string>) {
  const collapsed = ref(new Set<string>())
  const children = computed(() => {
    const found = new Map<string | null, Resource[]>()
    for (const resource of resources.value) {
      const parent = resource.parentId ?? null
      found.set(parent, [...found.get(parent) ?? [], resource])
    }
    return found
  })
  // Derive batch scope from the full tree, independently of search, filtering and folding.
  const moduleResources = computed(() => {
    const found = new Map<string, Resource[]>()
    const visit = (id: string, items: Resource[]) => {
      for (const child of children.value.get(id) ?? []) {
        if (grantable.has(child.type)) items.push(child)
        visit(child.id, items)
      }
    }
    for (const root of children.value.get(null) ?? []) {
      if (root.type !== 'MODULE') continue
      const items: Resource[] = []
      visit(root.id, items); found.set(root.id, items)
    }
    return found
  })
  const rows = computed(() => {
    const ordered: Resource[] = [], searching = !!query.value.trim()
    const visit = (parent: string | null) => {
      for (const resource of [...children.value.get(parent) ?? []].sort((a, b) => a.sortOrder - b.sortOrder)) {
        ordered.push(resource)
        if (resource.parentId || searching || !collapsed.value.has(resource.id)) visit(resource.id)
      }
    }
    visit(null)
    const text = query.value.trim().toLowerCase()
    return ordered.filter(resource => !text || displayName(resource).toLowerCase().includes(text) || resource.code.toLowerCase().includes(text))
  })
  function toggleRoot(id: string) {
    const next = new Set(collapsed.value)
    if (next.has(id)) next.delete(id); else next.add(id)
    collapsed.value = next
  }
  function reset() { collapsed.value = new Set() }
  return { children, collapsed, moduleResources, rows, toggleRoot, reset }
}
