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

import java.time.Duration;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * An access review (D-75): who holds some roles (see {@link AccessReviewRole}) is reviewed in rounds (see
 * {@link AccessReviewRound}), started by hand or every {@link #getIntervalDays()} days from {@link #getNextRunAt()}. Each
 * round stays open for {@link #getDurationDays()} days; then the decisions apply and undecided assignments follow
 * {@link #getUnreviewed()}.
 */
@Entity
@Table(name = "gf_access_review")
public class AccessReview
        extends TenantScopedEntity
{
    /** Longest name. */
    public static final int NAME_MAX = 128;

    /** Longest description. */
    public static final int DESCRIPTION_MAX = 512;

    /** Longest time a round stays open. */
    public static final int MAX_DURATION_DAYS = 90;

    /** Longest time between rounds. */
    public static final int MAX_INTERVAL_DAYS = 366;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name = "";

    @Column(name = "description", length = DESCRIPTION_MAX)
    private @Nullable String description;

    @Column(name = "duration_days", nullable = false)
    private int durationDays = 14;

    @Column(name = "interval_days")
    private @Nullable Integer intervalDays;

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "unreviewed", nullable = false, length = 16)
    private ReviewFallback unreviewed = ReviewFallback.KEEP;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "next_run_at")
    private @Nullable Instant nextRunAt;

    @Column(name = "last_started_at")
    private @Nullable Instant lastStartedAt;

    /** For JPA. */
    protected AccessReview()
    {
    }

    /**
     * Creates a review; {@link #configure} sets it up.
     *
     * @return the review
     */
    public static AccessReview create()
    {
        return new AccessReview();
    }

    /**
     * Changes what can change.
     *
     * @param newName the name
     * @param newDescription a longer explanation, or {@code null}
     * @param days how long each round stays open, 1 to {@link #MAX_DURATION_DAYS}
     * @param interval days between scheduled rounds, at least {@code days} and at most {@link #MAX_INTERVAL_DAYS}, or
     *        {@code null} for one scheduled round at most
     * @param fallback what happens to undecided assignments
     * @param on whether rounds start on schedule
     * @param next when the next scheduled round starts, or {@code null} for none
     * @throws IllegalArgumentException if a period is out of range
     */
    public void configure(String newName, @Nullable String newDescription, int days, @Nullable Integer interval, ReviewFallback fallback,
            boolean on, @Nullable Instant next)
    {
        if (days < 1 || days > MAX_DURATION_DAYS) {
            throw new IllegalArgumentException("a round lasts 1-" + MAX_DURATION_DAYS + " days");
        }
        // A round must end before the next one starts: a review has at most one open round.
        if (interval != null && (interval < days || interval > MAX_INTERVAL_DAYS)) {
            throw new IllegalArgumentException("rounds repeat every " + days + "-" + MAX_INTERVAL_DAYS + " days");
        }
        this.name = requireNonNull(newName, "newName");
        this.description = newDescription;
        this.durationDays = days;
        this.intervalDays = interval;
        this.unreviewed = requireNonNull(fallback, "fallback");
        this.enabled = on;
        this.nextRunAt = next;
    }

    /**
     * Records that a round started; one that started on schedule moves the schedule on: to the first time after now in
     * steps of the interval, so a server that was down does not start missed rounds one after another, or to none
     * without an interval.
     *
     * @param now the current time
     * @param scheduled whether the schedule started the round
     */
    // NULL is how the next-run column says "no scheduled round".
    @SuppressWarnings("PMD.NullAssignment")
    public void started(Instant now, boolean scheduled)
    {
        this.lastStartedAt = requireNonNull(now, "now");
        Instant next = nextRunAt;
        if (!scheduled || next == null) {
            return;
        }
        Integer interval = intervalDays;
        if (interval == null) {
            nextRunAt = null;
            return;
        }
        Duration step = Duration.ofDays(interval);
        while (!next.isAfter(now)) {
            next = next.plus(step);
        }
        nextRunAt = next;
    }

    /**
     * Returns whether a round is due on schedule.
     *
     * @param now the current time
     * @return whether the review is enabled and its next round's time has come
     */
    public boolean isDue(Instant now)
    {
        Instant next = nextRunAt;
        return enabled && next != null && !next.isAfter(now);
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the longer explanation.
     *
     * @return the description, or {@code null}
     */
    public @Nullable String getDescription()
    {
        return description;
    }

    /**
     * Returns how long each round stays open.
     *
     * @return days
     */
    public int getDurationDays()
    {
        return durationDays;
    }

    /**
     * Returns the days between scheduled rounds.
     *
     * @return days, or {@code null} if rounds do not repeat
     */
    public @Nullable Integer getIntervalDays()
    {
        return intervalDays;
    }

    /**
     * Returns what happens to undecided assignments.
     *
     * @return the fallback
     */
    public ReviewFallback getUnreviewed()
    {
        return unreviewed;
    }

    /**
     * Returns whether rounds start on schedule.
     *
     * @return whether the review is enabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Returns when the next scheduled round starts.
     *
     * @return the time, or {@code null} for none
     */
    public @Nullable Instant getNextRunAt()
    {
        return nextRunAt;
    }

    /**
     * Returns when the last round started.
     *
     * @return the time, or {@code null} if none did
     */
    public @Nullable Instant getLastStartedAt()
    {
        return lastStartedAt;
    }
}
