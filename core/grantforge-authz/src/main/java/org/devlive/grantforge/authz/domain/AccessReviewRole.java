// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;

/** A role whose assignments an access review reviews. */
@Entity
@Table(name = "gf_access_review_role")
public class AccessReviewRole
        extends TenantScopedEntity
{
    @Column(name = "review_id", nullable = false, updatable = false)
    private long reviewId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private long roleId;

    /** For JPA. */
    protected AccessReviewRole()
    {
    }

    /**
     * Adds a role to a review.
     *
     * @param reviewId the review
     * @param roleId the role
     * @return the link
     */
    public static AccessReviewRole of(long reviewId, long roleId)
    {
        AccessReviewRole link = new AccessReviewRole();
        link.reviewId = reviewId;
        link.roleId = roleId;
        return link;
    }

    /**
     * Returns the review.
     *
     * @return its ID
     */
    public long getReviewId()
    {
        return reviewId;
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
