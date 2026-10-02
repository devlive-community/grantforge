// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { components } from '@/api/schema'

export type ServiceType = components['schemas']['ServiceTypeResponse']
export type Level = ServiceType['resources'][number]
export type AccessType = ServiceType['accessTypes'][number]
export type PolicyKind = components['schemas']['PolicyResponse']['type']
export type Policy = components['schemas']['PolicyResponse']
export type PolicyRequest = components['schemas']['PolicyRequest']
type Item = components['schemas']['PolicyItemSpec']
type Document = components['schemas']['PolicyDocument']

/** One resource level of a policy being edited. */
export interface LevelDraft { name: string; values: string[]; excludes: boolean; recursive: boolean }
/** One policy item being edited; conditions by condition name. */
export interface ItemDraft {
  users: string[]; groups: string[]; roles: string[]; accessTypes: string[]; conditions: Record<string, string[]>
  maskType: string; maskValue: string; rowFilter: string
}
/** A validity period being edited, as `datetime-local` values in the browser's time zone. */
export interface PeriodDraft { from: string; until: string }
export const ITEM_LISTS = ['allow', 'allowExceptions', 'deny', 'denyExceptions'] as const
export type ItemList = typeof ITEM_LISTS[number]
/** A policy being edited. Levels run from the top level down. */
export interface PolicyDraft {
  name: string; description: string; enabled: boolean; override: boolean; labels: string[]; levels: LevelDraft[]
  allow: ItemDraft[]; allowExceptions: ItemDraft[]; deny: ItemDraft[]; denyExceptions: ItemDraft[]; validity: PeriodDraft[]
}

/** The levels directly below one, or the top levels. */
export function childrenOf(type: ServiceType, parent?: string): Level[] {
  return type.resources.filter(level => (level.parent ?? undefined) === parent)
}
/** Whether a policy may end at a level: it has no levels below or says it is a valid end. */
export function canEndAt(type: ServiceType, level: Level): boolean {
  return level.validLeaf || childrenOf(type, level.name).length === 0
}
/** The access types items may name when a policy ends at a level. */
export function accessTypesAt(type: ServiceType, levelName?: string): AccessType[] {
  const level = type.resources.find(candidate => candidate.name === levelName)
  return level?.accessTypes.length ? type.accessTypes.filter(access => level.accessTypes.includes(access.name)) : type.accessTypes
}
/** The levels a kind of policy may end at; every level for access policies. */
export function endsFor(type: ServiceType, kind: PolicyKind): string[] {
  if (kind === 'DATA_MASK') return type.maskableResources
  if (kind === 'ROW_FILTER') return type.filterableResources
  return type.resources.map(level => level.name)
}

export function emptyItem(): ItemDraft {
  return { users: [], groups: [], roles: [], accessTypes: [], conditions: {}, maskType: '', maskValue: '', rowFilter: '' }
}
function level(name: string): LevelDraft { return { name, values: [], excludes: false, recursive: false } }

/** A new policy: in use, at the first top level, with one empty item. */
export function emptyDraft(type: ServiceType): PolicyDraft {
  const top = childrenOf(type)[0]
  return { name: '', description: '', enabled: true, override: false, labels: [], levels: top ? [level(top.name)] : [],
    allow: [emptyItem()], allowExceptions: [], deny: [], denyExceptions: [], validity: [] }
}

/** Starts the chain again at a top level, or cuts it below a level and continues with another. */
export function chooseLevel(draft: PolicyDraft, depth: number, name: string): LevelDraft[] {
  const kept = draft.levels.slice(0, depth)
  return name ? [...kept, draft.levels[depth]?.name === name ? draft.levels[depth] : level(name)] : kept
}

/** Formats an instant for a `datetime-local` input, in the browser's time zone. */
export function localInput(instant?: string): string {
  if (!instant) return ''
  const date = new Date(instant), pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
function instant(local: string): string | undefined { return local ? new Date(local).toISOString() : undefined }

function itemDraft(item: Item): ItemDraft {
  return { users: [...item.users], groups: [...item.groups], roles: [...item.roles], accessTypes: [...item.accessTypes],
    conditions: Object.fromEntries(item.conditions.map(condition => [condition.type, [...condition.values]])),
    maskType: item.maskType ?? '', maskValue: item.maskValue ?? '', rowFilter: item.rowFilter ?? '' }
}

/** Turns a stored policy into a draft, its levels ordered from the top level down. */
export function draftOf(policy: Policy, type: ServiceType): PolicyDraft {
  const levels: LevelDraft[] = []
  let parent: string | undefined
  for (;;) {
    const next = childrenOf(type, parent).find(candidate => candidate.name in policy.document.resources)
    const values = next && policy.document.resources[next.name]
    if (!next || !values) break
    levels.push({ name: next.name, values: [...values.values], excludes: values.excludes, recursive: values.recursive })
    parent = next.name
  }
  const { document } = policy
  return { name: policy.name, description: policy.description ?? '', enabled: policy.enabled, override: policy.priority === 'OVERRIDE',
    labels: [...policy.labels], levels, allow: document.allow.map(itemDraft), allowExceptions: document.allowExceptions.map(itemDraft),
    deny: document.deny.map(itemDraft), denyExceptions: document.denyExceptions.map(itemDraft),
    validity: document.validity.map(period => ({ from: localInput(period.from), until: localInput(period.until) })) }
}

function itemOf(item: ItemDraft, kind: PolicyKind): Item {
  const conditions = Object.entries(item.conditions).filter(([, values]) => values.length).map(([type, values]) => ({ type, values }))
  return { users: item.users, groups: item.groups, roles: item.roles, accessTypes: item.accessTypes, conditions,
    maskType: kind === 'DATA_MASK' && item.maskType ? item.maskType : undefined,
    maskValue: kind === 'DATA_MASK' && item.maskValue.trim() ? item.maskValue.trim() : undefined,
    rowFilter: kind === 'ROW_FILTER' && item.rowFilter.trim() ? item.rowFilter.trim() : undefined }
}

/** The request saving a draft; masking and filtering policies only keep their items. */
export function requestOf(draft: PolicyDraft, kind: PolicyKind, version?: number): PolicyRequest {
  const items = (list: ItemList) => kind === 'ACCESS' || list === 'allow' ? draft[list].map(item => itemOf(item, kind)) : []
  const document: Document = {
    resources: Object.fromEntries(draft.levels.map(entry => [entry.name, { values: entry.values, excludes: entry.excludes, recursive: entry.recursive }])),
    allow: items('allow'), allowExceptions: items('allowExceptions'), deny: items('deny'), denyExceptions: items('denyExceptions'),
    validity: draft.validity.filter(period => period.from || period.until)
      .map(period => ({ from: instant(period.from), until: instant(period.until) })),
  }
  return { type: kind, name: draft.name.trim(), description: draft.description.trim() || undefined, enabled: draft.enabled,
    priority: draft.override ? 'OVERRIDE' : 'NORMAL', labels: draft.labels, document, version }
}

/** What a policy covers, such as `database: sales, hr / table: *`; excluded values are marked with `≠`. */
export function coverage(policy: Policy, type?: ServiceType): string {
  const order = type?.resources.map(entry => entry.name) ?? []
  return Object.entries(policy.document.resources)
    .sort(([first], [second]) => order.indexOf(first) - order.indexOf(second))
    .map(([name, values]) => `${type?.resources.find(entry => entry.name === name)?.label ?? name}: ${values.excludes ? '≠ ' : ''}${values.values.join(', ')}${values.recursive ? ' …' : ''}`)
    .join(' / ')
}
