// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Assignments under review in access review rounds. */
public interface AccessReviewItemRepository
        extends JpaRepository<AccessReviewItem, Long>
{
    /**
     * Lists a page of a round's items.
     *
     * @param roundId the round
     * @param pageable the page and order
     * @return the items
     */
    Page<AccessReviewItem> findByRoundId(long roundId, Pageable pageable);

    /**
     * Lists a page of a round's items with some decisions.
     *
     * @param roundId the round
     * @param decisions the decisions
     * @param pageable the page and order
     * @return the items
     */
    Page<AccessReviewItem> findByRoundIdAndDecisionIn(long roundId, Collection<ReviewDecision> decisions, Pageable pageable);

    /**
     * Lists every item of a round, in order.
     *
     * @param roundId the round
     * @return the items
     */
    List<AccessReviewItem> findByRoundIdOrderByIdAsc(long roundId);

    /**
     * Counts the items of some rounds by decision and outcome.
     *
     * @param roundIds the rounds; at most a few hundred
     * @return per round, decision and outcome: the number of items
     */
    @Query("select new org.devlive.grantforge.authz.domain.ReviewTally(i.roundId, i.decision, i.outcome, count(i)) from AccessReviewItem i"
            + " where i.roundId in :rounds group by i.roundId, i.decision, i.outcome")
    List<ReviewTally> tally(@Param("rounds") Collection<Long> roundIds);

    /**
     * Removes the items of the rounds of a review.
     *
     * @param reviewId the review
     * @return how many went
     */
    @Modifying
    @Query("delete from AccessReviewItem i where i.roundId in (select r.id from AccessReviewRound r where r.reviewId = :review)")
    int deleteByReview(@Param("review") long reviewId);
}
