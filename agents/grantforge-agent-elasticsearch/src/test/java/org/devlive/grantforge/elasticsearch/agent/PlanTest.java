// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest
{
    @Test
    void writesTheRoleElasticsearchKeeps()
    {
        Plan plan = new Plan(new TreeSet<>(Set.of("monitor")), List.of(
                new Plan.Entry(new TreeSet<>(Set.of("logs-*")), new TreeSet<>(Set.of("read", "index")), new TreeSet<>(), null),
                new Plan.Entry(new TreeSet<>(Set.of("hr")), new TreeSet<>(Set.of("read")), new TreeSet<>(Set.of("employee", "address.city")),
                        "{\"term\": {\"region\": \"EU\"}}")), List.of());
        ObjectNode role = plan.role("alice");
        assertThat(role.path("description").asText()).isEqualTo("GrantForge agent role of user alice");
        assertThat(role.path("metadata").path("grantforge_user").asText()).isEqualTo("alice");
        assertThat(role.path("cluster").toString()).isEqualTo("[\"monitor\"]");
        assertThat(role.path("indices").path(0).toString()).as("sorted, with each field's multi-fields")
                .isEqualTo("{\"names\":[\"hr\"],\"privileges\":[\"read\"],\"field_security\":{\"grant\":[\"address.city\",\"address.city.*\",\"employee\","
                        + "\"employee.*\"]},\"query\":\"{\\\"term\\\": {\\\"region\\\": \\\"EU\\\"}}\",\"allow_restricted_indices\":false}");
        assertThat(role.path("indices").path(1).toString())
                .isEqualTo("{\"names\":[\"logs-*\"],\"privileges\":[\"index\",\"read\"],\"allow_restricted_indices\":false}");
        assertThat(plan.empty()).isFalse();
    }

    @Test
    void aPlanOfNothingNeedsNoRole()
    {
        assertThat(new Plan(new TreeSet<>(), List.of(), List.of("why")).empty()).isTrue();
    }
}
