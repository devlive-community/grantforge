// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.authz.AuthorizationChangeListener;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;

/**
 * That a role inherits from another: holders of the role are allowed and denied what the parent role is, as long as
 * the parent is enabled. The links of a tenant form a directed graph without cycles ({@link RoleHierarchy}).
 */
@Entity
@EntityListeners(AuthorizationChangeListener.class)
@Table(name = "gf_role_parent")
public class RoleParent
        extends TenantScopedEntity
{
    @Column(name = "role_id", nullable = false, updatable = false)
    private long roleId;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private long parentId;

    /** For JPA. */
    protected RoleParent()
    {
    }

    /**
     * Links a role to a parent.
     *
     * @param roleId the inheriting role
     * @param parentId the role it inherits from
     * @return the link
     * @throws IllegalArgumentException if a role would inherit from itself
     */
    public static RoleParent of(long roleId, long parentId)
    {
        if (roleId == parentId) {
            throw new IllegalArgumentException("role " + roleId + " cannot inherit from itself");
        }
        RoleParent link = new RoleParent();
        link.roleId = roleId;
        link.parentId = parentId;
        return link;
    }

    /**
     * Returns the inheriting role.
     *
     * @return its ID
     */
    public long getRoleId()
    {
        return roleId;
    }

    /**
     * Returns the role inherited from.
     *
     * @return its ID
     */
    public long getParentId()
    {
        return parentId;
    }
}
