// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.AgentTokenView;
import org.devlive.grantforge.service.agent.IssuedToken;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IssuedTokenResponseTest
{
    @Test
    void carriesTheSecretButNeverPrintsIt()
    {
        IssuedTokenResponse response = IssuedTokenResponse.from(new IssuedToken(new AgentTokenView(1, 2, "c", "gfa_x", Instant.EPOCH,
                null, null, null, true), "gfa_xSECRET"));
        assertThat(response.secret()).isEqualTo("gfa_xSECRET");
        assertThat(response.toString()).doesNotContain("SECRET");
    }
}
