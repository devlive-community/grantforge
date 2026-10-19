// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

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
    private static final Caller ALICE = new Caller("alice", List.of("analysts"), "q1");
    private static final TrinoResource ORDERS = TrinoResource.table("hive", "sales", "orders");

    private static Policies policies(TrinoSnapshots snapshots)
    {
        return new Policies(snapshots.snapshot(), new DecisionCache(100, Duration.ofMinutes(1)));
    }

    @Test
    void refusesEverythingWithoutASnapshot()
    {
        Policies none = new Policies(null, new DecisionCache(0, Duration.ofSeconds(1)));
        assertThat(none.enforced()).isTrue();
        assertThat(none.received()).isFalse();
        Policies.Verdict verdict = none.decide(ALICE, ORDERS, "select");
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.denied()).isFalse();
        assertThat(verdict.accessType()).isEqualTo("select");
        assertThat(none.uses(ALICE, ORDERS, "select")).isFalse();
        assertThat(none.rowFilter(ALICE, ORDERS)).isNull();
        assertThat(none.mask(ALICE, TrinoResource.column("hive", "sales", "orders", "amount"))).isNull();
    }

    @Test
    void grantsByAPermissionOnAnyScopeAbove()
    {
        Policies policies = policies(new TrinoSnapshots().allow(List.of("@analysts"), List.of("select"), "catalog", "hive", "schema", "sales"));
        assertThat(policies.received()).isTrue();
        assertThat(policies.decide(ALICE, TrinoResource.column("hive", "sales", "orders", "amount"), "select").allowed()).isTrue();
        assertThat(policies.decide(ALICE, TrinoResource.table("hive", "hr", "people"), "select").allowed()).isFalse();
        assertThat(policies.decide(ALICE, ORDERS, "insert", "select").accessType()).as("the first allowed type").isEqualTo("select");
    }

    @Test
    void refusesByADenialOnAnyScope()
    {
        Policies policies = policies(new TrinoSnapshots().allow(List.of("alice"), List.of("all"), "catalog", "hive")
                .deny(List.of("alice"), List.of("drop"), "catalog", "hive", "schema", "sales", "table", "orders"));
        assertThat(policies.decide(ALICE, ORDERS, "select").allowed()).isTrue();
        Policies.Verdict drop = policies.decide(ALICE, ORDERS, "drop");
        assertThat(drop.denied()).isTrue();
        assertThat(drop.decision().policyId()).isEqualTo(2L);
        assertThat(policies.decide(ALICE, ORDERS, "alter", "drop").denied()).as("a denial is the refusal that tells").isFalse();
        assertThat(policies.decide(ALICE, ORDERS, "drop", "execute").denied()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> policies.decide(ALICE, ORDERS));
    }

    @Test
    void keepsRoutinesApartFromTables()
    {
        Policies policies = policies(new TrinoSnapshots().allow(List.of("alice"), List.of("create_function", "execute"), "catalog", "hive", "schema",
                "udf", "function", "*"));
        assertThat(policies.decide(ALICE, TrinoResource.function("hive", "udf", "clean"), "execute").allowed()).isTrue();
        assertThat(policies.decide(ALICE, TrinoResource.table("hive", "udf", "scratch"), "create").allowed())
                .as("a grant on routines is none on tables").isFalse();
        assertThat(policies.decide(ALICE, TrinoResource.procedure("hive", "udf", "clean"), "execute").allowed()).isFalse();
    }

    @Test
    void seesWhatHasSomethingAllowedWithin()
    {
        Policies policies = policies(new TrinoSnapshots().allow(List.of("alice"), List.of("select"), "catalog", "hive", "schema", "sales", "table",
                "orders").deny(List.of("alice"), List.of("select"), "catalog", "hive", "schema", "hr"));
        assertThat(policies.uses(ALICE, TrinoResource.catalog("hive"), "insert", "select")).isTrue();
        assertThat(policies.uses(ALICE, TrinoResource.schema("hive", "sales"), "select")).isTrue();
        assertThat(policies.uses(ALICE, ORDERS, "select")).isTrue();
        assertThat(policies.uses(ALICE, TrinoResource.table("hive", "sales", "customers"), "select")).isFalse();
        assertThat(policies.uses(ALICE, TrinoResource.schema("hive", "hr"), "select")).isFalse();
        assertThat(policies.uses(ALICE, TrinoResource.catalog("iceberg"), "select")).isFalse();
    }

    @Test
    void findsRowFiltersAndMasks()
    {
        Policies policies = policies(new TrinoSnapshots().filter(List.of("alice"), "region = 'EU'", "catalog", "hive", "schema", "sales", "table",
                "orders").mask(List.of("alice"), "mask_hash", null, "catalog", "hive", "schema", "sales", "table", "orders", "column", "customer"));
        assertThat(policies.rowFilter(ALICE, ORDERS)).isNotNull().extracting(filter -> filter.expression()).isEqualTo("region = 'EU'");
        assertThat(policies.rowFilter(new Caller("bob", List.of(), null), ORDERS)).isNull();
        assertThat(policies.mask(ALICE, TrinoResource.column("hive", "sales", "orders", "customer"))).isNotNull();
        assertThat(policies.mask(ALICE, TrinoResource.column("hive", "sales", "orders", "amount"))).isNull();
    }

    @Test
    void leavesTrinoUncheckedWhileTheServiceIsNotInUse()
    {
        assertThat(policies(new TrinoSnapshots().disabled()).enforced()).isFalse();
    }

    @Test
    void decidesNothingWhenTheSnapshotBreaks()
    {
        Snapshot broken = mock(Snapshot.class);
        when(broken.serviceEnabled()).thenReturn(true);
        when(broken.decide(any())).thenThrow(new IllegalStateException("corrupt"));
        Policies policies = new Policies(broken, new DecisionCache(0, Duration.ofSeconds(1)));
        Policies.Verdict verdict = policies.decide(ALICE, ORDERS, "select");
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.denied()).isFalse();
        assertThat(verdict.decision().outcome()).isEqualTo(AgentDecision.withoutSnapshot().outcome());
    }
}
