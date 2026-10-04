// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.review;

import org.devlive.grantforge.authz.application.AccessReviewCommand;
import org.devlive.grantforge.authz.domain.ReviewFallback;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessReviewRequestTest
{
    @Test
    void fillsInTheDefaults()
    {
        AccessReviewCommand command = new AccessReviewRequest("Quarterly", null, List.of(" 3 ", "4"), 7, null, null, null, null).command();

        assertThat(command).extracting(AccessReviewCommand::name, AccessReviewCommand::durationDays, AccessReviewCommand::intervalDays,
                AccessReviewCommand::unreviewed, AccessReviewCommand::enabled, AccessReviewCommand::nextRunAt)
                .containsExactly("Quarterly", 7, null, ReviewFallback.KEEP, true, null);
        assertThat(command.roleIds()).containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    void takesEverythingGiven()
    {
        Instant next = Instant.parse("2026-07-01T00:00:00Z");
        AccessReviewCommand command = new AccessReviewRequest("Monthly", "Finance", List.of("3"), 3, 30, ReviewFallback.REVOKE, false, next).command();

        assertThat(command).extracting(AccessReviewCommand::description, AccessReviewCommand::intervalDays, AccessReviewCommand::unreviewed,
                AccessReviewCommand::enabled, AccessReviewCommand::nextRunAt).containsExactly("Finance", 30, ReviewFallback.REVOKE, false, next);
        assertThat(new AccessReviewRequest("R", null, null, null, null, null, null, null).command().durationDays()).isZero();
    }
}
