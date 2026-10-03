// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { DataEntity, DataVariable } from '@/lib/dataCondition'

/** Users as the server describes them, with a field of every kind. */
export const users: DataEntity = {
  code: 'user', name: 'Users', scopes: ['ALL', 'TENANT', 'ORG_AND_CHILDREN', 'ORG', 'CUSTOM_ORGS', 'SELF', 'CONDITION'],
  fields: [
    { code: 'username', name: 'User name', type: 'TEXT', choices: [], operators: ['eq', 'ne', 'in', 'not_in', 'contains', 'starts_with', 'is_null', 'not_null'] },
    { code: 'status', name: 'Status', type: 'CHOICE', choices: ['ACTIVE', 'DISABLED'], operators: ['eq', 'ne', 'in', 'not_in', 'is_null', 'not_null'] },
    { code: 'age', name: 'Age', type: 'NUMBER', choices: [], operators: ['eq', 'ne', 'lt', 'lte', 'gt', 'gte', 'in', 'not_in', 'is_null', 'not_null'] },
    { code: 'admin', name: 'Admin', type: 'BOOLEAN', choices: [], operators: ['eq', 'ne', 'is_null', 'not_null'] },
    { code: 'lastLoginAt', name: 'Last sign-in', type: 'TIME', choices: [], operators: ['eq', 'ne', 'lt', 'lte', 'gt', 'gte', 'is_null', 'not_null'] },
  ],
  securedFields: [{ code: 'email', name: 'E-mail' }, { code: 'lastLoginAt', name: 'Last sign-in' }],
}
export const groups: DataEntity = { code: 'group', name: 'User groups', scopes: ['ALL', 'TENANT', 'CONDITION'], fields: [], securedFields: [] }
export const variables: DataVariable[] = [
  { key: 'subject.id', type: 'NUMBER', list: false }, { key: 'subject.username', type: 'TEXT', list: false },
  { key: 'subject.orgUnitIds', type: 'NUMBER', list: true }, { key: 'subject.groupCodes', type: 'TEXT', list: true },
  { key: 'now', type: 'TIME', list: false },
]
