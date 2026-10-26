// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconcilerTest
{
    private static Plan reading(String index)
    {
        return new Plan(new TreeSet<>(Set.of(Planner.READ_COMPOSITE)), List.of(new Plan.Entry(new TreeSet<>(Set.of(index)), new TreeSet<>(Set.of("read")),
                new TreeSet<>(), new TreeSet<>(), null)), List.of("a note"));
    }

    private static Plan nothing()
    {
        return new Plan(new TreeSet<>(), List.of(), List.of());
    }

    private static Reconciler.Outcome reconcile(FakeOpenSearch search, Function<Account, Plan> plans)
    {
        return new Reconciler(search, "grantforge_").reconcile(ServerState.read(search, "grantforge_", Set.of("etl"), Set.of()), plans);
    }

    @Test
    void keepsOneRolePerUserMappedToThatUserAlone()
    {
        FakeOpenSearch search = ServerStateTest.cluster();
        Reconciler.Outcome outcome = reconcile(search, account -> "bob".equals(account.name()) ? reading("logs-eu") : nothing());
        assertThat(outcome.users()).isEqualTo(1);
        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.dropped()).as("alice and gone are granted nothing now").isEqualTo(2);
        assertThat(outcome.failed()).isZero();
        assertThat(outcome.changed()).isTrue();
        assertThat(search.roles.has("grantforge_bob")).isTrue();
        assertThat(search.roles.has("grantforge_alice")).isFalse();
        assertThat(search.mappings.path("grantforge_bob").path("users").toString()).isEqualTo("[\"bob\"]");
        assertThat(search.mappings.has("grantforge_alice")).isFalse();
        assertThat(search.roles.has("readall")).as("roles outside the agent stay").isTrue();
        assertThat(search.sent("PATCH", Reconciler.ROLES)).as("one patch to write, one to remove").hasSize(2);

        Reconciler.Outcome again = reconcile(search, account -> "bob".equals(account.name()) ? reading("logs-eu") : nothing());
        assertThat(again.changed()).isFalse();
    }

    @Test
    void writesAgainWhatWasChangedByHand()
    {
        FakeOpenSearch search = ServerStateTest.cluster();
        reconcile(search, account -> reading("hr"));
        search.roles.with("grantforge_bob").put("reserved", false).withArray("cluster_permissions").removeAll();
        search.mappings.with("grantforge_bob").withArray("backend_roles").add("intruders");
        Reconciler.Outcome restored = reconcile(search, account -> reading("hr"));
        assertThat(restored.updated()).isEqualTo(1);
        assertThat(restored.mappings()).isEqualTo(1);
        assertThat(search.roles.path("grantforge_bob").path("cluster_permissions").toString()).contains(Planner.READ_COMPOSITE);
        assertThat(search.mappings.path("grantforge_bob").path("backend_roles").isMissingNode()).isTrue();
    }

    @Test
    void grantingNothingRemovesEveryRoleOfTheAgent()
    {
        FakeOpenSearch search = ServerStateTest.cluster();
        Reconciler.Outcome outcome = new Reconciler(search, "grantforge_").reconcile(ServerState.read(search, "grantforge_", Set.of(), Set.of()), null);
        assertThat(outcome.dropped()).isEqualTo(2);
        assertThat(search.roles.has("grantforge_alice") || search.roles.has("grantforge_gone")).isFalse();
    }

    @Test
    void aRefusedPatchIsAppliedChangeByChange()
    {
        FakeOpenSearch search = ServerStateTest.cluster().refuse("grantforge_bob");
        Reconciler.Outcome outcome = reconcile(search, account -> reading("hr"));
        assertThat(outcome.failed()).as("bob's role and mapping").isEqualTo(2);
        assertThat(search.roles.has("grantforge_alice")).isTrue();
        assertThat(search.roles.has("grantforge_bob")).isFalse();
        assertThat(outcome.changed()).isTrue();
    }

    @Test
    void anUnreachableClusterFailsTheReconciliation()
    {
        FakeOpenSearch search = ServerStateTest.cluster();
        ServerState state = ServerState.read(search, "grantforge_", Set.of(), Set.of());
        search.fail("PATCH " + Reconciler.ROLES, 503);
        assertThatThrownBy(() -> new Reconciler(search, "grantforge_").reconcile(state, account -> reading("hr"))).isInstanceOf(OpenSearchFailure.class);
        search.heal("PATCH " + Reconciler.ROLES).fail("PATCH " + Reconciler.ROLES, 400);
        assertThat(new Reconciler(search, "grantforge_").reconcile(state, account -> reading("hr")).failed()).isPositive();
    }

    @Test
    void comparesRolesWhateverTheOrderOfTheirValues()
    {
        String held = "{\"description\": \"d\", \"reserved\": false, \"cluster_permissions\": [\"b\", \"a\"], \"index_permissions\": [{\"index_patterns\":"
                + " [\"y\", \"x\"], \"allowed_actions\": [\"read\"], \"dls\": \"\"}, {\"index_patterns\": [\"z\"], \"allowed_actions\": [\"index\"],"
                + " \"dls\": \"q\"}]}";
        String wanted = "{\"description\": \"d\", \"cluster_permissions\": [\"a\", \"b\"], \"index_permissions\": [{\"index_patterns\": [\"z\"],"
                + " \"allowed_actions\": [\"index\"], \"fls\": [], \"dls\": \"q\"}, {\"index_patterns\": [\"x\", \"y\"], \"allowed_actions\": [\"read\"]}],"
                + " \"tenant_permissions\": []}";
        assertThat(Reconciler.canonical(FakeOpenSearch.json(held))).isEqualTo(Reconciler.canonical(FakeOpenSearch.json(wanted)));
        assertThat(Reconciler.canonical(FakeOpenSearch.json("{\"cluster_permissions\": [{\"x\": 1}]}"))).contains("{\\\"x\\\":1}");
        assertThat(new Reconciler(new FakeOpenSearch(), "gf_").role("alice")).isEqualTo("gf_alice");
    }
}
