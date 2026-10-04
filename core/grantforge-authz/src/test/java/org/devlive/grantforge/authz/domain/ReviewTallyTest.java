// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTallyTest
{
    @Test
    void countsItemsOfARound()
    {
        ReviewTally tally = new ReviewTally(3, ReviewDecision.REVOKE, ReviewOutcome.REVOKED, 4);

        assertThat(tally).extracting(ReviewTally::roundId, ReviewTally::decision, ReviewTally::outcome, ReviewTally::count)
                .containsExactly(3L, ReviewDecision.REVOKE, ReviewOutcome.REVOKED, 4L);
        assertThat(new ReviewTally(3, ReviewDecision.PENDING, null, 1).outcome()).isNull();
    }
}
