// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

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

    private static Policies policies(YarnSnapshots snapshots)
    {
        return new Policies(snapshots.snapshot(), new DecisionCache(100, Duration.ofMinutes(1)));
    }

    @Test
    void refusesEverythingWithoutASnapshot()
    {
        Policies none = new Policies(null, new DecisionCache(0, Duration.ofSeconds(1)));
        assertThat(none.enforced()).isTrue();
        assertThat(none.received()).isFalse();
        Policies.Verdict verdict = none.decide(ALICE, YarnResource.queue("root.sales", null), "submit_app");
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.denied()).isFalse();
        assertThat(verdict.accessType()).isEqualTo("submit_app");
    }

    @Test
    void grantsByAParentQueueAndRefusesByADenialBelow()
    {
        Policies policies = policies(new YarnSnapshots().allow(List.of("@analysts"), List.of("submit_app"), "queue", "root.sales")
                .deny(List.of("alice"), List.of("submit_app"), "queue", "root.sales.adhoc").group("analysts", "alice"));
        assertThat(policies.received()).isTrue();
        assertThat(policies.decide(ALICE, YarnResource.queue("root.sales.etl", "etl-daily"), "submit_app").allowed()).isTrue();
        Policies.Verdict adhoc = policies.decide(ALICE, YarnResource.queue("root.sales.adhoc", "report"), "submit_app");
        assertThat(adhoc.denied()).isTrue();
        assertThat(adhoc.decision().policyId()).isEqualTo(2L);
        assertThat(policies.decide(ALICE, YarnResource.queue("root.hr", null), "submit_app").allowed()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> policies.decide(ALICE, YarnResource.queue("root", null)));
    }

    @Test
    void grantsTheApplicationsAPolicyNames()
    {
        Policies policies = policies(new YarnSnapshots().allow(List.of("alice"), List.of("submit_app"), "queue", "root.hr", "application", "payroll-*"));
        assertThat(policies.decide(ALICE, YarnResource.queue("root.hr", "payroll-oct"), "submit_app").allowed()).isTrue();
        assertThat(policies.decide(ALICE, YarnResource.queue("root.hr", "bonus"), "submit_app").allowed()).isFalse();
        assertThat(policies.decide(ALICE, YarnResource.queue("root.hr", null), "submit_app").allowed()).isFalse();
    }

    @Test
    void letsAQueuesAdministratorSubmitAndTakesTheFirstAllowedType()
    {
        Policies policies = policies(new YarnSnapshots().allow(List.of("alice"), List.of("admin_queue"), "queue", "root.sales")
                .deny(List.of("alice"), List.of("admin"), "cluster", "*"));
        assertThat(policies.decide(ALICE, YarnResource.queue("root.sales.etl", "x"), "submit_app").allowed()).isTrue();
        assertThat(policies.decide(ALICE, YarnResource.queue("root.sales.etl", null), "submit_app", "admin_queue").accessType()).isEqualTo("submit_app");
        assertThat(policies.decide(ALICE, YarnResource.cluster(), "admin").denied()).isTrue();
        assertThat(policies.decide(ALICE, YarnResource.queue("root.hr", null), "submit_app", "admin_queue").denied()).isFalse();
    }

    @Test
    void holdsForTheClientsAddress()
    {
        Policies policies = policies(new YarnSnapshots().allowFrom(List.of("10.0.0.0/8"), List.of("alice", "bob"), List.of("submit_app"), "queue",
                "root"));
        assertThat(policies.decide(ALICE, YarnResource.queue("root.default", null), "submit_app").allowed()).isTrue();
        assertThat(policies.decide(new Caller("bob", List.of(), "203.0.113.9"), YarnResource.queue("root.default", null), "submit_app").allowed())
                .isFalse();
        assertThat(policies.decide(new Caller("bob", List.of(), null), YarnResource.queue("root.default", null), "submit_app").allowed()).isFalse();
    }

    @Test
    void leavesYarnUncheckedWhileTheServiceIsNotInUse()
    {
        assertThat(policies(new YarnSnapshots().disabled()).enforced()).isFalse();
    }

    @Test
    void decidesNothingWhenTheSnapshotBreaks()
    {
        Snapshot broken = mock(Snapshot.class);
        when(broken.serviceEnabled()).thenReturn(true);
        when(broken.decide(any())).thenThrow(new IllegalStateException("corrupt"));
        Policies.Verdict verdict = new Policies(broken, new DecisionCache(0, Duration.ofSeconds(1))).decide(ALICE, YarnResource.cluster(), "admin");
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.decision().outcome()).isEqualTo(AgentDecision.withoutSnapshot().outcome());
    }
}
