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
 * An assignment under review in a round: the role and subject are copied when the round starts, so the item still
 * says what was reviewed after the assignment is gone.
 */
@Entity
@Table(name = "gf_access_review_item")
public class AccessReviewItem
        extends TenantScopedEntity
{
    /** Longest comment. */
    public static final int COMMENT_MAX = 500;

    @Column(name = "round_id", nullable = false, updatable = false)
    private long roundId;

    @Column(name = "assignment_id", nullable = false, updatable = false)
    private long assignmentId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private long roleId;

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "subject_type", nullable = false, length = 16, updatable = false)
    private SubjectType subjectType = SubjectType.USER;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private long subjectId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "decision", nullable = false, length = 16)
    private ReviewDecision decision = ReviewDecision.PENDING;

    @Column(name = "decided_by")
    private @Nullable Long decidedBy;

    @Column(name = "decided_at")
    private @Nullable Instant decidedAt;

    @Column(name = "decision_comment", length = COMMENT_MAX)
    private @Nullable String comment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "outcome", length = 16)
    private @Nullable ReviewOutcome outcome;

    /** For JPA. */
    protected AccessReviewItem()
    {
    }

    /**
     * Puts an assignment under review.
     *
     * @param roundId the round
     * @param assignment the assignment
     * @return the item, undecided
     */
    public static AccessReviewItem of(long roundId, RoleAssignment assignment)
    {
        AccessReviewItem item = new AccessReviewItem();
        item.roundId = roundId;
        item.assignmentId = assignment.requireId();
        item.roleId = assignment.getRoleId();
        item.subjectType = assignment.getSubjectType();
        item.subjectId = assignment.getSubjectId();
        return item;
    }

    /**
     * Records a decision; {@link ReviewDecision#PENDING} takes one back.
     *
     * @param newDecision the decision
     * @param reviewer who decided
     * @param now the current time
     * @param newComment why, or {@code null}
     * @throws IllegalStateException if the round applied the item already
     */
    // An undecided item has no reviewer, time or comment: the nullable columns say so.
    @SuppressWarnings("PMD.NullAssignment")
    public void decide(ReviewDecision newDecision, long reviewer, Instant now, @Nullable String newComment)
    {
        requireUnapplied();
        this.decision = requireNonNull(newDecision, "newDecision");
        boolean undecided = newDecision == ReviewDecision.PENDING;
        this.decidedBy = undecided ? null : reviewer;
        this.decidedAt = undecided ? null : requireNonNull(now, "now");
        this.comment = undecided ? null : newComment;
    }

    /**
     * Records what completing the round did.
     *
     * @param result the outcome
     * @throws IllegalStateException if it was recorded already
     */
    public void apply(ReviewOutcome result)
    {
        requireUnapplied();
        this.outcome = requireNonNull(result, "result");
    }

    private void requireUnapplied()
    {
        if (outcome != null) {
            throw new IllegalStateException("item was applied: " + outcome);
        }
    }

    /**
     * Returns the round.
     *
     * @return its ID
     */
    public long getRoundId()
    {
        return roundId;
    }

    /**
     * Returns the assignment under review.
     *
     * @return its ID; it may be gone
     */
    public long getAssignmentId()
    {
        return assignmentId;
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
     * Returns what the subject is.
     *
     * @return the subject type
     */
    public SubjectType getSubjectType()
    {
        return subjectType;
    }

    /**
     * Returns the subject.
     *
     * @return its ID
     */
    public long getSubjectId()
    {
        return subjectId;
    }

    /**
     * Returns the decision.
     *
     * @return the decision, pending if nobody decided
     */
    public ReviewDecision getDecision()
    {
        return decision;
    }

    /**
     * Returns who decided.
     *
     * @return the reviewer, or {@code null}
     */
    public @Nullable Long getDecidedBy()
    {
        return decidedBy;
    }

    /**
     * Returns when it was decided.
     *
     * @return the time, or {@code null}
     */
    public @Nullable Instant getDecidedAt()
    {
        return decidedAt;
    }

    /**
     * Returns what the reviewer said.
     *
     * @return the comment, or {@code null}
     */
    public @Nullable String getComment()
    {
        return comment;
    }

    /**
     * Returns what completing the round did.
     *
     * @return the outcome, or {@code null} until the round completes
     */
    public @Nullable ReviewOutcome getOutcome()
    {
        return outcome;
    }
}
