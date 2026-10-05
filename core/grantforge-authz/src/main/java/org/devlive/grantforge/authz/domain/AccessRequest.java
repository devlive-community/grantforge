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
 * A request of an account for a role for a while (D-74). An approver grants it as an assignment that ends with the
 * granted period, or turns it down; the role is taken back when the period is over or an approver ends it early.
 */
@Entity
@Table(name = "gf_access_request")
public class AccessRequest
        extends TenantScopedEntity
{
    /** Longest reason or comment. */
    public static final int TEXT_MAX = 500;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private long requesterId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private long roleId;

    @Column(name = "reason", nullable = false, updatable = false, length = TEXT_MAX)
    private String reason = "";

    @Column(name = "requested_days", nullable = false, updatable = false)
    private int requestedDays;

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private AccessRequestStatus status = AccessRequestStatus.PENDING;

    @Column(name = "decided_by")
    private @Nullable Long decidedBy;

    @Column(name = "decided_at")
    private @Nullable Instant decidedAt;

    @Column(name = "decision_comment", length = TEXT_MAX)
    private @Nullable String decisionComment;

    @Column(name = "assignment_id")
    private @Nullable Long assignmentId;

    @Column(name = "valid_until")
    private @Nullable Instant validUntil;

    @Column(name = "ended_at")
    private @Nullable Instant endedAt;

    /** For JPA. */
    protected AccessRequest()
    {
    }

    /**
     * Files a request.
     *
     * @param requesterId the account asking
     * @param roleId the role asked for
     * @param reason why
     * @param days for how many days
     * @return the request, pending
     * @throws IllegalArgumentException if fewer than one day is asked for
     */
    public static AccessRequest file(long requesterId, long roleId, String reason, int days)
    {
        if (days < 1) {
            throw new IllegalArgumentException("days must be at least 1");
        }
        AccessRequest request = new AccessRequest();
        request.requesterId = requesterId;
        request.roleId = roleId;
        request.reason = requireNonNull(reason, "reason");
        request.requestedDays = days;
        return request;
    }

    /**
     * Records the approval.
     *
     * @param approverId the approver
     * @param now the current time
     * @param comment what the approver said, or {@code null}
     * @param assignment the assignment that grants the role
     * @param until when it ends
     */
    public void approve(long approverId, Instant now, @Nullable String comment, long assignment, Instant until)
    {
        requireStatus(AccessRequestStatus.PENDING);
        decide(approverId, now, comment, AccessRequestStatus.APPROVED);
        this.assignmentId = assignment;
        this.validUntil = requireNonNull(until, "until");
    }

    /**
     * Records the rejection.
     *
     * @param approverId the approver
     * @param now the current time
     * @param comment why, or {@code null}
     */
    public void reject(long approverId, Instant now, @Nullable String comment)
    {
        requireStatus(AccessRequestStatus.PENDING);
        decide(approverId, now, comment, AccessRequestStatus.REJECTED);
    }

    /**
     * Withdraws the request.
     *
     * @param now the current time
     */
    public void cancel(Instant now)
    {
        requireStatus(AccessRequestStatus.PENDING);
        this.status = AccessRequestStatus.CANCELLED;
        this.endedAt = requireNonNull(now, "now");
    }

    /**
     * Ends an approved grant, because its period is over or an approver ended it early.
     *
     * @param now the current time
     * @param early whether an approver ended it before its period was over
     */
    public void end(Instant now, boolean early)
    {
        requireStatus(AccessRequestStatus.APPROVED);
        this.status = early ? AccessRequestStatus.REVOKED : AccessRequestStatus.EXPIRED;
        this.endedAt = requireNonNull(now, "now");
    }

    private void decide(long approverId, Instant now, @Nullable String comment, AccessRequestStatus outcome)
    {
        this.decidedBy = approverId;
        this.decidedAt = requireNonNull(now, "now");
        this.decisionComment = comment;
        this.status = outcome;
    }

    private void requireStatus(AccessRequestStatus expected)
    {
        if (status != expected) {
            throw new IllegalStateException("request is " + status + ", not " + expected);
        }
    }

    /**
     * Returns the account asking.
     *
     * @return its ID
     */
    public long getRequesterId()
    {
        return requesterId;
    }

    /**
     * Returns the role asked for.
     *
     * @return its ID
     */
    public long getRoleId()
    {
        return roleId;
    }

    /**
     * Returns why.
     *
     * @return the reason
     */
    public String getReason()
    {
        return reason;
    }

    /**
     * Returns for how many days.
     *
     * @return the days asked for
     */
    public int getRequestedDays()
    {
        return requestedDays;
    }

    /**
     * Returns where the request stands.
     *
     * @return the status
     */
    public AccessRequestStatus getStatus()
    {
        return status;
    }

    /**
     * Returns who decided.
     *
     * @return the approver, or {@code null}
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
     * Returns what the approver said.
     *
     * @return the comment, or {@code null}
     */
    public @Nullable String getDecisionComment()
    {
        return decisionComment;
    }

    /**
     * Returns the assignment that grants the role.
     *
     * @return its ID, or {@code null}
     */
    public @Nullable Long getAssignmentId()
    {
        return assignmentId;
    }

    /**
     * Returns when the granted period ends.
     *
     * @return the time, or {@code null}
     */
    public @Nullable Instant getValidUntil()
    {
        return validUntil;
    }

    /**
     * Returns when the request or grant ended.
     *
     * @return the time, or {@code null}
     */
    public @Nullable Instant getEndedAt()
    {
        return endedAt;
    }
}
