// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PoliciesTest
{
    private static final Caller ALICE = new Caller("alice", List.of(), "10.0.0.7");

    private static Policies policies(SolrSnapshots snapshots)
    {
        return new Policies(snapshots.snapshot(), new DecisionCache(100, Duration.ofMinutes(1)));
    }

    @Test
    void refusesEverythingWithoutASnapshot()
    {
        Policies none = new Policies(null, new DecisionCache(0, Duration.ofSeconds(1)));
        assertThat(none.enforced()).isTrue();
        assertThat(none.received()).isFalse();
        Policies.Verdict verdict = none.decide(ALICE, SolrResource.collection("sales", "/select"), "query");
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.denied()).isFalse();
        assertThat(verdict.accessType()).isEqualTo("query");
    }

    @Test
    void grantsByTheCollectionAndRefusesByADenialOnAPath()
    {
        Policies policies = policies(new SolrSnapshots().allow(List.of("@analysts"), List.of("query"), "collection", "sales")
                .deny(List.of("alice"), List.of("query"), "collection", "sales", "path", "/export").group("analysts", "alice"));
        assertThat(policies.received()).isTrue();
        assertThat(policies.decide(ALICE, SolrResource.collection("sales", "/select"), "query").allowed()).isTrue();
        Policies.Verdict export = policies.decide(ALICE, SolrResource.collection("sales", "/export"), "query");
        assertThat(export.denied()).isTrue();
        assertThat(export.decision().policyId()).isEqualTo(2L);
        assertThat(policies.decide(ALICE, SolrResource.collection("hr", "/select"), "query").allowed()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> policies.decide(ALICE, SolrResource.cluster()));
    }

    @Test
    void grantsThePathsAPolicyNames()
    {
        Policies policies = policies(new SolrSnapshots().allow(List.of("alice"), List.of("update"), "collection", "sales", "path", "/update*"));
        assertThat(policies.decide(ALICE, SolrResource.collection("sales", "/update/json"), "update").allowed()).isTrue();
        assertThat(policies.decide(ALICE, SolrResource.collection("sales", "/select"), "update").allowed()).isFalse();
        assertThat(policies.decide(ALICE, SolrResource.collection("sales", null), "update").allowed()).isFalse();
    }

    @Test
    void letsTheClustersAdministratorsDoEverythingButNotTheClusterByItsCollections()
    {
        Policies policies = policies(new SolrSnapshots().allow(List.of("alice"), List.of("admin"), "cluster", "*")
                .allow(List.of("bob"), List.of("admin_collection"), "collection", "team-*"));
        Policies.Verdict query = policies.decide(ALICE, SolrResource.collection("sales", "/select"), "query");
        assertThat(query.allowed()).isTrue();
        assertThat(query.accessType()).isEqualTo("admin");
        Caller bob = new Caller("bob", List.of(), null);
        assertThat(policies.decide(bob, SolrResource.collection("team-a", "/update"), "update").allowed()).isTrue();
        assertThat(policies.decide(bob, SolrResource.cluster(), "admin").allowed()).isFalse();
        assertThat(policies.decide(bob, SolrResource.collection("team-a", null), "query", "update").accessType()).isEqualTo("query");
    }

    @Test
    void holdsForTheClientsAddress()
    {
        Policies policies = policies(new SolrSnapshots().allowFrom(List.of("10.0.0.0/8"), List.of("alice", "bob"), List.of("query"), "collection", "*"));
        assertThat(policies.decide(ALICE, SolrResource.collection("sales", "/select"), "query").allowed()).isTrue();
        assertThat(policies.decide(new Caller("bob", List.of(), "203.0.113.9"), SolrResource.collection("sales", "/select"), "query").allowed()).isFalse();
        assertThat(policies.decide(new Caller("bob", List.of(), null), SolrResource.collection("sales", "/select"), "query").allowed()).isFalse();
    }

    @Test
    void leavesSolrUncheckedWhileTheServiceIsNotInUse()
    {
        assertThat(policies(new SolrSnapshots().disabled()).enforced()).isFalse();
    }

    @Test
    void decidesNothingWhenTheSnapshotBreaks()
    {
        Snapshot broken = mock(Snapshot.class);
        when(broken.serviceEnabled()).thenReturn(true);
        when(broken.decide(any())).thenThrow(new IllegalStateException("corrupt"));
        Policies.Verdict verdict = new Policies(broken, new DecisionCache(0, Duration.ofSeconds(1))).decide(ALICE, SolrResource.cluster(), "admin");
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.decision().outcome()).isEqualTo(AgentDecision.withoutSnapshot().outcome());
    }
}
