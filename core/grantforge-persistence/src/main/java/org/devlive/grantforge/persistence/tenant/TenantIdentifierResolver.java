// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Supplies the {@link TenantContext} tenant to Hibernate's {@code @TenantId} filtering.
 *
 * <p>Hibernate asks once, when a session opens, so the tenant must be bound before the transaction starts
 * (open-session-in-view is disabled for this reason).
 */
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<Long>
{
    /**
     * Tenant used when none is bound: no tenant has this ID, so tenant-scoped queries match nothing and the
     * application fails closed instead of reading or writing across tenants.
     */
    public static final long UNBOUND_TENANT = -1L;

    @Override
    public Long resolveCurrentTenantIdentifier()
    {
        return TenantContext.currentTenantId().orElse(UNBOUND_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions()
    {
        return true;
    }

    @Override
    public boolean isRoot(Long tenantId)
    {
        // Root sessions see every tenant; only explicit system work gets one.
        return TenantContext.isSystem();
    }
}
