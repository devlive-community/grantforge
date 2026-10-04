// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewDecisionTest
{
    @Test
    void namesEveryDecision()
    {
        assertThat(ReviewDecision.values()).containsExactly(ReviewDecision.PENDING, ReviewDecision.KEEP, ReviewDecision.REVOKE);
    }
}
