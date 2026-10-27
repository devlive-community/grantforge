// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class ServerStateTest
{
    /** A cluster with native users, a superuser, a reserved user, an agent role, roles outside it and role mappings. */
    static FakeElasticsearch cluster()
    {
        FakeElasticsearch cluster = new FakeElasticsearch().user("alice", "analysts", "grantforge_alice").user("bob").user("root", ServerState.SUPERUSER)
                .user("etl").user("agent", ServerState.SUPERUSER).reserved("kibana_system")
                .answer("GET /_resolve/index/*", FakeElasticsearch.json("{\"indices\": [{\"name\": \"hr\"}, {\"name\": \"logs-eu\"}],"
                        + " \"data_streams\": []}"))
                .answer("GET /hr,logs-eu/_mapping", FakeElasticsearch.json("{\"hr\": {\"mappings\": {\"properties\": {\"salary\": {\"type\": \"long\"}}}},"
                        + " \"logs-eu\": {\"mappings\": {\"properties\": {\"region\": {\"type\": \"keyword\"}}}}}"));
        cluster.roles.set("grantforge_alice", FakeElasticsearch.json("{\"description\": \"GrantForge agent role of user alice\", \"metadata\":"
                + " {\"grantforge_user\": \"alice\"}, \"indices\": [{\"names\": [\"hr\"], \"privileges\": [\"read\"]}]}"));
        cluster.roles.set("grantforge_gone", FakeElasticsearch.json("{\"metadata\": {\"grantforge_user\": \"gone\"}}"));
        cluster.roles.set("dave_reader", FakeElasticsearch.json("{\"indices\": [{\"names\": [\"hr\"], \"privileges\": [\"read\"]}]}"));
        cluster.mappings.set("ldap_readers", FakeElasticsearch.json("{\"enabled\": true, \"roles\": [\"dave_reader\"], \"rules\": {\"any\":"
                + " [{\"field\": {\"username\": \"dave\"}}, {\"field\": {\"groups\": \"cn=readers,dc=example\"}}]}}"));
        cluster.mappings.set("off", FakeElasticsearch.json("{\"enabled\": false, \"roles\": [\"superuser\"], \"rules\": {\"field\": {\"username\": \"*\"}}}"));
        cluster.mappings.set("grantforge_alice", FakeElasticsearch.json("{\"enabled\": true, \"roles\": [\"grantforge_alice\"], \"rules\":"
                + " {\"field\": {\"username\": \"alice\"}}}"));
        return cluster;
    }

    private static ServerState read(FakeElasticsearch cluster)
    {
        return ServerState.read(cluster, "grantforge_", Set.of("etl"), Set.of("carol", "dave"));
    }

    @Test
    void readsTheUsersItKeepsRolesForAndThoseNoRoleBinds()
    {
        ServerState state = read(cluster());
        assertThat(state.version()).isEqualTo("9.5.5");
        assertThat(state.self()).isEqualTo("agent");
        assertThat(state.license()).isEqualTo(new ServerState.License("platinum", true));
        assertThat(state.accounts()).extracting(Account::name).as("not the superuser, the reserved user, the agent itself nor the unmanaged user")
                .containsExactly("alice", "bob", "carol", "dave", "gone");
        assertThat(state.accounts().get(0)).isEqualTo(new Account("alice", Set.of("analysts"), true));
        assertThat(state.accounts().get(2)).as("a user of another realm").isEqualTo(new Account("carol", Set.of(), false));
        assertThat(state.superusers()).containsExactly("root");
        assertThat(state.agentRoles()).containsOnlyKeys("grantforge_alice", "grantforge_gone");
        assertThat(requireNonNull(state.nativeUsers().get("kibana_system")).reserved()).isTrue();
        assertThat(state.inventory().names()).containsExactly("hr", "logs-eu");
        assertThat(state.inventory().cluster()).isEqualTo("search");
        assertThat(state.roles()).containsKey("dave_reader");
        assertThat(state.mappings()).containsKey("ldap_readers");
    }

    @Test
    void findsTheRolesAUserHasOutsideTheAgent()
    {
        ServerState state = read(cluster());
        assertThat(state.nativeRoles(new Account("alice", Set.of(), true))).containsExactly("analysts");
        assertThat(state.nativeRoles(new Account("dave", Set.of(), false))).as("a mapping of the user's name").containsExactly("dave_reader");
        assertThat(state.nativeRoles(new Account("erin", Set.of(), false))).as("a mapping turned off gives nothing").isEmpty();
    }

    @Test
    void followsRoleMappingRulesThatNameTheUser()
    {
        assertThat(ServerState.matches(FakeElasticsearch.json("{\"field\": {\"username\": \"alice\"}}"), "alice")).isTrue();
        assertThat(ServerState.matches(FakeElasticsearch.json("{\"field\": {\"username\": [\"bob\", \"al*\"]}}"), "alice")).isTrue();
        assertThat(ServerState.matches(FakeElasticsearch.json("{\"field\": {\"username\": 7}}"), "alice")).isFalse();
        assertThat(ServerState.matches(FakeElasticsearch.json("{\"field\": {\"groups\": \"admins\"}}"), "alice")).as("groups cannot be known")
                .isFalse();
        assertThat(ServerState.matches(FakeElasticsearch.json("{\"all\": [{\"field\": {\"username\": \"alice\"}}, {\"field\": {\"realm.name\": \"ldap\"}}]}"),
                "alice")).isFalse();
        assertThat(ServerState.matches(FakeElasticsearch.json("{\"all\": [{\"field\": {\"username\": \"a*\"}}, {\"field\": {\"username\": \"*e\"}}]}"),
                "alice")).isTrue();
        assertThat(ServerState.matches(FakeElasticsearch.json("{\"all\": []}"), "alice")).isFalse();
        assertThat(ServerState.matches(FakeElasticsearch.json("{\"any\": [{\"field\": {\"username\": \"bob\"}}]}"), "alice")).isFalse();
        assertThat(ServerState.matches(FakeElasticsearch.json("{\"except\": {\"field\": {\"username\": \"bob\"}}}"), "alice")).isFalse();
    }

    @Test
    void knowsTheAgentsRolesByTheirNameAndMetadata()
    {
        JsonNode role = FakeElasticsearch.json("{\"metadata\": {\"grantforge_user\": \"alice\"}}");
        assertThat(ServerState.owner("grantforge_alice", role, "grantforge_")).isEqualTo("alice");
        assertThat(ServerState.owner("grantforge_bob", role, "grantforge_")).as("named for another user").isNull();
        assertThat(ServerState.owner("other_alice", role, "grantforge_")).isNull();
        assertThat(ServerState.owner("grantforge_alice", FakeElasticsearch.json("{}"), "grantforge_")).isNull();
        assertThat(ServerState.owner("grantforge_alice", FakeElasticsearch.json("{\"metadata\": {\"grantforge_user\": \"alice\", \"_reserved\": true}}"),
                "grantforge_")).isNull();
    }

    @Test
    void readsWhatTheLicenseAllows()
    {
        assertThat(new ServerState.License("platinum", true).restricting()).isTrue();
        assertThat(new ServerState.License("trial", true).auditing()).isTrue();
        assertThat(new ServerState.License("gold", true).restricting()).isFalse();
        assertThat(new ServerState.License("gold", true).auditing()).isTrue();
        assertThat(new ServerState.License("basic", true).auditing()).isFalse();
        assertThat(new ServerState.License("enterprise", false).restricting()).as("an expired license allows neither").isFalse();
        assertThat(new ServerState.License("enterprise", false).auditing()).isFalse();
        assertThat(ServerState.texts(FakeElasticsearch.json("[\"b\", 3, \"a\"]"))).containsExactly("a", "b");
    }
}
