// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IssuedTokenTest
{
    @Test
    void neverPrintsTheSecret()
    {
        AgentTokenView view = new AgentTokenView(1, 2, "cluster", "gfa_abcdef", Instant.EPOCH, null, null, null, true);
        IssuedToken issued = new IssuedToken(view, "gfa_abcdefSECRET");
        assertThat(issued.toString()).contains("***").doesNotContain("SECRET");
        assertThat(issued.secret()).isEqualTo("gfa_abcdefSECRET");
    }
}
