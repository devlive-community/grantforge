// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.authz.AuthorizationChangeListener;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;

/** An account's membership of a department: one primary department, and any number of others. */
@Entity
@EntityListeners(AuthorizationChangeListener.class)
@Table(name = "gf_org_member")
public class OrgMember
        extends TenantScopedEntity
{
    @Column(name = "account_id", nullable = false, updatable = false)
    private long accountId;

    @Column(name = "org_unit_id", nullable = false, updatable = false)
    private long orgUnitId;

    @Column(name = "primary_unit", nullable = false, updatable = false)
    private boolean primaryUnit;

    /** For JPA. */
    protected OrgMember()
    {
    }

    /**
     * Records a membership.
     *
     * @param accountId the account
     * @param orgUnitId the department
     * @param primaryUnit whether it is the account's primary department
     * @return the membership
     */
    public static OrgMember of(long accountId, long orgUnitId, boolean primaryUnit)
    {
        OrgMember member = new OrgMember();
        member.accountId = accountId;
        member.orgUnitId = orgUnitId;
        member.primaryUnit = primaryUnit;
        return member;
    }

    /**
     * Returns the account.
     *
     * @return the account ID
     */
    public long getAccountId()
    {
        return accountId;
    }

    /**
     * Returns the department.
     *
     * @return the department ID
     */
    public long getOrgUnitId()
    {
        return orgUnitId;
    }

    /**
     * Returns whether this is the account's primary department.
     *
     * @return {@code true} for the primary department
     */
    public boolean isPrimaryUnit()
    {
        return primaryUnit;
    }
}
