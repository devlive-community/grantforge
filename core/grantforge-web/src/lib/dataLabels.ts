// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { components } from '@/api/schema'

type Translate = (key: string, values?: Record<string, unknown>) => string
type Policy = components['schemas']['DataPolicyResponse']

/**
 * Names what data policies are made of in the user's language. Entities and fields the console does not know keep the
 * names the server gives them.
 */
export function dataLabels(t: Translate) {
  const scopes: Record<Policy['scope'], string> = {
    ALL: t('dataPolicies.scopeAll'), TENANT: t('dataPolicies.scopeTenant'), ORG_AND_CHILDREN: t('dataPolicies.scopeOrgAndChildren'),
    ORG: t('dataPolicies.scopeOrg'), CUSTOM_ORGS: t('dataPolicies.scopeCustomOrgs'), SELF: t('dataPolicies.scopeSelf'),
    CONDITION: t('dataPolicies.scopeCondition'),
  }
  const actions: Record<Policy['action'], string> = {
    READ: t('dataPolicies.actionRead'), UPDATE: t('dataPolicies.actionUpdate'), DELETE: t('dataPolicies.actionDelete'),
    EXPORT: t('dataPolicies.actionExport'),
  }
  const operators: Record<string, string> = {
    eq: t('dataPolicies.opEq'), ne: t('dataPolicies.opNe'), lt: t('dataPolicies.opLt'), lte: t('dataPolicies.opLte'),
    gt: t('dataPolicies.opGt'), gte: t('dataPolicies.opGte'), in: t('dataPolicies.opIn'), not_in: t('dataPolicies.opNotIn'),
    contains: t('dataPolicies.opContains'), starts_with: t('dataPolicies.opStartsWith'), is_null: t('dataPolicies.opIsNull'),
    not_null: t('dataPolicies.opNotNull'),
  }
  const variables: Record<string, string> = {
    'subject.id': t('dataPolicies.varSubjectId'), 'subject.username': t('dataPolicies.varSubjectUsername'),
    'subject.orgUnitIds': t('dataPolicies.varSubjectOrgUnits'), 'subject.groupCodes': t('dataPolicies.varSubjectGroups'),
    'subject.positionCodes': t('dataPolicies.varSubjectPositions'), now: t('dataPolicies.varNow'),
  }
  const entities: Record<string, string> = {
    user: t('dataPolicies.entityUser'), 'org-unit': t('dataPolicies.entityOrgUnit'), group: t('dataPolicies.entityGroup'),
    position: t('dataPolicies.entityPosition'), 'audit-event': t('dataPolicies.entityAuditEvent'),
  }
  const fields: Record<string, string> = {
    username: t('dataPolicies.fieldUsername'), displayName: t('dataPolicies.fieldDisplayName'), email: t('dataPolicies.fieldEmail'),
    status: t('dataPolicies.fieldStatus'), lastLoginAt: t('dataPolicies.fieldLastLoginAt'), code: t('dataPolicies.fieldCode'),
    name: t('dataPolicies.fieldName'), depth: t('dataPolicies.fieldDepth'), occurredAt: t('dataPolicies.fieldOccurredAt'),
    action: t('dataPolicies.fieldAction'), outcome: t('dataPolicies.fieldOutcome'), actorName: t('dataPolicies.fieldActorName'),
    clientIp: t('dataPolicies.fieldClientIp'),
  }
  return {
    scope: (scope: Policy['scope']) => scopes[scope],
    action: (action: Policy['action']) => actions[action],
    effect: (effect: Policy['effect']) => effect === 'DENY' ? t('dataPolicies.effectDeny') : t('dataPolicies.effectAllow'),
    operator: (symbol: string) => operators[symbol] ?? symbol,
    variable: (key: string) => variables[key] ?? key,
    entity: (code: string, name: string) => entities[code] ?? name,
    field: (code: string, name: string) => fields[code] ?? name,
  }
}
