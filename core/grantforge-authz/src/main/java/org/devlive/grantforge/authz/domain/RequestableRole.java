// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;

/** A role accounts may ask for, and for how long at most (D-74). */
@Entity
@Table(name = "gf_requestable_role")
public class RequestableRole
        extends TenantScopedEntity
{
    /** Longest period anyone may ask for. */
    public static final int MAX_DAYS = 365;

    @Column(name = "role_id", nullable = false, updatable = false)
    private long roleId;

    @Column(name = "max_days", nullable = false)
    private int maxDays;

    /** For JPA. */
    protected RequestableRole()
    {
    }

    /**
     * Makes a role requestable.
     *
     * @param roleId the role
     * @param maxDays the longest period one may ask for, 1 to {@value #MAX_DAYS}
     * @return the setting
     */
    public static RequestableRole of(long roleId, int maxDays)
    {
        if (maxDays < 1 || maxDays > MAX_DAYS) {
            throw new IllegalArgumentException("max days must be 1-" + MAX_DAYS);
        }
        RequestableRole requestable = new RequestableRole();
        requestable.roleId = roleId;
        requestable.maxDays = maxDays;
        return requestable;
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

    /**
     * Returns the longest period one may ask for.
     *
     * @return days
     */
    public int getMaxDays()
    {
        return maxDays;
    }
}
