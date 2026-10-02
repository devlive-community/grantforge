// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.authz;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;

import static java.util.Objects.requireNonNull;

/**
 * Entity listener for data that permissions are worked out from (roles, grants, assignments, memberships, the
 * resource catalog): any insert, update or delete notes a change of the entity's tenant, or of the shared catalog
 * for entities that belong to no tenant. Created by Spring, so it reaches the {@link AuthorizationChanges} bean.
 */
public final class AuthorizationChangeListener
{
    private final AuthorizationChanges changes;

    /**
     * Creates the listener.
     *
     * @param changes collects the changes
     */
    public AuthorizationChangeListener(AuthorizationChanges changes)
    {
        this.changes = requireNonNull(changes, "changes");
    }

    /**
     * Makes sure the transaction notices the entity's changes before it commits.
     *
     * @param entity the entity loaded, or about to be stored or deleted
     */
    @PostLoad
    @PrePersist
    @PreRemove
    public void touched(Object entity)
    {
        changes.watch();
    }

    /**
     * Notes the change of an entity.
     *
     * @param entity the entity inserted, updated or deleted
     */
    @PostPersist
    @PostUpdate
    @PostRemove
    public void changed(Object entity)
    {
        if (entity instanceof TenantScopedEntity scoped) {
            Long tenantId = scoped.getTenantId();
            if (tenantId != null) {
                changes.tenant(tenantId);
            }
            else {
                changes.currentTenant();
            }
        }
        else {
            changes.catalog();
        }
    }
}
