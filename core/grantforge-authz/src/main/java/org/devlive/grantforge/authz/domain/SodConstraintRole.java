// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;

/** A role of a separation-of-duties constraint. */
@Entity
@Table(name = "gf_sod_constraint_role")
public class SodConstraintRole
        extends TenantScopedEntity
{
    @Column(name = "constraint_id", nullable = false, updatable = false)
    private long constraintId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private long roleId;

    /** For JPA. */
    protected SodConstraintRole()
    {
    }

    /**
     * Adds a role to a constraint.
     *
     * @param constraintId the constraint
     * @param roleId the role
     * @return the link
     */
    public static SodConstraintRole of(long constraintId, long roleId)
    {
        SodConstraintRole link = new SodConstraintRole();
        link.constraintId = constraintId;
        link.roleId = roleId;
        return link;
    }

    /**
     * Returns the constraint.
     *
     * @return its ID
     */
    public long getConstraintId()
    {
        return constraintId;
    }

    /**
     * Returns the role.
     *
     * @return its ID
     */
    public long getRoleId()
    {
        return roleId;
    }
}
