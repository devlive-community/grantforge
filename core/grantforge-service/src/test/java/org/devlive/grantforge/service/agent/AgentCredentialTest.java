// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCredentialTest
{
    @Test
    void namesTheTenantServiceAndToken()
    {
        assertThat(new AgentCredential(1, 2, 3)).extracting(AgentCredential::tenantId, AgentCredential::serviceId, AgentCredential::tokenId)
                .containsExactly(1L, 2L, 3L);
    }
}
