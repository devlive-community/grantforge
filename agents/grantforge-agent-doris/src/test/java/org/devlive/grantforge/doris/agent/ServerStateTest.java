// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServerStateTest
{
    static final TableRef ORDERS = new TableRef("internal", "sales", "orders");

    /** A cluster with the agent's role of alice, another role, users of each kind, a row policy and two databases. */
    static FakeDoris cluster()
    {
        return new FakeDoris().answer("SHOW GRANTS", Row.of("UserIdentity", "'grantforge'@'%'"))
                .answer("SHOW ROLES", Row.of("Name", "grantforge_alice", "CatalogPrivs", "internal.*.*: Create_priv", "DatabasePrivs",
                        "internal.sales.*: Select_priv", "TablePrivs", "internal.sales.orders: Load_priv,Alter_priv; internal.sales.v1: ",
                        "WorkloadGroupPrivs", "normal: Usage_priv"), Row.of("Name", "analysts", "TablePrivs", "internal.hr.salaries: Select_priv",
                        "CatalogPrivs", "NULL", "DatabasePrivs", "NULL", "WorkloadGroupPrivs", "NULL"))
                .answer("SHOW ALL GRANTS", Row.of("UserIdentity", "'root'@'%'", "GlobalPrivs", "Node_priv,Admin_priv"),
                        Row.of("UserIdentity", "'grantforge'@'%'", "GlobalPrivs", "NULL", "Roles", ""),
                        Row.of("UserIdentity", "'boss'@'%'", "GlobalPrivs", "Admin_priv"),
                        Row.of("UserIdentity", "'etl'@'%'", "GlobalPrivs", "NULL", "Roles", ""),
                        Row.of("UserIdentity", "'alice'@'%'", "GlobalPrivs", "NULL", "Roles", "analysts,grantforge_alice", "CatalogPrivs", "internal: Create_priv",
                                "DatabasePrivs", "internal.mysql: Select_priv; internal.sales: Select_priv", "TablePrivs", "NULL",
                                "ColPrivs", "internal.sales.orders: Select_priv[Region, id]", "WorkloadGroupPrivs", "normal: Usage_priv"),
                        Row.of("UserIdentity", "'alice'@'10.%'", "GlobalPrivs", "NULL", "Roles", "", "ColPrivs", "NULL"),
                        Row.of("UserIdentity", "garbage", "GlobalPrivs", "NULL"))
                .answer("SHOW ROW POLICY", Row.of("PolicyName", "grantforge_alice", "CatalogName", "internal", "DbName", "sales", "TableName", "orders",
                        "FilterType", "PERMISSIVE", "WherePredicate", "(`Region` = 'EU')", "User", "NULL", "Role", "grantforge_alice"),
                        Row.of("PolicyName", "old", "DbName", "hr", "TableName", "people", "FilterType", "RESTRICTIVE", "WherePredicate", "(1 = 0)",
                                "User", "'bob'@'%'", "Role", ""))
                .answer("SHOW CATALOGS", Row.of("CatalogId", 0, "CatalogName", "internal"), Row.of("CatalogId", 1, "CatalogName", "hive"))
                .answer("SHOW WORKLOAD GROUPS", Row.of("Id", 1, "Name", "normal"), Row.of("Id", 1, "Name", "normal"), Row.of("Id", 2, "Name", "etl"))
                .answer("SHOW DATABASES FROM `internal`", Row.of("Database", "sales"), Row.of("Database", "information_schema"),
                        Row.of("Database", "__internal_schema"))
                .answer("SHOW TABLES FROM `internal`.`sales`", Row.of("Tables_in_sales", "orders"))
                .answer("SHOW COLUMNS FROM `internal`.`sales`.`orders`", Row.of("Field", "id"), Row.of("Field", "Region"));
    }

    @Test
    void readsUsersRolesGrantsAndRowPolicies()
    {
        ServerState state = ServerState.read(cluster(), "grantforge_", Set.of("etl"));
        assertThat(state.self()).isEqualTo("grantforge");
        assertThat(state.superusers()).as("root is Doris's own").containsExactly("boss");
        assertThat(state.accounts()).singleElement().satisfies(alice -> {
            assertThat(alice.name()).isEqualTo("alice");
            assertThat(alice.identities()).containsExactly("'alice'@'%'", "'alice'@'10.%'");
            assertThat(alice.groups()).containsExactly("analysts");
            assertThat(alice.granted().get("'alice'@'%'")).containsExactlyInAnyOrder("analysts", "grantforge_alice");
            assertThat(alice.readableAsShown(ORDERS)).containsExactlyInAnyOrder("Region", "id");
        });
        assertThat(state.roles().get("grantforge_alice")).containsExactlyInAnyOrder(Privilege.catalog("CREATE_PRIV", "internal"),
                Privilege.database("SELECT_PRIV", "internal", "sales"), Privilege.table("LOAD_PRIV", ORDERS), Privilege.table("ALTER_PRIV", ORDERS),
                Privilege.workloadGroup("USAGE_PRIV", "normal"));
        assertThat(state.otherRoles()).containsOnlyKeys("analysts");
        assertThat(state.userPrivileges().get("'alice'@'%'")).contains(Privilege.catalog("CREATE_PRIV", "internal"),
                Privilege.database("SELECT_PRIV", "internal", "sales"), Privilege.workloadGroup("USAGE_PRIV", "normal"));
        ServerState.RowPolicy policy = state.rowPolicies().get(new ServerState.RowPolicyKey("grantforge_alice", ORDERS));
        assertThat(policy).isEqualTo(new ServerState.RowPolicy(false, "(`Region` = 'EU')", null, "grantforge_alice"));
        assertThat(state.rowPolicies().get(new ServerState.RowPolicyKey("old", new TableRef("internal", "hr", "people"))))
                .as("a release without catalogs names the internal one").isEqualTo(new ServerState.RowPolicy(true, "(1 = 0)", "'bob'@'%'", null));
        assertThat(state.inventory().catalogs()).containsExactly("internal", "hive");
        assertThat(state.inventory().workloadGroups()).containsExactly("normal", "etl");
        assertThat(state.inventory().databases("internal")).containsExactly("sales");
        assertThat(state.inventory().tables("internal", "sales")).containsExactly("orders");
        assertThat(state.inventory().columns(ORDERS)).containsExactly("id", "Region");
    }

    @Test
    void aTableDroppedSinceItsDatabaseWasReadHasNoColumns()
    {
        FakeDoris doris = cluster().fail("SHOW COLUMNS", false);
        assertThat(ServerState.read(doris, "grantforge_", Set.of()).inventory().columns(ORDERS)).isEmpty();
    }

    @Test
    void readsIdentitiesNamesAndPrivileges()
    {
        assertThat(ServerState.name("'alice'@'%'")).isEqualTo("alice");
        assertThat(ServerState.name("'o\\'brien'@['example.com']")).isEqualTo("o'brien");
        assertThat(ServerState.name("alice")).isEmpty();
        assertThat(ServerState.names(" a, ,b,NULL")).containsExactly("a", "b");
        assertThat(ServerState.entries("internal.sales.orders: Load_priv,Alter_priv; normal: Usage_priv"))
                .containsExactly(Map.entry("internal.sales.orders", List.of("Load_priv", "Alter_priv")), Map.entry("normal", List.of("Usage_priv")));
        assertThat(ServerState.entries("NULL")).isEmpty();
        assertThat(ServerState.entries("nonsense")).isEmpty();
        assertThat(ServerState.columnPrivileges("internal.sales.orders: Select_priv[Region, id]; internal.x: Select_priv[a]; i.d.t: Other[b]"))
                .containsExactly(Map.entry(ORDERS, Set.of("Region", "id")));
        assertThat(ServerState.columnPrivileges("NULL")).isEmpty();
        assertThat(ServerState.columnPrivileges("internal.sales.orders Select_priv[a]")).isEmpty();
    }
}
