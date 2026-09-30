// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * Base class of entities that belong to one tenant.
 *
 * <p>Hibernate fills {@code tenant_id} from {@link TenantIdentifierResolver} on insert and adds a
 * {@code tenant_id} restriction to every query, so repositories never filter by tenant themselves.
 * Inserting requires a tenant binding ({@link TenantContext#callInTenant}); system context cannot insert
 * because it has no tenant to assign.
 */
@MappedSuperclass
public abstract class TenantScopedEntity
        extends BaseEntity
{
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private @Nullable Long tenantId;

    /** For JPA and subclasses. */
    protected TenantScopedEntity()
    {
    }

    /**
     * Returns the owning tenant.
     *
     * @return the tenant ID, or {@code null} before the entity is persisted
     */
    public @Nullable Long getTenantId()
    {
        return tenantId;
    }

    @PrePersist
    void requireTenantBinding()
    {
        // Fails before Hibernate would store the "unbound" tenant ID.
        TenantContext.requireTenantId();
    }
}
