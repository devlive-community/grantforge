// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerTest
{
    private static final Inventory INVENTORY = new Inventory(Map.of("sales", List.of("items", "orders"), "hr", List.of("people", "salaries"),
            "empty", List.of()), database -> Map.of("orders", List.of("id", "region", "card"), "items", List.of("sku"), "people",
            List.of("name"), "salaries", List.of("amount")));

    private static Account user(String name, String... groups)
    {
        return new Account(name, Set.of(groups), Set.of(), true, List.of(), List.of());
    }

    private static List<String> grants(ClickHouseSnapshots policies, Account account)
    {
        return new Planner(policies.snapshot(), INVENTORY, Instant.now()).plan(account).grants().stream().map(Grant::sql).toList();
    }

    private static String[] column(String database, String table, String column)
    {
        return new String[] {"database", database, "table", table, "column", column};
    }

    @Test
    void grantsAWholeDatabaseWhenNothingBelowItIsDecidedOtherwise()
    {
        ClickHouseSnapshots policies = new ClickHouseSnapshots().allow(List.of("alice"), List.of("select", "insert"), column("sales", "*", "*"))
                .allow(List.of("alice"), List.of("alter"), "database", "sales", "table", "*");
        assertThat(grants(policies, user("alice"))).containsExactly("ALTER ON \"sales\".*", "INSERT ON \"sales\".*", "SELECT ON \"sales\".*");
        assertThat(grants(policies, user("bob"))).isEmpty();
    }

    @Test
    void grantsTableByTableAndColumnByColumnWherePoliciesTellThemApart()
    {
        ClickHouseSnapshots policies = new ClickHouseSnapshots().allow(List.of("bob"), List.of("select"), column("sales", "*", "*"))
                .deny(List.of("bob"), List.of("select"), column("sales", "orders", "card"))
                .allow(List.of("bob"), List.of("alter", "drop"), "database", "sales", "table", "*")
                .deny(List.of("bob"), List.of("drop"), "database", "sales", "table", "orders")
                .allow(List.of("bob"), List.of("insert"), column("sales", "orders", "region"));
        assertThat(grants(policies, user("bob"))).containsExactly("ALTER ON \"sales\".*", "DROP TABLE, DROP VIEW, DROP DICTIONARY ON \"sales\".\"items\"",
                "SELECT ON \"sales\".\"items\"", "INSERT(\"region\") ON \"sales\".\"orders\"", "SELECT(\"id\", \"region\") ON \"sales\".\"orders\"");
    }

    @Test
    void namesClickHouseRolesAsGroupsAndGrantsNothingWhereNoColumnIsAllowed()
    {
        ClickHouseSnapshots policies = new ClickHouseSnapshots().allow(List.of("@analysts"), List.of("select"), column("hr", "*", "*"))
                .deny(List.of("carol"), List.of("select"), column("hr", "salaries", "*"))
                .allow(List.of("carol"), List.of("insert"), column("hr", "salaries", "nothing"));
        assertThat(grants(policies, user("carol", "analysts"))).containsExactly("SELECT ON \"hr\".\"people\"");
        assertThat(grants(policies, user("carol"))).isEmpty();
    }

    @Test
    void grantsDatabaseAndServerPrivileges()
    {
        ClickHouseSnapshots policies = new ClickHouseSnapshots().allow(List.of("dave"), List.of("create", "drop_database"), "database", "empty")
                .allow(List.of("dave"), List.of("create_database", "kill_query"), "server", "*");
        assertThat(grants(policies, user("dave"))).containsExactly("CREATE DATABASE ON *.*", "KILL QUERY ON *.*",
                "CREATE TABLE, CREATE VIEW, CREATE DICTIONARY ON \"empty\".*", "DROP DATABASE ON \"empty\".*");
    }

    @Test
    void filtersTheRowsOfTablesTheUserMayRead()
    {
        ClickHouseSnapshots policies = new ClickHouseSnapshots().allow(List.of("erin"), List.of("select"), column("sales", "*", "*"))
                .allow(List.of("erin"), List.of("select"), column("hr", "people", "name"))
                .filter(List.of("erin"), "region = 'EU'", "sales", "orders").filter(List.of("erin"), "name != ''", "hr", "people")
                .filter(List.of("erin"), "1 = 1", "hr", "salaries");
        long first = 3;
        Plan plan = new Planner(policies.snapshot(), INVENTORY, Instant.now()).plan(user("erin"));
        assertThat(plan.filters()).containsOnlyKeys(new TableRef("sales", "orders"), new TableRef("hr", "people"));
        assertThat(plan.filters().get(new TableRef("sales", "orders"))).isEqualTo(new Plan.Filter("region = 'EU'", first));
    }
}
