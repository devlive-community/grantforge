// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.AgentStatus;
import org.devlive.grantforge.service.agent.AgentView;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResponseTest
{
    @Test
    void sendsTheIdAsText()
    {
        AgentResponse response = AgentResponse.from(new AgentView(7, 2, "hs2-1", null, null, 1L, null, Instant.EPOCH, AgentStatus.CURRENT));
        assertThat(response.id()).isEqualTo("7");
        assertThat(response.status()).isEqualTo(AgentStatus.CURRENT);
    }
}
