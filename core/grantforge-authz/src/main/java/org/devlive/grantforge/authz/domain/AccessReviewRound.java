// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A round of an access review: the assignments of the review's roles when it started (see {@link AccessReviewItem}),
 * which reviewers keep or revoke until it is due. Completing it applies the decisions.
 */
@Entity
@Table(name = "gf_access_review_round")
public class AccessReviewRound
        extends TenantScopedEntity
{
    @Column(name = "review_id", nullable = false, updatable = false)
    private long reviewId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.EPOCH;

    @Column(name = "due_at", nullable = false, updatable = false)
    private Instant dueAt = Instant.EPOCH;

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private ReviewRoundStatus status = ReviewRoundStatus.OPEN;

    @Column(name = "started_by", updatable = false)
    private @Nullable Long startedBy;

    @Column(name = "ended_at")
    private @Nullable Instant endedAt;

    @Column(name = "ended_by")
    private @Nullable Long endedBy;

    /** For JPA. */
    protected AccessReviewRound()
    {
    }

    /**
     * Opens a round.
     *
     * @param reviewId the review
     * @param startedBy who started it, or {@code null} for the schedule
     * @param now the current time
     * @param due when it completes by itself
     * @return the round, open
     * @throws IllegalArgumentException if it would be due before it starts
     */
    public static AccessReviewRound open(long reviewId, @Nullable Long startedBy, Instant now, Instant due)
    {
        if (!due.isAfter(now)) {
            throw new IllegalArgumentException("a round must be due after it starts");
        }
        AccessReviewRound round = new AccessReviewRound();
        round.reviewId = reviewId;
        round.startedBy = startedBy;
        round.startedAt = requireNonNull(now, "now");
        round.dueAt = due;
        return round;
    }

    /**
     * Ends the round.
     *
     * @param outcome completed or cancelled
     * @param by who ended it, or {@code null} for the schedule
     * @param now the current time
     * @throws IllegalStateException if it is not open
     * @throws IllegalArgumentException if the outcome is {@link ReviewRoundStatus#OPEN}
     */
    public void end(ReviewRoundStatus outcome, @Nullable Long by, Instant now)
    {
        if (status != ReviewRoundStatus.OPEN) {
            throw new IllegalStateException("round is " + status);
        }
        if (outcome == ReviewRoundStatus.OPEN) {
            throw new IllegalArgumentException("a round ends completed or cancelled");
        }
        this.status = outcome;
        this.endedBy = by;
        this.endedAt = requireNonNull(now, "now");
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
     * Returns when the round started.
     *
     * @return the time
     */
    public Instant getStartedAt()
    {
        return startedAt;
    }

    /**
     * Returns when the round completes by itself.
     *
     * @return the time
     */
    public Instant getDueAt()
    {
        return dueAt;
    }

    /**
     * Returns where the round stands.
     *
     * @return the status
     */
    public ReviewRoundStatus getStatus()
    {
        return status;
    }

    /**
     * Returns who started the round.
     *
     * @return the account, or {@code null} for the schedule
     */
    public @Nullable Long getStartedBy()
    {
        return startedBy;
    }

    /**
     * Returns when the round ended.
     *
     * @return the time, or {@code null} while it is open
     */
    public @Nullable Instant getEndedAt()
    {
        return endedAt;
    }

    /**
     * Returns who ended the round.
     *
     * @return the account, or {@code null} for the schedule or while it is open
     */
    public @Nullable Long getEndedBy()
    {
        return endedBy;
    }
}
