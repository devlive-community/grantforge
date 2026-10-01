// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.TenantStatus;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A tenant as the platform administration lists it.
 *
 * @param id the tenant ID
 * @param code the stable code
 * @param name the display name
 * @param status whether its accounts may sign in
 * @param platform whether it is the platform tenant
 * @param accounts how many accounts it has
 * @param createdAt when it was created
 */
public record TenantSummary(long id, String code, String name, TenantStatus status, boolean platform, long accounts,
        Instant createdAt)
{
    /** Validates the values. */
    public TenantSummary
    {
        requireNonNull(code, "code");
        requireNonNull(name, "name");
        requireNonNull(status, "status");
        requireNonNull(createdAt, "createdAt");
    }
}
