// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeartbeatTest
{
    @Test
    void carriesThePolicyVersionAndTheInterval()
    {
        assertThat(new Heartbeat(4, 30)).extracting(Heartbeat::policyVersion, Heartbeat::refreshSeconds).containsExactly(4L, 30L);
    }
}
