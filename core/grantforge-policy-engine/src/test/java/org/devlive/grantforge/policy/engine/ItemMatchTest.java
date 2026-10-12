// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMatchTest
{
    @Test
    void identifiesAnItemByBothItsFullPolicyIdAndItsPosition()
    {
        long policyId = (long) Integer.MAX_VALUE + 10;
        ItemMatch match = new ItemMatch(policyId, 2);
        Map<ItemMatch, String> transformations = Map.of(match, "SHA2(card, 256)");

        assertThat(match.policyId()).isEqualTo(policyId);
        assertThat(match.item()).isEqualTo(2);
        assertThat(match).isEqualTo(new ItemMatch(policyId, 2))
                .hasSameHashCodeAs(new ItemMatch(policyId, 2))
                .isNotEqualTo(new ItemMatch(policyId, 1))
                .isNotEqualTo(new ItemMatch(policyId - 1, 2))
                .isNotEqualTo(new ItemMatch((int) policyId, 2))
                .isNotEqualTo("item").isNotEqualTo(null);
        assertThat(transformations.get(new ItemMatch(policyId, 2))).isEqualTo("SHA2(card, 256)");
        assertThat(transformations.get(new ItemMatch(policyId, 1))).isNull();
        assertThat(match).hasToString("item 2 of policy " + policyId);
    }

    @Test
    void locatesTheFirstMatchingPayloadOfTheSelectedOverridePolicy()
    {
        Policy normal = table(1).allow(item("alice", "bob")).build();
        Policy override = table(20).priority(Priority.OVERRIDE).allow(item("carol"), item("alice", "bob"), item("alice"))
                .allowExceptions(item("bob")).build();
        PolicyEngine engine = PolicyEngine.create(Models.HIVE, List.of(normal, override), Map.of());
        Map<ItemMatch, String> transformations = Map.of(new ItemMatch(1, 0), "normal", new ItemMatch(20, 1), "override");

        ItemMatch alice = engine.select(request("alice"));
        assertThat(alice).isNotNull();
        assertThat(alice.policyId()).isEqualTo(20);
        assertThat(alice.item()).isEqualTo(1);
        assertThat(transformations.get(alice)).isEqualTo("override");
        ItemMatch bob = engine.select(request("bob"));
        assertThat(bob).isNotNull();
        assertThat(transformations.get(bob)).isEqualTo("normal");
        assertThat(engine.select(request("dave"))).isNull();
    }

    @Test
    void retainsTheOriginalAllowItemPositionWhenEarlierItemsDoNotMatch()
    {
        long policyId = (long) Integer.MAX_VALUE + 10;
        Policy policy = table(policyId).allow(item("bob"), PolicyItem.builder().users("alice").accessTypes("update").build(),
                item("alice")).build();
        ItemMatch match = PolicyEngine.create(Models.HIVE, List.of(policy), Map.of()).select(request("alice"));

        assertThat(match).isNotNull();
        assertThat(match).isEqualTo(new ItemMatch(policyId, 2));
        assertThat(policy.allow().get(match.item()).users()).containsExactly("alice");
    }

    private static Policy.Builder table(long id)
    {
        return Policy.builder(id).resource("database", ResourceSpec.of("sales")).resource("table", ResourceSpec.of("orders"));
    }

    private static PolicyItem item(String... users)
    {
        return PolicyItem.builder().users(users).accessTypes("select").build();
    }

    private static AccessRequest request(String user)
    {
        return AccessRequest.builder(user, "select").resource("database", "sales").resource("table", "orders").build();
    }
}
