// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { translate } from '@/i18n'
import type { components } from '@/api/schema'

type Round = components['schemas']['ReviewRoundResponse']
type Item = components['schemas']['ReviewItemResponse']
type Review = components['schemas']['AccessReviewResponse']

// Message keys stay literal so the i18n check can see them.
const statuses = {
  OPEN: 'reviews.status.OPEN',
  COMPLETED: 'reviews.status.COMPLETED',
  CANCELLED: 'reviews.status.CANCELLED',
} as const satisfies Record<Round['status'], string>
const decisions = {
  PENDING: 'reviews.decision.PENDING',
  KEEP: 'reviews.decision.KEEP',
  REVOKE: 'reviews.decision.REVOKE',
} as const satisfies Record<Item['decision'], string>
const outcomes = {
  KEPT: 'reviews.outcome.KEPT',
  REVOKED: 'reviews.outcome.REVOKED',
  GONE: 'reviews.outcome.GONE',
} as const satisfies Record<NonNullable<Item['outcome']>, string>
const fallbacks = {
  KEEP: 'reviews.fallback.KEEP',
  REVOKE: 'reviews.fallback.REVOKE',
} as const satisfies Record<Review['unreviewed'], string>
const fallbackEffects = {
  KEEP: 'reviews.fallbackText.KEEP',
  REVOKE: 'reviews.fallbackText.REVOKE',
} as const satisfies Record<Review['unreviewed'], string>
const subjects = {
  USER: 'assignments.typeUser',
  GROUP: 'assignments.typeGroup',
  ORG_UNIT: 'assignments.typeOrgUnit',
  POSITION: 'assignments.typePosition',
} as const satisfies Record<Item['subjectType'], string>

/** Names where a round of an access review stands. */
export function roundStatusLabel(status: Round['status']): string { return translate(statuses[status]) }
/** Names a reviewer's decision. */
export function decisionLabel(decision: Item['decision']): string { return translate(decisions[decision]) }
/** Names what completing a round did to an assignment. */
export function outcomeLabel(outcome: NonNullable<Item['outcome']>): string { return translate(outcomes[outcome]) }
/** Names what happens to undecided assignments, as a choice. */
export function fallbackLabel(fallback: Review['unreviewed']): string { return translate(fallbacks[fallback]) }
/** Says what happens to undecided assignments, to finish a sentence. */
export function fallbackEffect(fallback: Review['unreviewed']): string { return translate(fallbackEffects[fallback]) }
/** Names what a reviewed subject is. */
export function subjectTypeLabel(type: Item['subjectType']): string { return translate(subjects[type]) }
