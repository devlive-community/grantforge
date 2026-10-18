// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void creditsGrantForgeWhenItsAgentEnforcesStrictDefaultDeny()
    {
        Map<String, Object> fields = AccessEvent.builder("bob", "/data", "read", false).decidedBy(AgentDecision.notDetermined(4))
                .enforcedByGrantForge().build().fields();

        assertThat(fields).containsEntry("outcome", "DENIED").containsEntry("enforcer", "GRANTFORGE")
                .containsEntry("policyId", null).containsEntry("policyVersion", 4L);
        assertThat(AccessEvent.builder("bob", "/data", "read", false).enforcedByGrantForge().build().fields())
                .containsEntry("enforcer", "GRANTFORGE").containsEntry("policyId", null).containsEntry("policyVersion", null);
    }

    @Test
    void foldsIdenticalAccessesIntoANewEvent()
    {
        Instant first = Instant.parse("2026-10-17T10:00:00Z");
        AccessEvent event = AccessEvent.builder("alice", "orders", "write", true).decidedBy(AgentDecision.allowed(4, 11)).occurredAt(first)
                .clientIp("10.0.0.7").resourceType("topic").action("PRODUCE").build();
        assertThat(event.count()).isEqualTo(1);
        assertThat(event.allowed()).isTrue();
        assertThat(event.fields()).doesNotContainKeys("count", "lastOccurredAt");

        AccessEvent folded = event.folded(1200, first.plusSeconds(59));
        assertThat(folded.count()).isEqualTo(1200);
        assertThat(folded.eventId()).isNotEqualTo(event.eventId()).hasSize(36);
        assertThat(folded.fields()).containsEntry("count", 1200).containsEntry("lastOccurredAt", "2026-10-17T10:00:59Z")
                .containsEntry("occurredAt", "2026-10-17T10:00:00Z").containsEntry("user", "alice").containsEntry("policyId", "11")
                .containsEntry("action", "PRODUCE");
        assertThat(event.folded(1, first.plusSeconds(5)).fields()).doesNotContainKeys("count", "lastOccurredAt");
        assertThatThrownBy(() -> event.folded(0, first)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identicalAccessesShareTheirFoldingKey()
    {
        AccessEvent.Builder produce = AccessEvent.builder("alice", "orders", "write", true).clientIp("10.0.0.7").action("PRODUCE");
        String key = produce.occurredAt(Instant.EPOCH).build().foldingKey();
        assertThat(produce.occurredAt(Instant.EPOCH.plusSeconds(9)).build().foldingKey()).as("the moment does not count").isEqualTo(key);
        assertThat(AccessEvent.builder("alice", "orders", "write", true).clientIp("10.0.0.8").action("PRODUCE").build().foldingKey())
                .isNotEqualTo(key);
        assertThat(AccessEvent.builder("alice", "orders", "write", false).clientIp("10.0.0.7").action("PRODUCE").build().foldingKey())
                .isNotEqualTo(key);
        assertThat(AccessEvent.builder("bob", "orders", "write", true).clientIp("10.0.0.7").action("PRODUCE").build().foldingKey())
                .isNotEqualTo(key);
    }
}
