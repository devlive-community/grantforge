// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerStateTest
{
    private static Document role(String role, String database)
    {
        return new Document("role", role).append("db", database);
    }

    private static Document user(String name, String database, Document... roles)
    {
        return new Document("user", name).append("db", database).append("roles", List.of(roles));
    }

    private static Document privilege(Document resource, String... actions)
    {
        return new Document("resource", resource).append("actions", List.of(actions));
    }

    private static Document collection(String database, String collection)
    {
        return new Document("db", database).append("collection", collection);
    }

    /** A replica set with users, one role of the agent and roles outside GrantForge, on with authorization. */
    static FakeMongo deployment()
    {
        return new FakeMongo().database("sales", "orders", "items", "system.views").database("hr", "salaries").database("admin", "system.users")
                .answer("hello", new Document("setName", "rs0"))
                .answer("getCmdLineOpts", new Document("parsed", new Document("security", new Document("authorization", "enabled"))))
                .answer("usersInfo", new Document("users", List.of(user("alice", "admin", role("grantforge_alice", "admin"), role("analysts", "admin")),
                        user("alice", "sales", role("readWrite", "hr")), user("bob", "admin"), user("etl", "admin"), user("boss", "admin", role("root", "admin")),
                        user("owner", "admin", role("everything", "admin")), user("grantforge", "admin", role("userAdminAnyDatabase", "admin")))))
                .answer("rolesInfo", new Document("roles", List.of(
                        new Document("role", "grantforge_alice").append("db", "admin").append("roles", List.of())
                                .append("privileges", List.of(privilege(collection("sales", "orders"), "find", "collStats"), privilege(new Document("cluster", true), "top"),
                                        privilege(new Document("anyResource", true), "find")))
                                .append("inheritedPrivileges", List.of()),
                        new Document("role", "readWrite").append("db", "hr").append("roles", List.of()).append("privileges", List.of())
                                .append("inheritedPrivileges", List.of(privilege(collection("hr", ""), "find", "insert"))),
                        new Document("role", "everything").append("db", "admin").append("roles", List.of()).append("privileges", List.of())
                                .append("inheritedPrivileges", List.of(privilege(new Document("anyResource", true), "anyAction"))),
                        new Document("role", "analysts").append("db", "admin").append("roles", List.of(role("read", "admin"))).append("privileges", List.of())
                                .append("inheritedPrivileges", List.of(privilege(new Document("anyResource", true), "find"))))));
    }

    @Test
    void readsTheUsersByNameWithTheRolesTheyHold()
    {
        ServerState state = ServerState.read(deployment(), "grantforge_", Set.of("etl", "grantforge"));
        Account alice = state.accounts().stream().filter(account -> account.name().equals("alice")).findFirst().orElseThrow();
        assertThat(alice.identities()).extracting(Account.Identity::label).containsExactly("alice@admin", "alice@sales");
        assertThat(alice.groups()).as("the agent's own role is no group").containsExactlyInAnyOrder("analysts", "readWrite");
        assertThat(alice.roles()).containsExactlyInAnyOrder(new Account.RoleRef("analysts", "admin"), new Account.RoleRef("readWrite", "hr"));
        assertThat(state.accounts()).extracting(Account::name).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void leavesAloneWhomTheSettingsNameAndWhomNoRoleBinds()
    {
        ServerState state = ServerState.read(deployment(), "grantforge_", Set.of("etl", "grantforge"));
        assertThat(state.accounts()).extracting(Account::name).doesNotContain("etl", "grantforge", "boss", "owner");
        assertThat(state.superusers()).containsEntry("boss", new Account.RoleRef("root", "admin"))
                .as("a custom role with every action on everything").containsEntry("owner", new Account.RoleRef("everything", "admin"));
    }

    @Test
    void readsTheAgentsRolesBack()
    {
        ServerState state = ServerState.read(deployment(), "grantforge_", Set.of());
        ServerState.ManagedRole role = requireNonNull(state.managed().get("grantforge_alice"), "role");
        assertThat(state.managed()).containsOnlyKeys("grantforge_alice");
        assertThat(role.privileges().privileges()).containsOnlyKeys(Target.DEPLOYMENT, Target.collection("sales", "orders"));
        assertThat(role.privileges().privileges().get(Target.collection("sales", "orders"))).containsExactly("collStats", "find");
        assertThat(role.inherited()).isFalse();
        assertThat(role.comparable()).as("anyResource is nothing the agent writes").isFalse();
    }

    @Test
    void readsWhatRolesOutsideGrantForgeGive()
    {
        ServerState state = ServerState.read(deployment(), "grantforge_", Set.of());
        ServerState.Role readWrite = requireNonNull(state.others().get(new Account.RoleRef("readWrite", "hr")), "readWrite");
        ServerState.Role everything = requireNonNull(state.others().get(new Account.RoleRef("everything", "admin")), "everything");
        assertThat(readWrite.privileges().get(Target.database("hr"))).containsExactly("find", "insert");
        assertThat(readWrite.everything()).isFalse();
        assertThat(readWrite.administersUsers()).isFalse();
        assertThat(everything.everything()).isTrue();
        assertThat(everything.administersUsers()).isTrue();
        assertThat(new ServerState.Role(Map.of(Target.database("hr"), Set.of("grantRole")), false).administersUsers()).isTrue();
    }

    @Test
    void knowsTheDeployment()
    {
        ServerState state = ServerState.read(deployment(), "grantforge_", Set.of());
        assertThat(state.inventory().deployment()).isEqualTo("rs0");
        assertThat(state.inventory().databases()).containsExactly("hr", "sales");
        assertThat(state.inventory().collections("sales")).containsExactly("items", "orders");
        assertThat(ServerState.deployment(new Document("msg", "isdbgrid"))).isEqualTo(Inventory.SHARDED);
        assertThat(ServerState.deployment(new Document())).isEqualTo(Inventory.STANDALONE);
    }

    @Test
    void asksNothingOfNoRoles()
    {
        FakeMongo empty = deployment().answer("usersInfo", new Document("users", List.of())).answer("rolesInfo", new Document());
        ServerState state = ServerState.read(empty, "grantforge_", Set.of());
        assertThat(state.accounts()).isEmpty();
        assertThat(state.others()).isEmpty();
        assertThat(state.managed()).isEmpty();
    }

    @Test
    void tellsWhetherAuthorizationIsOn()
    {
        assertThat(ServerState.read(deployment(), "grantforge_", Set.of()).authorization()).isTrue();
        Document none = new Document("parsed", new Document("net", new Document()));
        assertThat(ServerState.read(deployment().answer("getCmdLineOpts", none), "grantforge_", Set.of()).authorization()).isFalse();
        assertThat(ServerState.read(deployment().answer("getCmdLineOpts", new Document()), "grantforge_", Set.of()).authorization()).isFalse();
        Document security = new Document("parsed", new Document("security", new Document("authorization", "disabled")));
        assertThat(ServerState.read(deployment().answer("getCmdLineOpts", security), "grantforge_", Set.of()).authorization()).isFalse();
        for (String key : List.of("keyFile", "clusterAuthMode")) {
            Document implied = new Document("parsed", new Document("security", new Document(key, "x")));
            assertThat(ServerState.read(deployment().answer("getCmdLineOpts", implied), "grantforge_", Set.of()).authorization()).as(key).isTrue();
        }
    }

    @Test
    void anAgentThatMayNotAskLeavesItOpen()
    {
        assertThat(ServerState.read(deployment().fail("getCmdLineOpts", false), "grantforge_", Set.of()).authorization()).isNull();
        assertThatThrownBy(() -> ServerState.read(deployment().fail("getCmdLineOpts", true), "grantforge_", Set.of())).isInstanceOf(MongoFailure.class);
    }
}
