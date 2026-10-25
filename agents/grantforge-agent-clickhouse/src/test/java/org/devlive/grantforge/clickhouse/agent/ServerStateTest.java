// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class ServerStateTest
{
    static FakeClickHouse server()
    {
        return new FakeClickHouse().answer("currentUser()", Row.of("user", "grantforge"))
                .answer("system.role_grants", Row.of("user_name", "alice", "role_name", null, "granted_role_name", "analysts"),
                        Row.of("user_name", null, "role_name", "analysts", "granted_role_name", "readers"),
                        Row.of("user_name", null, "role_name", "readers", "granted_role_name", "analysts"),
                        Row.of("user_name", "alice", "role_name", null, "granted_role_name", "grantforge_alice"),
                        Row.of("user_name", null, "role_name", "grantforge_alice", "granted_role_name", "hidden"),
                        Row.of("user_name", null, "role_name", null, "granted_role_name", "lost"))
                .answer("system.users AS u", Row.of("name", "admin", "directory", "users_xml"),
                        Row.of("name", "alice", "directory", "local_directory", "default_all", 1, "default_list", List.of(), "default_except",
                                List.of("analysts")),
                        Row.of("name", "carol", "directory", "replicated", "default_all", 0, "default_list", List.of("x"), "default_except", List.of()),
                        Row.of("name", "etl", "directory", "local_directory"), Row.of("name", "grantforge", "directory", "local_directory"),
                        Row.of("name", "ldap", "directory", null))
                .answer("FROM system.roles", Row.of("name", "grantforge_alice"))
                .answer("system.grants WHERE startsWith", Row.of("role_name", "grantforge_alice", "access_type", "SELECT", "database", "sales"),
                        Row.of("role_name", "grantforge_alice", "access_type", "ALTER", "database", "sales", "table", "orders", "grant_option", 0))
                .answer("system.row_policies", Row.of("short_name", "grantforge_alice", "database", "sales", "table", "orders", "select_filter",
                        "region = 'EU'", "is_restrictive", 0, "apply_to_all", 0, "apply_to_list", List.of("grantforge_alice"), "apply_to_except", List.of()))
                .answer("NOT is_partial_revoke", Row.of("user_name", "alice", "role_name", null, "access_type", "SELECT", "database", "hr", "table",
                        "salaries", "column", null), Row.of("user_name", null, "role_name", "readers", "access_type", "ALL"))
                .answer("system.databases", Row.of("name", "sales"), Row.of("name", "hr"))
                .answer("system.tables", Row.of("database", "sales", "name", "orders"))
                .answer("system.columns", values -> "sales".equals(values.get("database"))
                        ? List.of(Row.of("table", "orders", "name", "id"), Row.of("table", "orders", "name", "region")) : List.of());
    }

    @Test
    void readsTheUsersItKeepsRolesForAndThoseItCannotChange()
    {
        ServerState state = ServerState.read(server(), "grantforge_", Set.of("etl"));
        assertThat(state.self()).isEqualTo("grantforge");
        assertThat(state.accounts()).extracting(Account::name).containsExactly("alice", "carol");
        assertThat(state.readOnly()).containsExactly("admin", "ldap");
        Account alice = state.accounts().get(0);
        assertThat(alice.groups()).as("held through roles, the agent's own left out").containsExactlyInAnyOrder("analysts", "readers");
        assertThat(alice.granted()).containsExactlyInAnyOrder("analysts", "grantforge_alice");
        assertThat(alice.defaultAll()).isTrue();
        assertThat(alice.defaultExcept()).containsExactly("analysts");
        Account carol = state.accounts().get(1);
        assertThat(carol.groups()).isEmpty();
        assertThat(carol.defaultRoles()).containsExactly("x");
    }

    @Test
    void readsTheAgentsRolesRowPoliciesAndTheGrantsItDidNotMake()
    {
        ServerState state = ServerState.read(server(), "grantforge_", Set.of());
        assertThat(state.roles()).containsExactly("grantforge_alice");
        assertThat(state.privileges()).containsOnlyKeys("grantforge_alice");
        assertThat(state.privileges().get("grantforge_alice")).as("sorted, so that it changes only with the privileges")
                .startsWith("ALTER\u001fsales\u001forders").contains("SELECT\u001fsales\u001fnull");
        ServerState.RowPolicy policy = requireNonNull(state.rowPolicies().get(new ServerState.RowPolicyKey("grantforge_alice", new TableRef("sales", "orders"))), "policy");
        assertThat(policy.filter()).isEqualTo("region = 'EU'");
        assertThat(policy.onlyFor("grantforge_alice")).isTrue();
        assertThat(state.nativeGrants()).extracting(ServerState.NativeGrant::text).containsExactly("SELECT ON hr.salaries", "ALL ON *.*");
        assertThat(state.inventory().databases()).containsExactly("hr", "sales");
        assertThat(state.inventory().tables("sales")).containsExactly("orders");
        assertThat(state.inventory().columns(new TableRef("sales", "orders"))).containsExactly("id", "region");
        assertThat(state.inventory().columns(new TableRef("hr", "x"))).isEmpty();
    }

    @Test
    void readsNoGrantsWithoutUsersToKeep()
    {
        FakeClickHouse server = server().answer("system.users AS u", Row.of("name", "admin", "directory", "users_xml"));
        assertThat(ServerState.read(server, "grantforge_", Set.of()).nativeGrants()).isEmpty();
    }

    @Test
    void aRowPolicyIsTheRolesAloneWhenPermissiveAndForItOnly()
    {
        assertThat(new ServerState.RowPolicy("x", true, false, List.of("r"), List.of()).onlyFor("r")).isFalse();
        assertThat(new ServerState.RowPolicy("x", false, true, List.of(), List.of()).onlyFor("r")).isFalse();
        assertThat(new ServerState.RowPolicy("x", false, false, List.of("r"), List.of("s")).onlyFor("r")).isFalse();
        assertThat(new ServerState.RowPolicy("x", false, false, List.of("r", "s"), List.of()).onlyFor("r")).isFalse();
        assertThat(new ServerState.NativeGrant(null, "r", "SELECT", "hr", null, null).text()).isEqualTo("SELECT ON hr.*");
        assertThat(new ServerState.NativeGrant("u", null, "SELECT", "hr", "t", "c").text()).isEqualTo("SELECT(c) ON hr.t");
    }
}
