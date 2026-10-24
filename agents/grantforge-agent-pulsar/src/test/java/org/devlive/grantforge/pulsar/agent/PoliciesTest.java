// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.naming.TopicName;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
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
    private static final Caller ALICE = new Caller("alice", "10.0.0.7");

    private static Policies policies(PulsarSnapshots snapshots)
    {
        return new Policies(snapshots.snapshot(), new DecisionCache(100, Duration.ofMinutes(1)));
    }

    private static PulsarResource topic(String name, @Nullable String subscription)
    {
        return PulsarResource.topic("west", TopicName.get(name), subscription);
    }

    @Test
    void refusesEverythingWithoutASnapshot()
    {
        Policies none = new Policies(null, new DecisionCache(0, Duration.ofSeconds(1)));
        assertThat(none.enforced()).isTrue();
        assertThat(none.received()).isFalse();
        Policies.Verdict verdict = none.decide(ALICE, topic("sales/orders/payments", null), "produce");
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.denied()).isFalse();
        assertThat(verdict.accessType()).isEqualTo("produce");
    }

    @Test
    void grantsByANamespaceAndRefusesByADenialOnATopic()
    {
        Policies policies = policies(new PulsarSnapshots().allow(List.of("@analysts"), List.of("produce"), "tenant", "sales", "namespace", "orders")
                .deny(List.of("alice"), List.of("produce"), "tenant", "sales", "namespace", "orders", "topic", "audit").group("analysts", "alice"));
        assertThat(policies.received()).isTrue();
        assertThat(policies.decide(ALICE, topic("persistent://sales/orders/payments-partition-3", null), "produce").allowed()).isTrue();
        Policies.Verdict audit = policies.decide(ALICE, topic("sales/orders/audit", null), "produce");
        assertThat(audit.denied()).isTrue();
        assertThat(audit.decision().policyId()).isEqualTo(2L);
        assertThat(policies.decide(ALICE, topic("sales/returns/refunds", null), "produce").allowed()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> policies.decide(ALICE, PulsarResource.tenant("west", "sales")));
    }

    @Test
    void grantsTheSubscriptionsAPolicyNames()
    {
        Policies policies = policies(new PulsarSnapshots().allow(List.of("alice"), List.of("consume"), "tenant", "sales", "namespace", "orders", "topic",
                "*", "subscription", "billing-*"));
        assertThat(policies.decide(ALICE, topic("sales/orders/payments", "billing-eu"), "consume").allowed()).isTrue();
        assertThat(policies.decide(ALICE, topic("sales/orders/payments", "audit"), "consume").allowed()).isFalse();
        assertThat(policies.decide(ALICE, topic("sales/orders/payments", null), "consume").allowed()).isFalse();
    }

    @Test
    void letsTheClustersAdministratorsDoEverything()
    {
        Policies policies = policies(new PulsarSnapshots().allow(List.of("alice"), List.of("admin"), "cluster", "west"));
        assertThat(policies.decide(ALICE, topic("sales/orders/payments", "any"), "consume").allowed()).isTrue();
        assertThat(policies.decide(ALICE, PulsarResource.namespace("west", NamespaceName.get("hr/payroll")), "functions").allowed()).isTrue();
        assertThat(policies.decide(ALICE, PulsarResource.cluster("east"), "admin").allowed()).isFalse();
        assertThat(policies.decide(ALICE, PulsarResource.topic("east", TopicName.get("sales/orders/payments"), null), "produce").allowed()).isFalse();
    }

    @Test
    void letsATenantsAdministratorAdministerWhatIsInIt()
    {
        Policies policies = policies(new PulsarSnapshots().allow(List.of("alice"), List.of("admin_tenant"), "tenant", "sales")
                .deny(List.of("alice"), List.of("admin"), "cluster", "*"));
        assertThat(policies.decide(ALICE, topic("sales/orders/payments", null), "admin_topic").allowed()).isTrue();
        assertThat(policies.decide(ALICE, topic("sales/orders/payments", null), "produce", "consume").accessType()).isEqualTo("produce");
        assertThat(policies.decide(ALICE, PulsarResource.cluster("west"), "admin").denied()).isTrue();
        assertThat(policies.decide(ALICE, topic("hr/payroll/salaries", null), "produce", "consume").denied()).isFalse();
    }

    @Test
    void holdsForTheClientsAddress()
    {
        Policies policies = policies(new PulsarSnapshots().allowFrom(List.of("10.0.0.0/8"), List.of("alice", "bob"), List.of("produce"), "tenant", "*"));
        assertThat(policies.decide(ALICE, topic("sales/orders/payments", null), "produce").allowed()).isTrue();
        assertThat(policies.decide(new Caller("bob", "203.0.113.9"), topic("sales/orders/payments", null), "produce").allowed()).isFalse();
        assertThat(policies.decide(new Caller("bob", null), topic("sales/orders/payments", null), "produce").allowed()).isFalse();
    }

    @Test
    void leavesPulsarUncheckedWhileTheServiceIsNotInUse()
    {
        assertThat(policies(new PulsarSnapshots().disabled()).enforced()).isFalse();
    }

    @Test
    void decidesNothingWhenTheSnapshotBreaks()
    {
        Snapshot broken = mock(Snapshot.class);
        when(broken.serviceEnabled()).thenReturn(true);
        when(broken.decide(any())).thenThrow(new IllegalStateException("corrupt"));
        Policies.Verdict verdict = new Policies(broken, new DecisionCache(0, Duration.ofSeconds(1))).decide(ALICE, PulsarResource.cluster("west"), "admin");
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.decision().outcome()).isEqualTo(AgentDecision.withoutSnapshot().outcome());
    }
}
