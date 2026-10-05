// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessReviewTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void keepsItsSettings()
    {
        AccessReview review = AccessReview.create();
        review.configure("Quarterly", "Finance roles", 14, 90, ReviewFallback.REVOKE, true, NOW);

        assertThat(review).extracting(AccessReview::getName, AccessReview::getDescription, AccessReview::getDurationDays, AccessReview::getIntervalDays,
                AccessReview::getUnreviewed, AccessReview::isEnabled, AccessReview::getNextRunAt, AccessReview::getLastStartedAt)
                .containsExactly("Quarterly", "Finance roles", 14, 90, ReviewFallback.REVOKE, true, NOW, null);
    }

    @Test
    void refusesPeriodsOutOfRange()
    {
        AccessReview review = AccessReview.create();

        assertThatThrownBy(() -> review.configure("R", null, 0, null, ReviewFallback.KEEP, true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> review.configure("R", null, 91, null, ReviewFallback.KEEP, true, null)).isInstanceOf(IllegalArgumentException.class);
        // Rounds cannot overlap: the next one starts after the last one is due.
        assertThatThrownBy(() -> review.configure("R", null, 14, 13, ReviewFallback.KEEP, true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> review.configure("R", null, 14, 367, ReviewFallback.KEEP, true, null)).isInstanceOf(IllegalArgumentException.class);
        review.configure("R", null, 90, 366, ReviewFallback.KEEP, true, null);
        assertThat(review.getIntervalDays()).isEqualTo(366);
    }

    @Test
    void movesTheScheduleOnPastTheCurrentTime()
    {
        AccessReview review = AccessReview.create();
        review.configure("R", null, 7, 30, ReviewFallback.KEEP, true, NOW);
        assertThat(review.isDue(NOW.minusSeconds(1))).isFalse();
        assertThat(review.isDue(NOW)).isTrue();

        // The server was down for two intervals: the next round is the first one after now, not the missed ones.
        Instant late = NOW.plus(Duration.ofDays(65));
        review.started(late, true);
        assertThat(review.getNextRunAt()).isEqualTo(NOW.plus(Duration.ofDays(90)));
        assertThat(review.getLastStartedAt()).isEqualTo(late);

        // A round started by hand leaves the schedule alone.
        review.started(late.plusSeconds(5), false);
        assertThat(review.getNextRunAt()).isEqualTo(NOW.plus(Duration.ofDays(90)));
        assertThat(review.getLastStartedAt()).isEqualTo(late.plusSeconds(5));
    }

    @Test
    void runsOnceWithoutAnInterval()
    {
        AccessReview review = AccessReview.create();
        review.configure("R", null, 7, null, ReviewFallback.KEEP, true, NOW);
        review.started(NOW, true);
        assertThat(review.getNextRunAt()).isNull();
        assertThat(review.isDue(NOW)).isFalse();

        review.configure("R", null, 7, null, ReviewFallback.KEEP, false, NOW);
        assertThat(review.isDue(NOW)).as("disabled").isFalse();
        review.started(NOW, true);
        assertThat(review.getNextRunAt()).isNull();
    }
}
