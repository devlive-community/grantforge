// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { components } from '@/api/schema'

type Unit = components['schemas']['OrgUnitResponse']

/** Departments in tree order with an indentation per level, for drop-downs; excluded subtrees are left out. */
export function orgOptions(units: readonly Unit[], excludeSubtreeOf?: string | null): { value: string; label: string }[] {
  const children = new Map<string | null, Unit[]>()
  for (const unit of units) {
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
