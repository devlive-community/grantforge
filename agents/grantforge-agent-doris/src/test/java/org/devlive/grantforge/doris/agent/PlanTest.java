// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest
{
    private static final TableRef ORDERS = new TableRef("internal", "sales", "orders");
    private static final TableRef ITEMS = new TableRef("internal", "sales", "items");

    @Test
    void sortsAndCopiesWhatItGives()
    {
        Plan plan = new Plan(List.of(Privilege.workloadGroup("USAGE_PRIV", "g"), Privilege.table("SELECT_PRIV", ORDERS),
                Privilege.table("SELECT_PRIV", ORDERS)), Map.of(ORDERS, new TreeSet<>(List.of("b", "a")), ITEMS, new TreeSet<>()),
                Map.of(ORDERS, new Plan.Filter("region = 'EU'", 7)));
        assertThat(plan.privileges()).as("once each, workload groups last").extracting(Privilege::target).containsExactly("internal.sales.orders", "g");
        assertThat(plan.columns()).as("a table without columns gives nothing").containsOnlyKeys(ORDERS);
        assertThat(plan.columns().get(ORDERS)).containsExactly("a", "b");
        assertThat(plan.filters()).containsEntry(ORDERS, new Plan.Filter("region = 'EU'", 7));
        assertThat(plan.privilegeSet()).hasSize(2);
        assertThat(plan.empty()).isFalse();
    }

    @Test
    void givesNothingWhenEmpty()
    {
        assertThat(Plan.NOTHING.empty()).isTrue();
        assertThat(new Plan(List.of(), Map.of(), Map.of(ORDERS, new Plan.Filter("1", 1))).empty()).as("a filter alone needs a role").isFalse();
        assertThat(new Plan(List.of(), Map.of(ORDERS, new TreeSet<>(List.of("id"))), Map.of()).empty()).isFalse();
    }
}
