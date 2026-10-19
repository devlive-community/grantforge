// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CallerTest
{
    private static final HBaseResource SALES = HBaseResource.namespace("sales");

    private static Snapshot snapshot()
    {
        return new HBaseSnapshots()
                .allow(List.of("@staff"), List.of("read"), "namespace", "sales")
                .deny(List.of("@suspended"), List.of("read"), "namespace", "sales")
                .snapshot();
    }

    @Test
    void keepsTheRequestsGroupsEvenWhenTheirSourceChanges()
    {
        List<String> groups = new ArrayList<>(List.of("staff"));
        Caller caller = new Caller("alice", groups, "10.0.0.7");
        groups.clear();
        groups.add("suspended");

        Policies policies = new Policies(snapshot(), new DecisionCache(0, Duration.ofMinutes(1)), false);
        assertThat(policies.decide(caller, SALES, "read").allowed()).isTrue();
        assertThat(policies.decide(new Caller("alice", groups, "10.0.0.7"), SALES, "read").denied()).isTrue();
        assertThatThrownBy(() -> caller.groups().add("suspended")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(policies.decide(caller, SALES, "read").allowed()).isTrue();
    }

    @Test
    void reusesADecisionForTheSameGroupsInAnotherOrder()
    {
        DecisionCache cache = new DecisionCache(100, Duration.ofMinutes(1));
        Policies policies = new Policies(snapshot(), cache, false);
        assertThat(policies.decide(new Caller("alice", List.of("staff", "analytics", "staff"), "10.0.0.7"), SALES, "read").allowed())
                .isTrue();
        assertThat(policies.decide(new Caller("alice", List.of("analytics", "staff"), "10.0.0.7"), SALES, "read").allowed()).isTrue();
        assertThat(cache.kept()).as("ordering and duplicate groups do not create a different identity").isOne();

        assertThat(policies.decide(new Caller("alice", List.of("analytics"), "10.0.0.7"), SALES, "read").allowed()).isFalse();
        assertThat(cache.kept()).as("a changed membership must be checked afresh").isEqualTo(2);
    }

    @Test
    void passesEachCallersUserAndAddressToThePolicyEngine()
    {
        Snapshot snapshot = spy(snapshot());
        Policies policies = new Policies(snapshot, new DecisionCache(100, Duration.ofMinutes(1)), false);
        policies.decide(new Caller("alice", List.of("staff"), "10.0.0.7"), SALES, "read");
        policies.decide(new Caller("bob", List.of("staff"), "10.0.0.7"), SALES, "read");
        policies.decide(new Caller("alice", List.of("staff"), "10.0.0.8"), SALES, "read");
        policies.decide(new Caller("alice", List.of(), null), SALES, "read");

        ArgumentCaptor<AccessRequest> requests = ArgumentCaptor.forClass(AccessRequest.class);
        verify(snapshot, times(4)).decide(requests.capture());
        List<AccessRequest> actual = requests.getAllValues();
        assertThat(actual).extracting(AccessRequest::user).containsExactly("alice", "bob", "alice", "alice");
        assertThat(actual.get(0).context()).containsEntry("clientAddress", "10.0.0.7");
        assertThat(actual.get(2).context()).containsEntry("clientAddress", "10.0.0.8");
        assertThat(actual.get(3).context()).doesNotContainKey("clientAddress");
        assertThat(actual.get(3).groups()).isEmpty();
    }
}
