// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccessEventTest
{
    @Test
    void writesTheFieldsTheServerTakes()
    {
        AccessEvent event = AccessEvent.builder("alice", "sales.orders.id", "select", true).decidedBy(AgentDecision.allowed(4, 11))
                .occurredAt(Instant.parse("2026-10-05T01:02:03Z")).clientIp("10.1.2.3").resourceType("column").action("QUERY")
                .request("SELECT id FROM orders").build();

        Map<String, Object> fields = event.fields();
        assertThat(fields).containsEntry("eventId", event.eventId()).containsEntry("occurredAt", "2026-10-05T01:02:03Z")
                .containsEntry("user", "alice").containsEntry("clientIp", "10.1.2.3").containsEntry("resource", "sales.orders.id")
                .containsEntry("resourceType", "column").containsEntry("accessType", "select").containsEntry("action", "QUERY")
                .containsEntry("outcome", "ALLOWED").containsEntry("policyId", "11").containsEntry("policyVersion", 4L)
                .containsEntry("enforcer", "GRANTFORGE").containsEntry("request", "SELECT id FROM orders");
        assertThat(event.eventId()).hasSize(36);
    }

    @Test
    void creditsTheSystemWhenGrantForgeDidNotDecide()
    {
        Map<String, Object> fields = AccessEvent.builder("bob", "/data", "read", false).decidedBy(AgentDecision.notDetermined(4))
                .request("x".repeat(1500)).build().fields();

        assertThat(fields).containsEntry("outcome", "DENIED").containsEntry("enforcer", "NATIVE").containsEntry("policyId", null)
                .containsEntry("policyVersion", 4L).containsEntry("clientIp", null);
        assertThat((String) fields.get("request")).hasSize(AccessEvent.MAX_REQUEST);
        assertThat(AccessEvent.builder("bob", "/data", "read", true).build().fields()).containsEntry("enforcer", "NATIVE")
                .containsEntry("request", null);
        assertThat(AccessEvent.builder("a", "r", "t", true).build().eventId())
                .isNotEqualTo(AccessEvent.builder("a", "r", "t", true).build().eventId());
    }
}
