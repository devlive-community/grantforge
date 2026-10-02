// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { components } from '@/api/schema'
import { localInput } from '@/lib/policy'

export type DataEntity = components['schemas']['DataEntity']
export type DataEntityField = components['schemas']['DataEntityField']
export type DataVariable = components['schemas']['DataVariable']

/** A comparison being edited; values are kept as the inputs hold them. */
export interface ComparisonNode {
  type: 'comparison'
  field: string
  op: string
  /** Set when the value comes from the reader, such as their departments. */
  variable: string
  value: string
  values: string[]
}
/** A group of conditions being edited: all of them or any of them, optionally negated. */
export interface GroupNode { type: 'group'; join: 'and' | 'or'; negate: boolean; children: ConditionNode[] }
export type ConditionNode = GroupNode | ComparisonNode
type Json = null | boolean | number | string | Json[] | { [key: string]: Json }

/** Groups nest at most this deep, below the server's limit of five. */
export const MAX_GROUP_DEPTH = 4
const LIST_OPERATORS = new Set(['in', 'not_in'])
const VALUELESS_OPERATORS = new Set(['is_null', 'not_null'])

export function takesList(op: string): boolean { return LIST_OPERATORS.has(op) }
export function takesValue(op: string): boolean { return !VALUELESS_OPERATORS.has(op) }

export function emptyGroup(): GroupNode { return { type: 'group', join: 'and', negate: false, children: [] } }
export function emptyComparison(field?: DataEntityField): ComparisonNode {
  return { type: 'comparison', field: field?.code ?? '', op: field?.operators[0] ?? 'eq', variable: '', value: '', values: [] }
}

/** The variables a comparison may use: of the field's kind, lists for in and not in. */
export function variablesFor(variables: DataVariable[], field: DataEntityField | undefined, op: string): DataVariable[] {
  return field ? variables.filter(variable => variable.type === field.type && variable.list === takesList(op)) : []
}

function literal(text: string, field: DataEntityField | undefined): Json {
  if (field?.type === 'NUMBER') return text.trim() === '' ? text : Number(text)
  if (field?.type === 'BOOLEAN') return text === 'true'
  if (field?.type === 'TIME') return text ? new Date(text).toISOString() : text
  return text
}

/** Writes a condition being edited in the grammar the server reads. */
export function toCondition(group: GroupNode, fields: DataEntityField[]): Json {
  const write = (node: ConditionNode): Json => {
    if (node.type === 'group') {
      const joined: Json = { [node.join]: node.children.map(write) }
      return node.negate ? { not: joined } : joined
    }
    const field = fields.find(candidate => candidate.code === node.field)
    const comparison: { [key: string]: Json } = { field: node.field, op: node.op }
    if (!takesValue(node.op)) return comparison
    if (node.variable) comparison.value = { var: node.variable }
    else comparison.value = takesList(node.op) ? node.values.map(value => literal(value, field)) : literal(node.value, field)
    return comparison
  }
  return write(group)
}

function text(value: unknown, field: DataEntityField | undefined): string {
  if (field?.type === 'TIME' && typeof value === 'string') return localInput(value)
  return value === null || value === undefined ? '' : String(value)
}

/** Reads a stored condition into groups and comparisons; a lone comparison becomes a group of one. */
export function fromCondition(json: unknown, fields: DataEntityField[]): GroupNode {
  const read = (node: unknown): ConditionNode => {
    const object = (node ?? {}) as Record<string, unknown>
    if ('not' in object) {
      const inner = read(object.not)
      return inner.type === 'group' ? { ...inner, negate: !inner.negate } : { type: 'group', join: 'and', negate: true, children: [inner] }
    }
    if ('and' in object || 'or' in object) {
      const join = 'and' in object ? 'and' : 'or'
      return { type: 'group', join, negate: false, children: ((object[join] ?? []) as unknown[]).map(read) }
    }
    const field = fields.find(candidate => candidate.code === object.field)
    const op = String(object.op ?? 'eq')
    const value = object.value as unknown
    const variable = value && typeof value === 'object' && !Array.isArray(value) ? String((value as Record<string, unknown>).var ?? '') : ''
    return { type: 'comparison', field: String(object.field ?? ''), op, variable,
      value: variable || Array.isArray(value) ? '' : text(value, field),
      values: Array.isArray(value) ? value.map(element => text(element, field)) : [] }
  }
  const root = read(json)
  return root.type === 'group' ? root : { type: 'group', join: 'and', negate: false, children: [root] }
}

/** Where the server reports problems of a child: below the group's path, through its negation. */
export function childPath(group: GroupNode, path: string, index: number): string {
  return `${group.negate ? `${path}.not` : path}.${group.join}[${index}]`
}
