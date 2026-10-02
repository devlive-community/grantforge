// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.Heartbeat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeartbeatResponseTest
{
    @Test
    void copiesTheHeartbeat()
    {
        assertThat(HeartbeatResponse.from(new Heartbeat(4, 30))).isEqualTo(new HeartbeatResponse(4, 30));
    }
}
