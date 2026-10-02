// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.Ingested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngestedResponseTest
{
    @Test
    void copiesTheCounts()
    {
        assertThat(IngestedResponse.from(new Ingested(1, 2, 3))).isEqualTo(new IngestedResponse(1, 2, 3));
    }
}
