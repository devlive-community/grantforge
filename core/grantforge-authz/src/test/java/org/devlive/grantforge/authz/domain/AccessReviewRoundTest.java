// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessReviewRoundTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void opensAndEndsOnce()
    {
        AccessReviewRound round = AccessReviewRound.open(4, 7L, NOW, NOW.plusSeconds(60));
        assertThat(round).extracting(AccessReviewRound::getReviewId, AccessReviewRound::getStartedBy, AccessReviewRound::getStartedAt,
                AccessReviewRound::getDueAt, AccessReviewRound::getStatus).containsExactly(4L, 7L, NOW, NOW.plusSeconds(60), ReviewRoundStatus.OPEN);

        assertThatThrownBy(() -> round.end(ReviewRoundStatus.OPEN, 7L, NOW)).isInstanceOf(IllegalArgumentException.class);
        round.end(ReviewRoundStatus.COMPLETED, null, NOW.plusSeconds(60));
        assertThat(round).extracting(AccessReviewRound::getStatus, AccessReviewRound::getEndedBy, AccessReviewRound::getEndedAt)
                .containsExactly(ReviewRoundStatus.COMPLETED, null, NOW.plusSeconds(60));
        assertThatThrownBy(() -> round.end(ReviewRoundStatus.CANCELLED, 7L, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mustBeDueAfterItStarts()
    {
        assertThatThrownBy(() -> AccessReviewRound.open(4, null, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        AccessReviewRound round = AccessReviewRound.open(4, null, NOW, NOW.plusSeconds(1));
        round.end(ReviewRoundStatus.CANCELLED, 9L, NOW);
        assertThat(round.getStatus()).isEqualTo(ReviewRoundStatus.CANCELLED);
        assertThat(round.getStartedBy()).isNull();
    }
}
