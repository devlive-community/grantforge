// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Selecting the one item of masking and row filtering policies that applies. */
class PolicyEngineSelectTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    private static Policy.Builder column(long id, String table, String column)
    {
        return Policy.builder(id).resource("database", ResourceSpec.of("sales")).resource("table", ResourceSpec.of(table))
                .resource("column", ResourceSpec.of(column));
    }

    private static PolicyItem item(String... users)
    {
        return PolicyItem.builder().users(users).accessTypes("select").build();
    }

    private static AccessRequest request(String user, String table, String column)
    {
        return AccessRequest.builder(user, "select").groups("analysts").resource("database", "sales").resource("table", table)
                .resource("column", column).time(NOW).build();
    }

    private static @Nullable ItemMatch select(List<Policy> policies, AccessRequest request)
    {
        return PolicyEngine.create(Models.HIVE, policies, Map.of()).select(request);
    }

    @Test
    void theFirstMatchingItemOfTheSmallestPolicyApplies()
    {
        Policy cards = column(7, "orders", "card").allow(item("bob"), item("alice"), PolicyItem.builder().groups("analysts")
                .accessTypes("select").build()).build();
        Policy everything = column(3, "orders", "*").allow(item("carol")).build();
        Policy later = column(9, "orders", "card").allow(item("alice")).build();
        List<Policy> policies = List.of(later, cards, everything);

        assertThat(select(policies, request("alice", "orders", "card"))).isEqualTo(new ItemMatch(7, 1));
        assertThat(select(policies, request("dave", "orders", "card"))).as("through the group").isEqualTo(new ItemMatch(7, 2));
        assertThat(select(policies, request("carol", "orders", "card"))).isEqualTo(new ItemMatch(3, 0));
        assertThat(select(policies, request("alice", "orders", "id"))).isNull();
    }

    @Test
    void overridePoliciesComeFirstAndExceptionsCarveOut()
    {
        Policy normal = column(1, "orders", "card").allow(item("alice", "bob")).build();
        Policy override = column(5, "orders", "card").priority(Priority.OVERRIDE).allow(item("alice", "bob"))
                .allowExceptions(item("bob")).build();
        List<Policy> policies = List.of(normal, override);

        assertThat(select(policies, request("alice", "orders", "card"))).isEqualTo(new ItemMatch(5, 0));
        assertThat(select(policies, request("bob", "orders", "card"))).as("excepted from the override").isEqualTo(new ItemMatch(1, 0));
    }

    @Test
    void ignoresDenyItemsExpiredPoliciesAndOtherAccess()
    {
        Policy denying = column(1, "orders", "card").deny(item("alice")).build();
        Policy expired = column(2, "orders", "card").allow(item("alice")).validity(Validity.between(null, Instant.parse("2020-01-01T00:00:00Z")))
                .build();
        Policy updates = column(3, "orders", "card").allow(PolicyItem.builder().users("alice").accessTypes("update").build()).build();
        assertThat(select(List.of(denying, expired, updates), request("alice", "orders", "card"))).isNull();
        Policy all = column(4, "orders", "card").allow(PolicyItem.builder().users("alice").accessTypes("all").build()).build();
        assertThat(select(List.of(all), request("alice", "orders", "card"))).as("all grants select").isEqualTo(new ItemMatch(4, 0));
    }

    @Test
    void conditionsWithoutAnEvaluatorDoNotSelect()
    {
        PolicyItem conditional = PolicyItem.builder().users("alice").accessTypes("select").conditions(Condition.of("ip", "10.0.0.0/8")).build();
        Policy policy = column(1, "orders", "card").allow(conditional, item("alice")).build();
        assertThat(select(List.of(policy), request("alice", "orders", "card"))).isEqualTo(new ItemMatch(1, 1));
        ConditionEvaluator always = (values, request) -> true;
        assertThat(PolicyEngine.create(Models.HIVE, List.of(policy), Map.of("ip", always)).select(request("alice", "orders", "card")))
                .isEqualTo(new ItemMatch(1, 0));
    }

    @Test
    void tablesAreSelectedForRowFilters()
    {
        Policy filter = Policy.builder(1).resource("database", ResourceSpec.of("sales")).resource("table", ResourceSpec.of("ord*"))
                .allow(item("alice")).build();
        AccessRequest table = AccessRequest.builder("alice", "select").resource("database", "sales").resource("table", "Orders").time(NOW)
                .build();
        assertThat(select(List.of(filter), table)).as("the HIVE model compares without case").isEqualTo(new ItemMatch(1, 0));
        assertThatThrownBy(() -> select(List.of(filter), AccessRequest.builder("alice", "select").resource("table", "orders").build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownAccessTypeSelectsNothing()
    {
        Policy policy = column(1, "orders", "card").allow(item("alice")).build();
        assertThat(select(List.of(policy), AccessRequest.builder("alice", "export").resource("database", "sales").resource("table", "orders")
                .resource("column", "card").build())).isNull();
        assertThat(new ItemMatch(1, 2)).hasToString("item 2 of policy 1").isNotEqualTo(new ItemMatch(1, 3)).isNotEqualTo("item")
                .hasSameHashCodeAs(new ItemMatch(1, 2));
    }
}
