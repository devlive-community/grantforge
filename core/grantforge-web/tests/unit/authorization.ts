// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { components } from '@/api/schema'
import { flattenManifest } from '@/permissions/manifest'

/** Every resource code of the console's manifest: what a platform administrator holds. */
export const everything = (): string[] => flattenManifest().map(entry => entry.code)

type Authorization = components['schemas']['AuthorizationResponse']

/** An authorization holding the given resources and API permissions, with the given restricted fields. */
export function authorization(resources: string[], permissions: string[] = [], version = 1, fields: Authorization['fields'] = {}) {
  return { version, unrestricted: false, roles: [], resources, permissions, fields }
}
