// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServerStateTest
{
    /** A cluster with internal users, the agent's role of alice, a stale one of gone and a role mapped outside GrantForge. */
    static FakeOpenSearch cluster()
    {
        FakeOpenSearch search = new FakeOpenSearch().user("alice", "analysts").user("bob").user("etl").user("agent").user("root");
        search.users.with("kibanaserver").put("reserved", true);
        search.roles.set("grantforge_alice", FakeOpenSearch.json("{\"description\": \"GrantForge agent role of user alice\","
                + " \"index_permissions\": [{\"index_patterns\": [\"hr\"], \"allowed_actions\": [\"read\"]}]}"));
        search.roles.set("grantforge_gone", FakeOpenSearch.json("{\"description\": \"GrantForge agent role of user gone\"}"));
        search.roles.set("grantforge_fake", FakeOpenSearch.json("{\"description\": \"GrantForge agent role of user other\"}"));
        search.roles.set("readall", FakeOpenSearch.json("{\"reserved\": true, \"index_permissions\": [{\"index_patterns\": [\"*\"],"
                + " \"allowed_actions\": [\"read\"]}]}"));
        search.roles.set("all_access", FakeOpenSearch.json("{\"reserved\": true}"));
        search.mappings.set("grantforge_alice", FakeOpenSearch.json("{\"users\": [\"alice\"]}"));
        search.mappings.set("readall", FakeOpenSearch.json("{\"backend_roles\": [\"analysts\"], \"users\": [\"b*\"]}"));
        search.mappings.set("all_access", FakeOpenSearch.json("{\"users\": [\"root\"]}"));
        search.answer("GET /_resolve/index/*", FakeOpenSearch.json("{\"indices\": [{\"name\": \"hr\"}, {\"name\": \"logs-eu\"}]}"));
        search.answer("GET /hr/_mapping", FakeOpenSearch.json("{\"hr\": {\"mappings\": {\"properties\": {\"salary\": {\"type\": \"long\"}}}}}"));
        return search;
    }

    @Test
    void readsTheUsersItKeepsRolesForAndThoseNoRoleBinds()
    {
        ServerState state = ServerState.read(cluster(), "grantforge_", Set.of("etl"), Set.of("ldap-user", "alice"));
        assertThat(state.version()).isEqualTo("3.9.0");
        assertThat(state.self()).isEqualTo("agent");
        assertThat(state.agentRoles()).containsOnlyKeys("grantforge_alice", "grantforge_gone").containsEntry("grantforge_gone", "gone");
        assertThat(state.accounts()).extracting(Account::name).as("not the agent, unmanaged, reserved users or all_access")
                .containsExactly("alice", "bob", "gone", "ldap-user");
        assertThat(state.accounts().get(0).groups()).containsExactly("analysts");
        assertThat(state.accounts().get(0).internal()).isTrue();
        assertThat(state.accounts().get(3).internal()).isFalse();
        assertThat(state.superusers()).containsExactly("root");
        assertThat(state.internalUsers()).containsKeys("kibanaserver", "alice");
        assertThat(state.roles()).containsKey("readall");
        assertThat(state.mappings()).containsKey("all_access");
        assertThat(state.inventory().names()).containsExactly("hr", "logs-eu");
        assertThat(state.inventory().cluster()).isEqualTo("search");
        assertThat(state.inventory().fields("hr")).containsExactly("salary");
    }

    @Test
    void findsTheRolesAUserHasOutsideTheAgent()
    {
        FakeOpenSearch search = cluster();
        search.users.with("bob").withArray("opendistro_security_roles").add("kibana_user");
        search.mappings.set("everyone", FakeOpenSearch.json("{\"backend_roles\": [\"*\"]}"));
        ServerState state = ServerState.read(search, "grantforge_", Set.of(), Set.of());
        assertThat(state.nativeRoles(new Account("alice", Set.of("analysts"), true))).containsExactly("everyone", "readall");
        assertThat(state.nativeRoles(new Account("bob", Set.of(), true))).containsExactly("everyone", "kibana_user", "readall");
        assertThat(state.nativeRoles(new Account("carol", Set.of(), false))).containsExactly("everyone");
    }

    @Test
    void knowsTheAgentsRolesByNameAndDescription()
    {
        assertThat(ServerState.owner("grantforge_alice", FakeOpenSearch.json("{\"description\": \"GrantForge agent role of user alice\"}"),
                "grantforge_")).isEqualTo("alice");
        assertThat(ServerState.owner("other_alice", FakeOpenSearch.json("{\"description\": \"GrantForge agent role of user alice\"}"),
                "grantforge_")).isNull();
        assertThat(ServerState.owner("grantforge_alice", FakeOpenSearch.json("{\"description\": \"made by hand\"}"), "grantforge_")).isNull();
        assertThat(ServerState.owner("grantforge_alice", FakeOpenSearch.json("{\"description\": \"GrantForge agent role of user alice\","
                + " \"reserved\": true}"), "grantforge_")).isNull();
        assertThat(ServerState.texts(FakeOpenSearch.json("[\"b\", 1, \"a\"]"))).containsExactly("a", "b");
        assertThat(ServerState.texts(FakeOpenSearch.json("{}"))).isEmpty();
    }
}
