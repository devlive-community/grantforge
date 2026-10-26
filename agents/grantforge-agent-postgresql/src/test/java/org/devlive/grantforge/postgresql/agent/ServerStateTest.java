// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerStateTest
{
    static final TableRef ORDERS = new TableRef("crm", "orders");

    /**
     * A server with users alice, bob, carol (a member of analysts, a member of readers), etl and the superuser postgres,
     * the agent's roles grantforge_alice and grantforge_stale, a role grantforge_fake of another's, and databases sales
     * (crm.orders with a serial id, the view crm.regions, row security on crm.orders) and hr (public.salaries).
     */
    static FakePostgreSql server()
    {
        return new FakePostgreSql().answer("server_version_num')::int AS version", Row.of("self", "grantforge", "version", 170011))
                .answer("FROM pg_roles r ORDER BY", role("alice", true, false, null), role("bob", true, false, null), role("carol", true, false, null),
                        role("etl", true, false, null), role("postgres", true, true, null), role("grantforge", true, true, null),
                        role("analysts", false, false, null), role("readers", false, false, null),
                        role("grantforge_alice", false, false, ServerState.MARK + " of user alice"),
                        role("grantforge_stale", false, false, ServerState.MARK + " of user stale"), role("grantforge_fake", false, false, "mine"),
                        role("pg_monitor", false, false, null), role("pg_signal", true, false, null))
                .answer("FROM pg_auth_members", Row.of("member", "carol", "role", "analysts", "inherit", true),
                        Row.of("member", "analysts", "role", "readers", "inherit", true),
                        Row.of("member", "alice", "role", "grantforge_alice", "inherit", true),
                        Row.of("member", "bob", "role", "grantforge_fake", "inherit", false))
                .answer("FROM pg_database WHERE datallowconn", Row.of("name", "hr"), Row.of("name", "postgres"), Row.of("name", "sales"))
                .answer("aclexplode(COALESCE(d.datacl", Row.of("database", "sales", "privilege", "CONNECT", "grantee", "grantforge_alice", "by_owner", true),
                        Row.of("database", "sales", "privilege", "TEMPORARY", "grantee", null, "by_owner", true),
                        Row.of("database", "hr", "privilege", "CONNECT", "grantee", null, "by_owner", true))
                .answer("sales", "FROM pg_namespace n LEFT JOIN pg_class c", column("crm", "orders", "r", "id", "orders_id_seq"),
                        column("crm", "orders", "r", "region", null), column("crm", "orders", "r", "card", null),
                        column("crm", "regions", "v", "region", null), Row.of("schema", "public", "name", null))
                .answer("hr", "FROM pg_namespace n LEFT JOIN pg_class c", column("public", "salaries", "r", "employee", null),
                        column("public", "salaries", "r", "amount", null))
                .answer("sales", "aclexplode(c.relacl)", acl("TABLE", "crm", "orders", null, "SELECT", "grantforge_alice", true),
                        acl("TABLE", "crm", "orders", null, "INSERT", "grantforge_alice", false),
                        acl("COLUMN", "crm", "orders", "card", "SELECT", "bob", true), acl("SCHEMA", null, "crm", null, "USAGE", "grantforge_alice", true),
                        acl("SEQUENCE", "crm", "orders_id_seq", null, "USAGE", "grantforge_stale", true))
                .answer("hr", "aclexplode(c.relacl)", acl("TABLE", "public", "salaries", null, "SELECT", "carol", true))
                .answer("sales", "LEFT JOIN pg_policy p", policy("orders", true, "grantforge", true, "*", List.of(""), "true"),
                        policy("orders", true, "grantforge_alice", false, "r", List.of("grantforge_alice"), "(region = 'EU'::text)"),
                        Row.of("schema", "crm", "table", "regions", "enabled", false, "name", null));
    }

    static Row role(String name, boolean login, boolean superuser, @Nullable String comment)
    {
        return Row.of("name", name, "login", login, "superuser", superuser, "comment", comment);
    }

    static Row column(String schema, String table, String kind, String column, @Nullable String sequence)
    {
        return Row.of("schema", schema, "name", table, "kind", kind, "owner", "postgres", "column", column, "sequence", sequence, "sequence_schema",
                sequence == null ? null : schema);
    }

    static Row acl(String kind, @Nullable String schema, String object, @Nullable String column, String privilege, @Nullable String grantee,
            boolean byOwner)
    {
        return Row.of("kind", kind, "schema", schema, "object", object, "column", column, "privilege", privilege, "grantee", grantee, "by_owner",
                byOwner);
    }

    static Row policy(String table, boolean enabled, String name, boolean permissive, String command, List<String> roles, String using)
    {
        return Row.of("schema", "crm", "table", table, "enabled", enabled, "name", name, "permissive", permissive, "command", command, "roles", roles,
                "using", using);
    }

    static ServerState read(FakePostgreSql postgres)
    {
        return ServerState.read(postgres, "postgres", "grantforge_", Set.of("etl"), Set.of("sales", "hr"));
    }

    @Test
    void readsTheUsersTheAgentKeepsRolesFor()
    {
        ServerState state = read(server());
        assertThat(state.self()).isEqualTo("grantforge");
        assertThat(state.version()).isEqualTo(170011);
        assertThat(state.accounts()).extracting(Account::name).containsExactly("alice", "bob", "carol");
        assertThat(state.superusers()).containsExactly("postgres");
        assertThat(state.roles()).as("only roles the agent marked are its own").containsExactlyInAnyOrder("grantforge_alice", "grantforge_stale");
        Account carol = state.accounts().get(2);
        assertThat(carol.groups()).containsExactlyInAnyOrder("analysts", "readers");
        Account alice = state.accounts().get(0);
        assertThat(alice.groups()).as("the agent's own roles are no groups").isEmpty();
        assertThat(alice.granted()).containsExactly("grantforge_alice");
        assertThat(alice.inheriting()).containsExactly("grantforge_alice");
        Account bob = state.accounts().get(1);
        assertThat(bob.groups()).containsExactly("grantforge_fake");
        assertThat(bob.inheriting()).isEmpty();
    }

    @Test
    void readsEachDatabaseItKeeps()
    {
        FakePostgreSql postgres = server();
        ServerState state = read(postgres);
        assertThat(state.databases()).containsOnlyKeys("hr", "sales");
        ServerState.DatabaseState sales = requireNonNull(state.databases().get("sales"), "sales");
        assertThat(sales.inventory().columns(ORDERS)).containsExactly("id", "region", "card");
        assertThat(requireNonNull(sales.inventory().table(ORDERS), "orders").sequences()).containsEntry("id", new TableRef("crm", "orders_id_seq"));
        assertThat(sales.held("grantforge_alice")).as("what another than the owner gave is left out").containsExactlyInAnyOrder(
                Privilege.database("sales", "CONNECT"), Privilege.table(ORDERS, "SELECT"), Privilege.schema("crm", "USAGE"));
        assertThat(sales.held("grantforge_stale")).containsExactly(Privilege.sequence(new TableRef("crm", "orders_id_seq"), "USAGE"));
        assertThat(sales.held("nobody")).isEmpty();
        assertThat(sales.nativeGrants()).contains(new ServerState.NativeGrant(null, Privilege.database("sales", "TEMPORARY")),
                new ServerState.NativeGrant("bob", Privilege.column(ORDERS, "card", "SELECT")),
                new ServerState.NativeGrant("postgres", Privilege.table(ORDERS, "OWNER")));
        ServerState.Security security = requireNonNull(sales.security().get(ORDERS), "security");
        assertThat(security.enabled()).isTrue();
        assertThat(security.policies()).containsOnlyKeys("grantforge", "grantforge_alice");
        assertThat(requireNonNull(security.policies().get("grantforge_alice"), "alice").filters("grantforge_alice")).isTrue();
        assertThat(requireNonNull(security.policies().get("grantforge"), "everybody").filters("grantforge")).isFalse();
        assertThat(requireNonNull(sales.security().get(new TableRef("crm", "regions")), "regions").policies()).isEmpty();
        assertThat(requireNonNull(state.databases().get("hr"), "hr").nativeGrants()).contains(new ServerState.NativeGrant("carol",
                Privilege.table(new TableRef("public", "salaries"), "SELECT")));
        assertThat(postgres.queried).noneMatch(query -> query.startsWith("postgres: SELECT n.nspname"));
    }

    @Test
    void readsEveryDatabaseWhenNoneIsNamedAndOlderServersMemberships()
    {
        FakePostgreSql postgres = server().answer("server_version_num')::int AS version", Row.of("self", "grantforge", "version", 150019));
        ServerState state = ServerState.read(postgres, "postgres", "grantforge_", Set.of(), Set.of());
        assertThat(state.databases()).containsOnlyKeys("hr", "postgres", "sales");
        assertThat(state.accounts()).extracting(Account::name).contains("etl");
        assertThat(postgres.queried).anyMatch(query -> query.contains("m.rolinherit AS inherit"));
    }

    @Test
    void aDatabaseItCannotReadIsLeftAloneButAServerItCannotReachFails()
    {
        FakePostgreSql postgres = server().fail("hr: SELECT n.nspname", "42501");
        ServerState state = read(postgres);
        assertThat(state.databases()).containsOnlyKeys("sales");
        assertThat(state.unreadable()).containsOnlyKeys("hr");
        FakePostgreSql down = server().fail("hr: SELECT n.nspname", "08006");
        assertThatThrownBy(() -> read(down)).isInstanceOf(PostgreSqlFailure.class);
    }

    @Test
    void aRowPolicyFiltersForOneRoleAlone()
    {
        assertThat(new ServerState.RowPolicy(false, "r", List.of("r1"), "x").filters("r1")).isTrue();
        assertThat(new ServerState.RowPolicy(true, "r", List.of("r1"), "x").filters("r1")).isFalse();
        assertThat(new ServerState.RowPolicy(false, "*", List.of("r1"), "x").filters("r1")).isFalse();
        assertThat(new ServerState.RowPolicy(false, "r", List.of("r1", ""), "x").filters("r1")).isFalse();
        assertThat(ServerState.Security.NONE.enabled()).isFalse();
    }
}
