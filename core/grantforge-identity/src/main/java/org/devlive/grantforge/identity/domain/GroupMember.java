// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;

/** An account's membership of a user group. */
@Entity
@Table(name = "gf_group_member")
public class GroupMember
        extends TenantScopedEntity
{
    @Column(name = "group_id", nullable = false, updatable = false)
    private long groupId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private long accountId;

    /** For JPA. */
    protected GroupMember()
    {
    }

    /**
     * Records a membership.
     *
     * @param groupId the group
     * @param accountId the account
     * @return the membership
     */
    public static GroupMember of(long groupId, long accountId)
    {
        GroupMember member = new GroupMember();
        member.groupId = groupId;
        member.accountId = accountId;
        return member;
    }

    /**
     * Returns the group.
     *
     * @return the group ID
     */
    public long getGroupId()
    {
        return groupId;
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
}
