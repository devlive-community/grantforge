// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { flattenManifest } from '@/permissions/manifest'

/** Every resource code of the console's manifest: what a platform administrator holds. */
export const everything = (): string[] => flattenManifest().map(entry => entry.code)

/** An authorization holding the given resources and API permissions. */
export function authorization(resources: string[], permissions: string[] = [], version = 1) {
  return { version, unrestricted: false, roles: [], resources, permissions }
}
