// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.AgentReport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeartbeatRequestTest
{
    @Test
    void turnsIntoAReport()
    {
        assertThat(new HeartbeatRequest(" hs2-1 ", "h", "1.0", 3L).report()).isEqualTo(new AgentReport("hs2-1", "h", "1.0", 3L));
    }
}
