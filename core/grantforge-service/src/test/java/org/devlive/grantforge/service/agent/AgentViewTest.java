// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentViewTest
{
    @Test
    void describesTheAgent()
    {
        AgentView view = new AgentView(1, 2, "hs2-1", null, null, null, null, Instant.EPOCH, AgentStatus.SILENT);
        assertThat(view.status()).isEqualTo(AgentStatus.SILENT);
    }
}
