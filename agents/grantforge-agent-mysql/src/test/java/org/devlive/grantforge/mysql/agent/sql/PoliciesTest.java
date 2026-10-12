// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoliciesTest
{
    private static AccessRequest column()
    {
        return AccessRequest.builder("alice", "select").resource("database", "sales").resource("table", "orders")
                .resource("column", "card").build();
    }

    private static AccessRequest table()
    {
        return AccessRequest.builder("alice", "select").resource("database", "sales").resource("table", "orders").build();
    }

    private static Fixtures.Policy restricted()
    {
        return new Fixtures.Policy().allow("sales", "orders", "*", List.of("alice"), "select")
                .mask("sales", "orders", "card", "alice", "mask_hash", null)
                .filter("sales", "orders", "alice", "region = 'EU'");
    }

    @Test
    void enforcesAccessBeforeAnySnapshotHasArrived()
    {
        Policies policies = Policies.of(null);

        assertThat(policies.enforced()).isTrue();
        assertThat(policies.version()).isEqualTo(-1);
        assertThat(policies.decide(column()).outcome()).isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
        assertThat(policies.decide(column()).allowed()).isFalse();
        assertThat(policies.decide(column()).policyVersion()).isNull();
        assertThat(policies.mask(column())).isNull();
        assertThat(policies.rowFilter(table())).isNull();
    }

    @Test
    void disabledServicesStopEnforcingAccessMasksAndFilters()
    {
        Snapshot snapshot = restricted().disabled().snapshot();
        Policies policies = Policies.of(snapshot);

        assertThat(policies.enforced()).isFalse();
        assertThat(policies.version()).isEqualTo(snapshot.policyVersion());
        assertThat(policies.decide(column()).determined()).isFalse();
        assertThat(policies.mask(column())).isNull();
        assertThat(policies.rowFilter(table())).isNull();
    }

    @Test
    void usesOneSnapshotForPermissionMaskAndRowFilterDecisions()
    {
        Snapshot snapshot = restricted().snapshot();
        Policies policies = Policies.of(snapshot);

        assertThat(policies.enforced()).isTrue();
        assertThat(policies.decide(column())).satisfies(decision -> {
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.policyVersion()).isEqualTo(snapshot.policyVersion());
        });
        assertThat(policies.mask(column())).isNotNull().satisfies(mask -> {
            assertThat(mask.policyVersion()).isEqualTo(snapshot.policyVersion());
            assertThat(mask.expression("`card`")).isEqualTo("SHA2(`card`, 256)");
        });
        assertThat(policies.rowFilter(table())).isNotNull().satisfies(filter -> {
            assertThat(filter.policyVersion()).isEqualTo(snapshot.policyVersion());
            assertThat(filter.expression()).isEqualTo("region = 'EU'");
        });
    }

    @Test
    void aRefreshedSnapshotRevokesAccessWithoutChangingTheStatementAlreadyBeingChecked()
    {
        Policies before = restricted().policies();
        Policies after = new Fixtures.Policy().policies();

        assertThat(after.version()).isNotEqualTo(before.version());
        assertThat(after.decide(column()).allowed()).isFalse();
        assertThat(after.mask(column())).isNull();
        assertThat(after.rowFilter(table())).isNull();
        assertThat(before.decide(column()).allowed()).as("a statement retains its original snapshot").isTrue();
        assertThat(before.mask(column())).isNotNull();
        assertThat(before.rowFilter(table())).isNotNull();
    }
}
