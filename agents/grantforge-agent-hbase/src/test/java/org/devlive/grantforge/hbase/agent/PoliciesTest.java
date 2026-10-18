// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import org.apache.hadoop.hbase.TableName;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class PoliciesTest
{
    private static final HBaseResource ORDERS = HBaseResource.table(TableName.valueOf("sales:orders"));
    private static final HBaseResource SSN = ORDERS.family("d").column("ssn");
    private static final HBaseResource NAME = ORDERS.family("d").column("name");

    private static Policies policies(Snapshot snapshot)
    {
        return new Policies(snapshot, new DecisionCache(100, Duration.ofMinutes(1)), true);
    }

    private static Caller caller(String user, String... groups)
    {
        return new Caller(user, List.of(groups), "10.0.0.7");
    }

    private static Snapshot snapshot()
    {
        return new HBaseSnapshots()
                .allow(List.of("alice"), List.of("read"), "namespace", "sales")
                .deny(List.of("alice"), List.of("read"), "namespace", "sales", "table", "orders", "column_family", "d", "column", "ssn")
                .allow(List.of("@staff"), List.of("read"), "namespace", "sales", "table", "orders", "column_family", "d")
                .deny(List.of("mallory"), List.of("read", "write"), "namespace", "sales")
                .allow(List.of("mallory"), List.of("read"), "namespace", "sales", "table", "orders")
                .allow(List.of("dave"), List.of("admin"), "global", "*")
                .group("staff", "carol")
                .snapshot();
    }

    @Test
    void aNamespacesPermissionCoversWhatIsWithinItButADenialBelowRefuses()
    {
        Policies policies = policies(snapshot());
        assertThat(policies.decide(caller("alice"), ORDERS, "read").allowed()).isTrue();
        assertThat(policies.decide(caller("alice"), NAME, "read").allowed()).isTrue();
        Policies.Verdict ssn = policies.decide(caller("alice"), SSN, "read");
        assertThat(ssn.allowed()).isFalse();
        assertThat(ssn.denied()).isTrue();
        assertThat(ssn.decision().policyId()).isEqualTo(2L);
        assertThat(ssn.accessType()).isEqualTo("read");
    }

    @Test
    void aDenialAboveWinsOverAnAllowanceBelow()
    {
        Policies.Verdict verdict = policies(snapshot()).decide(caller("mallory"), ORDERS, "read");
        assertThat(verdict.denied()).isTrue();
        assertThat(verdict.decision().policyId()).isEqualTo(4L);
    }

    @Test
    void givesTheMostSpecificAllowanceAndTheSnapshotsGroups()
    {
        Policies policies = policies(snapshot());
        Policies.Verdict verdict = policies.decide(caller("carol"), NAME, "read");
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.decision().policyId()).isEqualTo(3L);
        assertThat(policies.decide(caller("carol"), ORDERS, "read").allowed()).as("one family is not the table").isFalse();
        assertThat(policies.decide(caller("erin", "staff"), NAME, "read").allowed()).as("the system's groups count too").isTrue();
    }

    @Test
    void grantsTheFirstAccessTypeAllowedAndOtherwiseTellsTheDenial()
    {
        Policies policies = policies(snapshot());
        assertThat(policies.decide(caller("alice"), ORDERS, "write", "read").accessType()).isEqualTo("read");
        Policies.Verdict refused = policies.decide(caller("mallory"), ORDERS, "admin", "write");
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.denied()).as("the denial rather than the absence").isTrue();
        assertThat(refused.accessType()).isEqualTo("write");
        assertThat(policies.decide(caller("dave"), HBaseResource.global(), "admin").allowed()).isTrue();
        assertThat(policies.decide(caller("dave"), ORDERS, "admin").allowed()).as("global is the cluster, not every table").isFalse();
        assertThatThrownBy(() -> policies.decide(caller("alice"), ORDERS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tellsWhetherAUserMayUseAResourceOrSomethingWithinIt()
    {
        Policies policies = policies(snapshot());
        assertThat(policies.uses(caller("carol"), ORDERS, "read")).as("a column family of it").isTrue();
        assertThat(policies.uses(caller("carol"), HBaseResource.namespace("sales"), "write", "read")).isTrue();
        assertThat(policies.uses(caller("carol"), HBaseResource.table(TableName.valueOf("sales:items")), "read")).isFalse();
        assertThat(policies.uses(caller("alice"), HBaseResource.table(TableName.valueOf("sales:items")), "read")).isTrue();
        assertThat(policies.uses(caller("mallory"), ORDERS, "read")).as("denied above").isFalse();
        assertThat(policies.uses(caller("nobody"), ORDERS, "read")).isFalse();
    }

    @Test
    void refusesEverythingWithoutASnapshotAndLeavesAServiceNotInUseUnchecked()
    {
        Policies none = new Policies(null, new DecisionCache(0, Duration.ofSeconds(1)), false);
        assertThat(none.enforced()).isTrue();
        assertThat(none.namesColumns()).isFalse();
        Policies.Verdict verdict = none.decide(caller("alice"), ORDERS, "read");
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.denied()).isFalse();
        assertThat(verdict.decision().outcome()).isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
        assertThat(none.uses(caller("alice"), ORDERS, "read")).isFalse();
        assertThat(policies(new HBaseSnapshots().disabled().snapshot()).enforced()).isFalse();
        assertThat(policies(snapshot()).namesColumns()).isTrue();
    }

    @Test
    void refusesWhatTheEngineCannotDecide()
    {
        Snapshot broken = spy(snapshot());
        doThrow(new IllegalArgumentException("broken")).when(broken).decide(any(AccessRequest.class));
        assertThat(policies(broken).decide(caller("alice"), ORDERS, "read").allowed()).isFalse();
        assertThat(policies(snapshot()).decide(new Caller("alice", List.of(), null), ORDERS, "read").allowed()).as("without an address").isTrue();
    }
}
