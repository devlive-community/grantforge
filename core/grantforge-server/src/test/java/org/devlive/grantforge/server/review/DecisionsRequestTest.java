// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.review;

import org.devlive.grantforge.authz.domain.ReviewDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionsRequestTest
{
    @Test
    void parsesTheItems()
    {
        DecisionsRequest request = new DecisionsRequest(List.of("5", " 6"), ReviewDecision.REVOKE, "left");

        assertThat(request.items()).containsExactly(5L, 6L);
        assertThat(request.chosen()).isEqualTo(ReviewDecision.REVOKE);
        assertThat(new DecisionsRequest(null, null, null).items()).isEmpty();
        assertThat(new DecisionsRequest(null, null, null).chosen()).isEqualTo(ReviewDecision.PENDING);
    }
}
