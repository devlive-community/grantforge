// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { i18n } from '@/i18n'

/** System roles have fixed codes; the console names them in the user's language. Literal keys for the message check. */
const systemNames: Readonly<Record<string, 'roles.systemTenantAdmin' | 'roles.systemPlatformAdmin'>> = {
  'tenant-admin': 'roles.systemTenantAdmin', 'platform-admin': 'roles.systemPlatformAdmin',
}

/** Returns a role's name in the user's language: translated for system roles, as stored otherwise. */
export function roleLabel(role: { type: 'SYSTEM' | 'CUSTOM'; code: string; name: string }): string {
  const key = role.type === 'SYSTEM' ? systemNames[role.code] : undefined
  return key ? i18n.global.t(key) : role.name
}
