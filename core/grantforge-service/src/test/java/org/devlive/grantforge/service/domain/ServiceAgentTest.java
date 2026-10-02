// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceAgentTest
{
    @Test
    void remembersItsLastHeartbeat()
    {
        ServiceAgent agent = ServiceAgent.register(3, "nn-1");
        assertThat(agent.getLastSeenAt()).isEqualTo(Instant.EPOCH);
        agent.heartbeat(7, "10.0.0.1", "1.0", 4L, "10.0.0.1", Instant.parse("2026-10-02T12:00:00Z"));
        assertThat(agent).extracting(ServiceAgent::getServiceId, ServiceAgent::getInstance, ServiceAgent::getTokenId, ServiceAgent::getHost,
                ServiceAgent::getAgentVersion, ServiceAgent::getAppliedPolicyVersion, ServiceAgent::getClientIp, ServiceAgent::getLastSeenAt)
                .containsExactly(3L, "nn-1", 7L, "10.0.0.1", "1.0", 4L, "10.0.0.1", Instant.parse("2026-10-02T12:00:00Z"));
    }
}
