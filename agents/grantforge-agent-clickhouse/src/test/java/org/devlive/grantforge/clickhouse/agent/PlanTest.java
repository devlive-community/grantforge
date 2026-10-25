// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest
{
    @Test
    void replacesTheRolesPrivilegesInOneStatement()
    {
        Plan plan = new Plan(List.of(Grant.database("select", "b"), Grant.server("system")), Map.of());
        assertThat(plan.grantStatement("\"r\"")).isEqualTo("GRANT SYSTEM ON *.*, SELECT ON \"b\".* TO \"r\" WITH REPLACE OPTION");
        assertThat(plan.empty()).isFalse();
    }

    @Test
    void aPlanWithoutPrivilegesRevokesThemAll()
    {
        Map<TableRef, Plan.Filter> filters = new HashMap<>();
        filters.put(new TableRef("a", "t"), new Plan.Filter("x = 1", 7));
        Plan filtered = new Plan(List.of(), filters);
        assertThat(filtered.grantStatement("\"r\"")).isEqualTo("REVOKE ALL ON *.* FROM \"r\"");
        assertThat(filtered.empty()).isFalse();
        filters.clear();
        assertThat(filtered.filters()).hasSize(1);
        assertThat(Plan.NOTHING.empty()).isTrue();
    }
}
