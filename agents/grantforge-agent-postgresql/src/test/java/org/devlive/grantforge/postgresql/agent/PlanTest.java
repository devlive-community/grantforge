// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest
{
    @Test
    void holdsPrivilegesAndFiltersByDatabase()
    {
        TableRef orders = new TableRef("crm", "orders");
        TableRef items = new TableRef("crm", "items");
        Plan plan = new Plan(Map.of("sales", Set.of(Privilege.table(orders, "SELECT")), "hr", Set.of()),
                Map.of("sales", Map.of(orders, new Plan.Filter("x", 1), items, new Plan.Filter("y", 2)), "hr", Map.of()), List.of("note"));
        assertThat(plan.empty()).isFalse();
        assertThat(plan.privileges()).as("a database without privileges is left out").containsOnlyKeys("sales");
        assertThat(plan.privileges("sales")).containsExactly(Privilege.table(orders, "SELECT"));
        assertThat(plan.privileges("hr")).isEmpty();
        assertThat(plan.filters("sales").keySet()).as("sorted by table").containsExactly(items, orders);
        assertThat(plan.filters("hr")).isEmpty();
        assertThat(plan.notes()).containsExactly("note");
        assertThat(Plan.NOTHING.empty()).isTrue();
        assertThat(new Plan(Map.of(), Map.of("sales", Map.of(orders, new Plan.Filter("x", 1))), List.of()).empty()).isFalse();
    }
}
