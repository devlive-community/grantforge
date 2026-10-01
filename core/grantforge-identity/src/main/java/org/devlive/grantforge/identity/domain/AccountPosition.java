// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;

/** An account holding a position. */
@Entity
@Table(name = "gf_account_position")
public class AccountPosition
        extends TenantScopedEntity
{
    @Column(name = "account_id", nullable = false, updatable = false)
    private long accountId;

    @Column(name = "position_id", nullable = false, updatable = false)
    private long positionId;

    /** For JPA. */
    protected AccountPosition()
    {
    }

    /**
     * Records that an account holds a position.
     *
     * @param accountId the account
     * @param positionId the position
     * @return the assignment
     */
    public static AccountPosition of(long accountId, long positionId)
    {
        AccountPosition assignment = new AccountPosition();
        assignment.accountId = accountId;
        assignment.positionId = positionId;
        return assignment;
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
     * Returns the position.
     *
     * @return the position ID
     */
    public long getPositionId()
    {
        return positionId;
    }
}
