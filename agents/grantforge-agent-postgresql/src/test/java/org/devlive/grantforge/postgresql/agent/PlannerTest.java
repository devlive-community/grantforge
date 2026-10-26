// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class PlannerTest
{
    private static final TableRef ORDERS = ServerStateTest.ORDERS;
    private static final TableRef REGIONS = new TableRef("crm", "regions");
    private static final TableRef SALARIES = new TableRef("public", "salaries");

    private final ServerState state = ServerStateTest.read(ServerStateTest.server());

    private Plan plan(PostgreSqlSnapshots policies, String user)
    {
        Account account = state.accounts().stream().filter(candidate -> candidate.name().equals(user)).findFirst().orElseThrow();
        return new Planner(policies.snapshot(), state.databases(), Instant.now()).plan(account);
    }

    private static String[] column(String database, String schema, String table, String column)
    {
        return new String[] {"database", database, "schema", schema, "table", table, "column", column};
    }

    @Test
    void grantsWholeTablesWithWhatReachingThemNeeds()
    {
        Plan plan = plan(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "crm", "*", "*")), "alice");
        assertThat(plan.privileges("sales")).containsExactlyInAnyOrder(Privilege.database("sales", "CONNECT"), Privilege.schema("crm", "USAGE"),
                Privilege.table(ORDERS, "SELECT"), Privilege.table(REGIONS, "SELECT"));
        assertThat(plan.privileges("hr")).isEmpty();
        assertThat(plan.filters()).isEmpty();
        assertThat(plan(new PostgreSqlSnapshots(), "alice").empty()).isTrue();
    }

    @Test
    void grantsColumnsWhenPoliciesTellThemApart()
    {
        Plan plan = plan(new PostgreSqlSnapshots().allow(List.of("bob"), List.of("select", "update"), column("sales", "crm", "orders", "*"))
                .deny(List.of("bob"), List.of("select", "update"), column("sales", "crm", "orders", "card")), "bob");
        assertThat(plan.privileges("sales")).contains(Privilege.column(ORDERS, "id", "SELECT"), Privilege.column(ORDERS, "region", "SELECT"),
                Privilege.column(ORDERS, "id", "UPDATE")).doesNotContain(Privilege.table(ORDERS, "SELECT"), Privilege.column(ORDERS, "card", "SELECT"));
    }

    @Test
    void insertingIntoASerialColumnComesWithItsSequence()
    {
        Plan plan = plan(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("insert"), column("sales", "crm", "orders", "*")), "alice");
        assertThat(plan.privileges("sales")).contains(Privilege.table(ORDERS, "INSERT"),
                Privilege.sequence(new TableRef("crm", "orders_id_seq"), "USAGE"));
        Plan without = plan(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("insert"), column("sales", "crm", "orders", "region")), "alice");
        assertThat(without.privileges("sales")).contains(Privilege.column(ORDERS, "region", "INSERT"))
                .noneMatch(privilege -> privilege.kind() == Privilege.Kind.SEQUENCE);
    }

    @Test
    void grantsTableSchemaAndDatabasePrivileges()
    {
        Plan plan = plan(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("delete", "truncate", "trigger"), "database", "sales", "schema",
                "crm", "table", "orders").allow(List.of("alice"), List.of("create"), "database", "sales", "schema", "*")
                .allow(List.of("alice"), List.of("create_schema", "temporary"), "database", "hr"), "alice");
        assertThat(plan.privileges("sales")).contains(Privilege.table(ORDERS, "DELETE"), Privilege.table(ORDERS, "TRUNCATE"),
                Privilege.table(ORDERS, "TRIGGER"), Privilege.schema("crm", "CREATE"), Privilege.schema("public", "CREATE"),
                Privilege.schema("public", "USAGE"));
        assertThat(plan.privileges("hr")).containsExactlyInAnyOrder(Privilege.database("hr", "CONNECT"), Privilege.database("hr", "CREATE"),
                Privilege.database("hr", "TEMPORARY"));
    }

    @Test
    void groupsAreTheRolesAUserIsAMemberOf()
    {
        Plan plan = plan(new PostgreSqlSnapshots().allow(List.of("@readers"), List.of("select"), column("hr", "public", "*", "*")), "carol");
        assertThat(plan.privileges("hr")).contains(Privilege.table(SALARIES, "SELECT"));
    }

    @Test
    void filtersTheRowsOfTablesAndKeepsViewsAFilterWouldApplyToUnreadable()
    {
        Plan plan = plan(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*", "*"))
                .filter(List.of("alice"), "region = 'EU'", "sales", "crm", "orders").filter(List.of("alice"), "true", "sales", "crm", "regions"), "alice");
        assertThat(plan.filters("sales")).containsOnlyKeys(ORDERS);
        assertThat(requireNonNull(plan.filters("sales").get(ORDERS), "filter").expression()).isEqualTo("region = 'EU'");
        assertThat(plan.privileges("sales")).contains(Privilege.table(ORDERS, "SELECT")).doesNotContain(Privilege.table(REGIONS, "SELECT"));
        assertThat(plan.notes()).singleElement().asString().contains("sales.crm.regions", "cannot filter");
        Plan unread = plan(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("insert"), column("sales", "crm", "orders", "*"))
                .filter(List.of("alice"), "region = 'EU'", "sales", "crm", "orders"), "alice");
        assertThat(unread.filters()).as("a filter of a table the user does not read").isEmpty();
        assertThat(Set.copyOf(unread.notes())).isEmpty();
    }
}
