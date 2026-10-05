// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { translate } from '@/i18n'
import type { components } from '@/api/schema'

type Status = components['schemas']['AccessRequestResponse']['status']

const labels = {
  PENDING: 'requests.status.PENDING',
  APPROVED: 'requests.status.APPROVED',
  REJECTED: 'requests.status.REJECTED',
  CANCELLED: 'requests.status.CANCELLED',
  EXPIRED: 'requests.status.EXPIRED',
  REVOKED: 'requests.status.REVOKED',
} as const satisfies Record<Status, string>

/** Names where an access request stands, in the interface language. */
export function statusLabel(status: Status): string {
  return translate(labels[status])
}
