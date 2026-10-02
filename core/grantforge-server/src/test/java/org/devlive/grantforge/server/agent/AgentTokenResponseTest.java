// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.AgentTokenView;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTokenResponseTest
{
    @Test
    void sendsIdsAsText()
    {
        AgentTokenResponse response = AgentTokenResponse.from(new AgentTokenView(9_007_199_254_740_993L, 2, "c", "gfa_x", Instant.EPOCH,
                null, null, null, true));
        assertThat(response.id()).isEqualTo("9007199254740993");
        assertThat(response.serviceId()).isEqualTo("2");
    }
}
