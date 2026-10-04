// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { describe, expect, it } from 'vitest'
import contract from '@/api/openapi.json'
import { auditActions } from './auditActions'

describe('audit actions', () => {
  it('lists every kind of event the server declares, once', () => {
    const declared = (contract as { components: { schemas: { AuditEventResponse: { properties: { action: { enum: string[] } } } } } })
      .components.schemas.AuditEventResponse.properties.action.enum
    expect([...auditActions]).toEqual(declared)
    expect(new Set(auditActions).size).toBe(auditActions.length)
  })
})
