// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Rounds of access reviews of the bound tenant; call as system to look across tenants. */
public interface AccessReviewRoundRepository
        extends JpaRepository<AccessReviewRound, Long>
{
    /**
     * Finds a round and raises its version when the transaction commits, so concurrent decisions and completions of
     * the same round cannot both succeed.
     *
     * @param id the round
     * @return the round, if it exists
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select r from AccessReviewRound r where r.id = :id")
    Optional<AccessReviewRound> findForUpdate(@Param("id") long id);

    /**
     * Lists a review's rounds, newest first.
     *
     * @param reviewId the review
     * @param limit how many at most
     * @return the rounds
     */
    List<AccessReviewRound> findByReviewIdOrderByStartedAtDescIdDesc(long reviewId, Limit limit);

    /**
     * Lists the rounds of some reviews in a state.
     *
     * @param reviewIds the reviews
     * @param status the state
     * @return the rounds
     */
    List<AccessReviewRound> findByReviewIdInAndStatus(Collection<Long> reviewIds, ReviewRoundStatus status);

    /**
     * Tells whether a review has a round in a state.
     *
     * @param reviewId the review
     * @param status the state
     * @return whether one exists
     */
    boolean existsByReviewIdAndStatus(long reviewId, ReviewRoundStatus status);

    /**
     * Lists the rounds in a state that are due.
     *
     * @param status open
     * @param now the current time
     * @return the rounds to complete
     */
    List<AccessReviewRound> findByStatusAndDueAtLessThanEqual(ReviewRoundStatus status, Instant now);

    /**
     * Removes the rounds of a review; their items go with them.
     *
     * @param reviewId the review
     * @return how many went
     */
    @Modifying
    @Query("delete from AccessReviewRound r where r.reviewId = :review")
    int deleteByReview(@Param("review") long reviewId);
}
