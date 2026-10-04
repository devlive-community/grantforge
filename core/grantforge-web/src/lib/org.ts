// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { components } from '@/api/schema'

type Unit = components['schemas']['OrgUnitResponse']

/**
 * Departments as a reader sees them: data permissions may hide a department's parent, so such a department becomes a
 * root, and depths count from the roots the reader sees.
 */
export function visibleTree(units: readonly Unit[]): Unit[] {
  const byId = new Map(units.map(unit => [unit.id, unit]))
  const parentOf = (unit: Unit) => unit.parentId ? byId.get(unit.parentId) : undefined
  return units.map(unit => {
    let depth = 0
    for (let parent = parentOf(unit); parent; parent = parentOf(parent)) depth++
    if (parentOf(unit)) return depth === unit.depth ? unit : { ...unit, depth }
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { parentId, ...root } = unit
    return { ...root, depth }
  })
}

/** Departments in tree order with an indentation per level, for drop-downs; excluded subtrees are left out. */
export function orgOptions(units: readonly Unit[], excludeSubtreeOf?: string | null): { value: string; label: string }[] {
  const children = new Map<string | null, Unit[]>()
  for (const unit of visibleTree(units)) {
    const parent = unit.parentId ?? null
    children.set(parent, [...children.get(parent) ?? [], unit])
  }
  const options: { value: string; label: string }[] = []
  const visit = (parentId: string | null) => {
    for (const unit of [...children.get(parentId) ?? []].sort((a, b) => a.sortOrder - b.sortOrder)) {
      if (unit.id === excludeSubtreeOf) continue
      options.push({ value: unit.id, label: `${'— '.repeat(unit.depth)}${unit.name}` })
      visit(unit.id)
    }
  }
  visit(null)
  return options
}
