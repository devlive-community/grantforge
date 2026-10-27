// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest
{
    private static TreeSet<String> set(String... values)
    {
        return new TreeSet<>(Set.of(values));
    }

    @Test
    void writesTheRoleTheSecurityPluginKeeps()
    {
        Plan plan = new Plan(set("cluster_composite_ops_ro"), List.of(new Plan.Entry(set("logs-*"), set("read"), set("name", "region"),
                set("card::SHA-256"), "{\"term\":{\"region\":\"EU\"}}"), new Plan.Entry(set("hr"), set("index", "read"), set(), set(), null)), List.of());
        assertThat(plan.empty()).isFalse();
        assertThat(plan.role("alice").toString()).isEqualTo("{\"description\":\"GrantForge agent role of user alice\","
                + "\"cluster_permissions\":[\"cluster_composite_ops_ro\"],\"index_permissions\":["
                + "{\"index_patterns\":[\"hr\"],\"allowed_actions\":[\"index\",\"read\"],\"fls\":[],\"masked_fields\":[]},"
                + "{\"index_patterns\":[\"logs-*\"],\"allowed_actions\":[\"read\"],\"fls\":[\"name\",\"region\"],\"masked_fields\":[\"card::SHA-256\"],"
                + "\"dls\":\"{\\\"term\\\":{\\\"region\\\":\\\"EU\\\"}}\"}],\"tenant_permissions\":[]}");
    }

    @Test
    void aPlanOfNothingNeedsNoRole()
    {
        assertThat(new Plan(set(), List.of(), List.of("why")).empty()).isTrue();
        assertThat(new Plan(set("cluster_monitor"), List.of(), List.of()).empty()).isFalse();
        Plan.Entry entry = new Plan.Entry(set("a"), set("read"), set(), set(), null);
        assertThat(new Plan(set(), List.of(entry), List.of()).empty()).isFalse();
        assertThat(entry).isEqualTo(new Plan.Entry(set("a"), set("read"), set(), set(), null));
    }
}
