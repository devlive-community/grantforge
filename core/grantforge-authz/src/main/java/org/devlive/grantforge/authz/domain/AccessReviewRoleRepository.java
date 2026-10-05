// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Roles of access reviews. */
public interface AccessReviewRoleRepository
        extends JpaRepository<AccessReviewRole, Long>
{
    /**
     * Lists the roles of some reviews.
     *
     * @param reviewIds the reviews
     * @return their role links
     */
    List<AccessReviewRole> findByReviewIdIn(Collection<Long> reviewIds);

    /**
     * Removes the roles of a review.
     *
     * @param reviewId the review
     * @return how many went
     */
    @Modifying
    @Query("delete from AccessReviewRole r where r.reviewId = :review")
    int deleteByReview(@Param("review") long reviewId);
}
