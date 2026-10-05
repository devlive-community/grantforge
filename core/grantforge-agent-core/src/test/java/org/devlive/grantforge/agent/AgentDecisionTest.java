// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDecisionTest
{
    @Test
    void saysWhoDecidedAndWithWhichVersion()
    {
        AgentDecision allowed = AgentDecision.allowed(4, 11);
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.determined()).isTrue();
        assertThat(allowed.policyId()).isEqualTo(11L);
        assertThat(allowed.policyVersion()).isEqualTo(4L);
        assertThat(allowed).hasToString("ALLOWED by policy 11 at version 4");

        AgentDecision denied = AgentDecision.denied(4, 12);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.determined()).isTrue();
        assertThat(denied.outcome()).isEqualTo(AgentDecision.Outcome.DENIED);

        AgentDecision open = AgentDecision.notDetermined(4);
        assertThat(open.determined()).isFalse();
        assertThat(open.policyId()).isNull();
        assertThat(open).hasToString("NOT_DETERMINED at version 4");

        AgentDecision none = AgentDecision.withoutSnapshot();
        assertThat(none.outcome()).isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
        assertThat(none.policyVersion()).isNull();
        assertThat(none).hasToString("NOT_DETERMINED");
    }
}
