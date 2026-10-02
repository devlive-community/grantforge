// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTokenViewTest
{
    @Test
    void describesTheTokenWithoutItsSecret()
    {
        AgentTokenView view = new AgentTokenView(1, 2, "cluster", "gfa_abcdef", Instant.EPOCH, null, null, null, true);
        assertThat(view.toString()).contains("gfa_abcdef");
    }
}
