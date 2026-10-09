// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Policy, ServiceType } from '@/lib/policy'

const level = (name: string, label: string, extra: Partial<ServiceType['resources'][number]> = {}) => ({ name, label,
  lookupSupported: false, matcher: 'WILDCARD' as const, excludesSupported: true, recursiveSupported: false, validLeaf: false,
  accessTypes: [] as string[], browseSupported: false, ...extra })

/** A warehouse type: databases of tables of columns, and paths; columns can be masked, tables filtered. */
export const warehouse: ServiceType = {
  name: 'warehouse', label: 'Warehouse', configFields: [],
  resources: [
    level('database', 'Database', { validLeaf: true, lookupSupported: true, excludesSupported: false }),
    level('table', 'Table', { parent: 'database', validLeaf: true }),
    level('column', 'Column', { parent: 'table', accessTypes: ['select'] }),
    level('path', 'Path', { matcher: 'PATH', recursiveSupported: true, browseSupported: true }),
  ],
  accessTypes: [{ name: 'select', label: 'Select', impliedGrants: [] }, { name: 'update', label: 'Update', impliedGrants: [] }],
  policyTypes: ['ACCESS', 'DATA_MASK', 'ROW_FILTER'],
  maskTypes: [{ name: 'redact', label: 'Redact' }, { name: 'custom', label: 'Custom', transformer: '{expr}' }],
  maskableResources: ['column'], filterableResources: ['table'],
  conditions: [{ name: 'ip-range', label: 'Client addresses' }],
}

/** An access policy on database sales, table orders: alice may select, ops may not update. */
export const salesPolicy: Policy = {
  id: '11', serviceId: '7', name: 'sales', description: 'sales team', type: 'ACCESS', priority: 'OVERRIDE', enabled: true,
  labels: ['pii'], version: 3, updatedAt: '2026-10-01T00:00:00Z',
  document: {
    resources: { table: { values: ['orders'], excludes: false, recursive: false }, database: { values: ['sales'], excludes: false, recursive: false } },
    allow: [{ users: ['alice'], groups: [], roles: [], accessTypes: ['select'], conditions: [{ type: 'ip-range', values: ['10.0.0.0/8'] }] }],
    allowExceptions: [],
    deny: [{ users: [], groups: ['ops'], roles: [], accessTypes: ['update'], conditions: [] }],
    denyExceptions: [],
    validity: [{ from: '2026-01-01T00:00:00Z' }],
  },
}
