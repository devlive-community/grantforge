// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewOutcomeTest
{
    @Test
    void namesEveryOutcome()
    {
        assertThat(ReviewOutcome.values()).containsExactly(ReviewOutcome.KEPT, ReviewOutcome.REVOKED, ReviewOutcome.GONE);
    }
}
