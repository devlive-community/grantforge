// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerTest
{
    static final TableRef ORDERS = new TableRef("internal", "sales", "orders");
    static final TableRef ITEMS = new TableRef("internal", "sales", "items");
    static final TableRef SALARIES = new TableRef("internal", "hr", "salaries");

    static Inventory inventory()
    {
        Map<String, List<String>> databases = Map.of("internal", List.of("sales", "hr"), "hive", List.of("lake"));
        Map<String, List<String>> tables = Map.of("internal.sales", List.of("items", "orders"), "internal.hr", List.of("salaries"), "hive.lake",
                List.of("events"));
        Map<String, List<String>> columns = Map.of("internal.sales.orders", List.of("id", "region", "card"), "internal.sales.items",
                List.of("order_id", "sku"), "internal.hr.salaries", List.of("employee", "amount"), "hive.lake.events", List.of("at"));
        return new Inventory(List.of("internal", "hive"), List.of("normal", "etl"), catalog -> databases.getOrDefault(catalog, List.of()),
                (catalog, database) -> tables.getOrDefault(catalog + "." + database, List.of()), table -> columns.getOrDefault(table.toString(), List.of()));
    }

    static Account account(String name, String... groups)
    {
        return new Account(name, List.of("'" + name + "'@'%'"), Set.of(groups), Map.of(), Map.of());
    }

    private static String[] column(String database, String table, String column)
    {
        return new String[] {"catalog", "internal", "database", database, "table", table, "column", column};
    }

    private final List<String> withheld = new ArrayList<>();

    private Plan plan(DorisSnapshots policies, Account account)
    {
        return plan(policies, account, catalog -> true);
    }

    private Plan plan(DorisSnapshots policies, Account account, Predicate<String> managed)
    {
        return new Planner(policies.snapshot(), inventory(), managed, Instant.now(), withheld::add).plan(account);
    }

    @Test
    void readsWholeCatalogsDatabasesTablesOrColumns()
    {
        assertThat(plan(new DorisSnapshots().allow(List.of("alice"), List.of("select"), "catalog", "hive"), account("alice")).privileges())
                .containsExactly(Privilege.catalog("SELECT_PRIV", "hive"));
        assertThat(plan(new DorisSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*")), account("alice")).privileges())
                .containsExactly(Privilege.database("SELECT_PRIV", "internal", "sales"));
        Plan bob = plan(new DorisSnapshots().allow(List.of("bob"), List.of("select"), column("sales", "*", "*"))
                .deny(List.of("bob"), List.of("select"), column("sales", "orders", "card")), account("bob"));
        assertThat(bob.privileges()).containsExactly(Privilege.table("SELECT_PRIV", ITEMS));
        assertThat(bob.columns()).containsOnlyKeys(ORDERS);
        assertThat(bob.columns().get(ORDERS)).containsExactly("id", "region");
    }

    @Test
    void groupsAreDorisRoles()
    {
        DorisSnapshots policies = new DorisSnapshots().allow(List.of("@analysts"), List.of("select"), column("hr", "*", "*"));
        assertThat(plan(policies, account("carol", "analysts")).privileges()).containsExactly(Privilege.database("SELECT_PRIV", "internal", "hr"));
        assertThat(plan(policies, account("dave")).empty()).isTrue();
    }

    @Test
    void grantsTablePrivilegesTableByTableButLoadingAndShowingViewsAlsoWhole()
    {
        Plan plan = plan(new DorisSnapshots().allow(List.of("alice"), List.of("load", "alter", "drop", "show_view"), "catalog", "internal",
                "database", "sales", "table", "*"), account("alice"));
        assertThat(plan.privileges()).contains(Privilege.database("LOAD_PRIV", "internal", "sales"), Privilege.database("SHOW_VIEW_PRIV", "internal",
                "sales"), Privilege.table("ALTER_PRIV", ORDERS), Privilege.table("DROP_PRIV", ORDERS), Privilege.table("ALTER_PRIV", ITEMS))
                .doesNotContain(Privilege.database("ALTER_PRIV", "internal", "sales"), Privilege.database("DROP_PRIV", "internal", "sales"));
    }

    @Test
    void createsInDatabasesAndDropsThemOnlyWhereDorisGivesNoMore()
    {
        Plan creator = plan(new DorisSnapshots().allow(List.of("alice"), List.of("create"), "catalog", "internal", "database", "sales"), account("alice"));
        assertThat(creator.privileges()).containsExactly(Privilege.database("CREATE_PRIV", "internal", "sales"));

        Plan everywhere = plan(new DorisSnapshots().allow(List.of("alice"), List.of("create"), "catalog", "internal", "database", "*")
                .allow(List.of("alice"), List.of("create_database"), "catalog", "internal"), account("alice"));
        assertThat(everywhere.privileges()).containsExactly(Privilege.catalog("CREATE_PRIV", "internal"));

        Plan narrow = plan(new DorisSnapshots().allow(List.of("alice"), List.of("create_database"), "catalog", "internal"), account("alice"));
        assertThat(narrow.privileges()).isEmpty();
        assertThat(withheld).anyMatch(reason -> reason.startsWith("creating databases in catalog internal for user alice"));

        Plan dropper = plan(new DorisSnapshots().allow(List.of("bob"), List.of("drop_database"), "catalog", "internal", "database", "sales")
                .allow(List.of("bob"), List.of("drop"), "catalog", "internal", "database", "sales", "table", "*"), account("bob"));
        assertThat(dropper.privileges()).containsExactly(Privilege.database("DROP_PRIV", "internal", "sales"));
        plan(new DorisSnapshots().allow(List.of("bob"), List.of("drop_database"), "catalog", "internal", "database", "hr"), account("bob"));
        assertThat(withheld).anyMatch(reason -> reason.startsWith("dropping database internal.hr for user bob"));
    }

    @Test
    void catalogsNotLookedAfterByNameGetWholeGrantsOnly()
    {
        Plan plan = plan(new DorisSnapshots().allow(List.of("alice"), List.of("select"), "catalog", "hive", "database", "lake", "table", "events",
                "column", "*"), account("alice"), "internal"::equals);
        assertThat(plan.empty()).isTrue();
        assertThat(plan(new DorisSnapshots().allow(List.of("alice"), List.of("select"), "catalog", "hive"), account("alice"), "internal"::equals)
                .privileges()).containsExactly(Privilege.catalog("SELECT_PRIV", "hive"));
    }

    @Test
    void filtersTheRowsOfTablesTheUserReads()
    {
        Plan plan = plan(new DorisSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"))
                .filter(List.of("alice"), "region = 'EU'", "sales", "orders").filter(List.of("alice"), "1 = 1", "hr", "salaries"), account("alice"));
        assertThat(plan.filters()).containsOnlyKeys(ORDERS);
        assertThat(plan.filters()).containsEntry(ORDERS, new Plan.Filter("region = 'EU'", 2));
    }

    @Test
    void grantsTheWorkloadGroupsPoliciesAllow()
    {
        assertThat(plan(new DorisSnapshots().allow(List.of("alice"), List.of("usage"), "workload_group", "*"), account("alice")).privileges())
                .as("by name, as no request can ask about groups to come").containsExactlyInAnyOrder(Privilege.workloadGroup("USAGE_PRIV", "normal"),
                        Privilege.workloadGroup("USAGE_PRIV", "etl"));
        assertThat(plan(new DorisSnapshots().allow(List.of("alice"), List.of("usage"), "workload_group", "et*"), account("alice")).privileges())
                .containsExactly(Privilege.workloadGroup("USAGE_PRIV", "etl"));
        assertThat(plan(new DorisSnapshots().allow(List.of("alice"), List.of("usage"), "workload_group", "*")
                .deny(List.of("alice"), List.of("usage"), "workload_group", "etl"), account("alice")).privileges())
                .containsExactly(Privilege.workloadGroup("USAGE_PRIV", "normal"));
    }
}
