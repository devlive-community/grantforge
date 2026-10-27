// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconcilerTest
{
    private static final Plan READS_HR = new Plan(new TreeSet<>(),
            List.of(new Plan.Entry(new TreeSet<>(Set.of("hr")), new TreeSet<>(Set.of("read")), new TreeSet<>(), null)), List.of("a note"));
    private static final Plan NOTHING = new Plan(new TreeSet<>(), List.of(), List.of());

    private static ServerState state(FakeElasticsearch cluster)
    {
        return ServerState.read(cluster, "grantforge_", Set.of("etl"), Set.of("carol"));
    }

    private static Function<Account, Plan> everyone(Plan plan)
    {
        return account -> plan;
    }

    private static List<String> roles(FakeElasticsearch cluster, String user)
    {
        return ServerState.texts(cluster.users.path(user).path("roles")).stream().toList();
    }

    @Test
    void keepsOneRolePerUserGivenToNativeUsersAndMappedToOthersByName()
    {
        FakeElasticsearch cluster = new FakeElasticsearch().user("alice", "analysts").user("etl", "ingest");
        Reconciler.Outcome outcome = new Reconciler(cluster, "grantforge_").reconcile(state(cluster), everyone(READS_HR));
        assertThat(outcome).isEqualTo(new Reconciler.Outcome(2, 2, 0, 0, 3, 0));
        assertThat(outcome.changed()).isTrue();
        assertThat(cluster.roles.properties()).extracting(entry -> entry.getKey()).containsExactly("grantforge_alice", "grantforge_carol");
        assertThat(cluster.roles.path("grantforge_alice").path("metadata").path("grantforge_user").asText()).isEqualTo("alice");
        assertThat(roles(cluster, "alice")).as("the user's own roles are kept").containsExactly("analysts", "grantforge_alice");
        assertThat(cluster.users.path("alice").path("full_name").asText()).isEqualTo("ALICE");
        assertThat(roles(cluster, "etl")).as("an unmanaged user is left alone").containsExactly("ingest");
        JsonNode carol = cluster.mappings.path("grantforge_carol");
        assertThat(carol.path("rules").path("field").path("username").asText()).as("a user of another realm, by name").isEqualTo("carol");
        assertThat(carol.path("roles").toString()).isEqualTo("[\"grantforge_carol\"]");

        Reconciler.Outcome again = new Reconciler(cluster, "grantforge_").reconcile(state(cluster), everyone(READS_HR));
        assertThat(again).as("nothing changes the second time").isEqualTo(new Reconciler.Outcome(2, 0, 0, 0, 0, 0));
        assertThat(again.changed()).isFalse();
        assertThat(cluster.sent("POST", Reconciler.ROLES)).hasSize(1);
    }

    @Test
    void undoesWhatWasChangedByHand()
    {
        FakeElasticsearch cluster = new FakeElasticsearch().user("alice").user("bob");
        Reconciler reconciler = new Reconciler(cluster, "grantforge_");
        reconciler.reconcile(state(cluster), everyone(READS_HR));
        cluster.roles.set("grantforge_alice", FakeElasticsearch.json("{\"description\": \"GrantForge agent role of user alice\", \"metadata\":"
                + " {\"grantforge_user\": \"alice\"}, \"indices\": [{\"names\": [\"*\"], \"privileges\": [\"all\"]}], \"run_as\": [\"root\"]}"));
        cluster.user("bob", "grantforge_bob", "grantforge_alice");
        cluster.mappings.set("grantforge_carol", FakeElasticsearch.json("{\"enabled\": true, \"roles\": [\"grantforge_carol\"], \"rules\": {\"any\":"
                + " [{\"field\": {\"username\": \"carol\"}}, {\"field\": {\"username\": \"*\"}}]}}"));
        Reconciler.Outcome restored = reconciler.reconcile(state(cluster), everyone(READS_HR));
        assertThat(restored.updated()).isEqualTo(1);
        assertThat(restored.assigned()).as("bob's roles and carol's mapping").isEqualTo(2);
        assertThat(cluster.roles.path("grantforge_alice").path("run_as")).isEmpty();
        assertThat(roles(cluster, "bob")).as("another user's role is taken back").containsExactly("grantforge_bob");
        assertThat(cluster.mappings.path("grantforge_carol").path("rules").path("field").path("username").asText()).isEqualTo("carol");
    }

    @Test
    void grantingNothingTakesEveryRoleOfTheAgentBackBeforeRemovingIt()
    {
        FakeElasticsearch cluster = new FakeElasticsearch().user("alice", "analysts");
        Reconciler reconciler = new Reconciler(cluster, "grantforge_");
        reconciler.reconcile(state(cluster), everyone(READS_HR));
        Reconciler.Outcome off = reconciler.reconcile(state(cluster), null);
        assertThat(off).isEqualTo(new Reconciler.Outcome(0, 0, 0, 2, 3, 0));
        assertThat(cluster.roles.isEmpty()).isTrue();
        assertThat(cluster.mappings.isEmpty()).isTrue();
        assertThat(roles(cluster, "alice")).containsExactly("analysts");
        List<FakeElasticsearch.Sent> sent = cluster.sent();
        assertThat(sent.get(sent.size() - 1).method()).as("the roles go last").isEqualTo("DELETE");
        assertThat(sent.get(sent.size() - 1).path()).isEqualTo(Reconciler.ROLES);
        Reconciler.Outcome empty = reconciler.reconcile(state(cluster), everyone(NOTHING));
        assertThat(empty.changed()).isFalse();
    }

    @Test
    void aRefusedChangeIsLoggedAndTheOthersGoOn()
    {
        FakeElasticsearch cluster = new FakeElasticsearch().user("alice").user("bob").refuse("grantforge_bob").refuse("bob");
        Reconciler.Outcome outcome = new Reconciler(cluster, "grantforge_").reconcile(state(cluster), everyone(READS_HR));
        assertThat(outcome.failed()).as("bob's role, its mapping and bob himself").isEqualTo(3);
        assertThat(cluster.roles.has("grantforge_alice")).isTrue();
        assertThat(roles(cluster, "alice")).containsExactly("grantforge_alice");

        FakeElasticsearch refusing = new FakeElasticsearch().user("alice").fail("POST " + Reconciler.ROLES, 400).fail("DELETE " + Reconciler.ROLES, 400);
        refusing.roles.set("grantforge_gone", FakeElasticsearch.json("{\"metadata\": {\"grantforge_user\": \"gone\"}}"));
        Reconciler.Outcome refused = new Reconciler(refusing, "grantforge_").reconcile(state(refusing), everyone(READS_HR));
        assertThat(refused.failed()).as("the writes, alice's mapping and her user, as her role is missing; then the removal").isGreaterThanOrEqualTo(2);
    }

    @Test
    void anUnreachableClusterFailsTheReconciliation()
    {
        FakeElasticsearch cluster = new FakeElasticsearch().user("alice").fail("POST " + Reconciler.ROLES, 0);
        assertThatThrownBy(() -> new Reconciler(cluster, "grantforge_").reconcile(state(cluster), everyone(READS_HR)))
                .isInstanceOf(ElasticsearchFailure.class);
        FakeElasticsearch mapping = new FakeElasticsearch().fail("PUT /_security/role_mapping/grantforge_carol", 503);
        assertThatThrownBy(() -> new Reconciler(mapping, "grantforge_").reconcile(state(mapping), everyone(READS_HR)))
                .isInstanceOf(ElasticsearchFailure.class);
        FakeElasticsearch removal = new FakeElasticsearch().fail("DELETE " + Reconciler.ROLES, 0);
        removal.roles.set("grantforge_gone", FakeElasticsearch.json("{\"metadata\": {\"grantforge_user\": \"gone\"}}"));
        assertThatThrownBy(() -> new Reconciler(removal, "grantforge_").reconcile(state(removal), null)).isInstanceOf(ElasticsearchFailure.class);
    }

    @Test
    void comparesRolesWhateverTheOrderOfTheirValues()
    {
        JsonNode held = FakeElasticsearch.json("{\"description\": \"d\", \"metadata\": {\"grantforge_user\": \"a\"}, \"cluster\": [\"b\", \"a\"],"
                + " \"indices\": [{\"names\": [\"y\", \"x\"], \"privileges\": [\"read\"], \"field_security\": {\"grant\": [\"f\"]}, \"query\": \"q\","
                + " \"allow_restricted_indices\": false}, {\"names\": [\"z\"], \"privileges\": [\"index\"]}], \"applications\": [], \"run_as\": [],"
                + " \"transient_metadata\": {\"enabled\": true}}");
        JsonNode wanted = FakeElasticsearch.json("{\"description\": \"d\", \"metadata\": {\"grantforge_user\": \"a\"}, \"cluster\": [\"a\", \"b\"],"
                + " \"indices\": [{\"names\": [\"z\"], \"privileges\": [\"index\"]}, {\"names\": [\"x\", \"y\"], \"privileges\": [\"read\"],"
                + " \"field_security\": {\"grant\": [\"f\"]}, \"query\": \"q\"}]}");
        assertThat(Reconciler.canonical(held)).isEqualTo(Reconciler.canonical(wanted));
        assertThat(Reconciler.path("_security", "user", "a b/c")).isEqualTo("/_security/user/a%20b%2Fc");
    }
}
