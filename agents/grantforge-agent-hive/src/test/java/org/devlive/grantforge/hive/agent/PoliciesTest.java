// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoliciesTest
{
    private static AccessRequest select(String user, String... levels)
    {
        return new HiveCheck("select", levels).request(user, List.of(), null);
    }

    @Test
    void withoutASnapshotEverythingIsRefused()
    {
        Policies none = Policies.of(null);
        assertThat(none.enforced()).isTrue();
        assertThat(none.decide(select("alice", "database", "sales")).outcome()).isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
        assertThat(none.mask(select("alice", "database", "sales", "table", "orders", "column", "card"))).isNull();
        assertThat(none.rowFilter(select("alice", "database", "sales", "table", "orders"))).isNull();
        assertThat(none.rolesOf("alice")).isEmpty();
    }

    @Test
    void answersFromTheSnapshot()
    {
        Policies policies = new HiveSnapshots().allow(List.of("alice"), List.of("select"), "database", "sales", "table", "*", "column", "*")
                .mask(List.of("alice"), "mask_hash", null, "database", "sales", "table", "orders", "column", "card")
                .filter(List.of("alice"), "region = 'EU'", "database", "sales", "table", "orders").role("analysts", "alice").policies();
        assertThat(policies.enforced()).isTrue();
        assertThat(policies.decide(select("alice", "database", "sales", "table", "orders")).allowed()).isTrue();
        assertThat(policies.decide(select("bob", "database", "sales", "table", "orders")).allowed()).isFalse();
        assertThat(policies.mask(select("alice", "database", "sales", "table", "orders", "column", "card"))).isNotNull();
        assertThat(policies.rowFilter(select("alice", "database", "sales", "table", "orders"))).isNotNull();
        assertThat(policies.rolesOf("alice")).containsExactly("analysts");
        assertThat(new HiveSnapshots().disabled().policies().enforced()).isFalse();
    }
}
