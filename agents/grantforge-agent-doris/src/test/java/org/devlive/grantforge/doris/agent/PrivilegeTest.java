// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrivilegeTest
{
    @Test
    void namesItsObjectAsShowRolesAndGrantDo()
    {
        Privilege catalog = Privilege.catalog("create_priv", "internal");
        assertThat(catalog.privilege()).isEqualTo("CREATE_PRIV");
        assertThat(catalog.target()).isEqualTo("internal.*.*");
        assertThat(catalog.on()).isEqualTo("`internal`.*.*");
        assertThat(catalog.level()).isEqualTo("catalog");

        Privilege database = Privilege.database("SELECT_PRIV", "internal", "sales");
        assertThat(database.target()).isEqualTo("internal.sales.*");
        assertThat(database.on()).isEqualTo("`internal`.`sales`.*");
        assertThat(database.level()).isEqualTo("database");

        Privilege table = Privilege.table("LOAD_PRIV", new TableRef("internal", "sales", "orders"));
        assertThat(table.on()).isEqualTo("`internal`.`sales`.`orders`");
        assertThat(table.level()).isEqualTo("table");
        assertThat(table).hasToString("LOAD_PRIV ON internal.sales.orders");

        Privilege group = Privilege.workloadGroup("USAGE_PRIV", "etl's");
        assertThat(group.on()).isEqualTo("WORKLOAD GROUP 'etl\\'s'");
        assertThat(group.level()).isEqualTo("workload_group");
        assertThat(group).hasToString("USAGE_PRIV ON WORKLOAD GROUP etl's");
    }

    @Test
    void cannotNameATargetWhoseNamesHoldDots()
    {
        assertThat(new Privilege("SELECT_PRIV", "internal.sales.v1.x", false).on()).isNull();
    }

    @Test
    void quotesNamesAndLiterals()
    {
        assertThat(Privilege.quote("a`b")).isEqualTo("`a``b`");
        assertThat(Privilege.literal("it's \\ fine")).isEqualTo("'it\\'s \\\\ fine'");
    }

    @Test
    void sortsWorkloadGroupsLast()
    {
        List<Privilege> sorted = List.of(Privilege.workloadGroup("USAGE_PRIV", "a"), Privilege.table("SELECT_PRIV", new TableRef("i", "d", "t")),
                Privilege.catalog("SELECT_PRIV", "i")).stream().sorted(Privilege.ORDER).toList();
        assertThat(sorted).extracting(Privilege::target).containsExactly("i.*.*", "i.d.t", "a");
    }
}
