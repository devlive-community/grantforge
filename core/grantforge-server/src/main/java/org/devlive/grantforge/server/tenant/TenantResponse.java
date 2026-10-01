// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.tenant;

import org.devlive.grantforge.identity.application.TenantSummary;
import org.devlive.grantforge.identity.domain.TenantStatus;

import java.time.Instant;

/**
 * A tenant as the platform administration shows it.
 *
 * @param id the tenant ID, a string because it exceeds JavaScript's safe integers
 * @param code the stable code
 * @param name the display name
 * @param status whether its accounts may sign in
 * @param platform whether it is the platform tenant, which cannot be suspended
 * @param accounts how many accounts it has
 * @param createdAt when it was created
 */
public record TenantResponse(String id, String code, String name, TenantStatus status, boolean platform, long accounts,
        Instant createdAt)
{
    /**
     * Converts a summary.
     *
     * @param tenant the summary
     * @return the response
     */
    public static TenantResponse from(TenantSummary tenant)
    {
        return new TenantResponse(Long.toString(tenant.id()), tenant.code(), tenant.name(), tenant.status(),
                tenant.platform(), tenant.accounts(), tenant.createdAt());
    }
}
