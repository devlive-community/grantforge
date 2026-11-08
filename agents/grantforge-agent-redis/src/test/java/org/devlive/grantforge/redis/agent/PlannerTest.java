// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerTest
{
    private final List<String> withheld = new ArrayList<>();

    private Plan plan(RedisSnapshots policies, String user)
    {
        return new Planner(policies.snapshot(), List.of("p:6379", "r:6379"), Instant.now(), withheld::add).plan(user);
    }

    @Test
    void givesNothingWithoutAPolicy()
    {
        assertThat(plan(new RedisSnapshots(), "alice")).isEqualTo(new Plan(List.of(), Set.of()));
    }

    @Test
    void holdsKeysReadWrittenOrBothInOneSelector()
    {
        Plan plan = plan(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "sales:*").allow(List.of("alice"), List.of("write"), "key", "cart:*")
                .allow(List.of("alice"), List.of("read", "write"), "key", "orders:*"), "alice");
        assertThat(plan.selectors()).singleElement().extracting(Selector::rule)
                .isEqualTo("(%R~sales:* %W~cart:* ~orders:* -@all +@read +@write -@dangerous -scan -randomkey)");
    }

    @Test
    void aUserThatMayReadEveryKeyMayListThem()
    {
        Plan plan = plan(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "*"), "alice");
        assertThat(plan.selectors()).singleElement().extracting(Selector::rule).isEqualTo("(%R~* -@all +@read -@dangerous)");
    }

    @Test
    void onlyWritingGivesNoReading()
    {
        Plan plan = plan(new RedisSnapshots().allow(List.of("alice"), List.of("write"), "key", "cart:*"), "alice");
        assertThat(plan.selectors()).singleElement().extracting(Selector::rule).isEqualTo("(%W~cart:* -@all +@write -@dangerous -scan -randomkey)");
    }

    @Test
    void withholdsWhatPoliciesDecideDifferentlyWithinOrRedisCannotTake()
    {
        Plan plan = plan(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "hr:*").deny(List.of("alice"), List.of("read"), "key", "hr:salary:*")
                .allow(List.of("alice"), List.of("read"), "key", "hr[1]"), "alice");
        assertThat(plan.selectors()).isEmpty();
        assertThat(withheld).containsExactlyInAnyOrder("read of keys hr:* for user alice, as policies decide keys within it differently",
                "read of keys hr[1] for user alice, as Redis cannot take it as a pattern of its own");
    }

    @Test
    void holdsChannelsToSubscribeToAndPublishToApart()
    {
        Plan plan = plan(new RedisSnapshots().allow(List.of("alice"), List.of("subscribe"), "channel", "news:*").allow(List.of("alice"), List.of("publish"), "channel",
                "alerts"), "alice");
        assertThat(plan.selectors()).extracting(Selector::rule).containsExactly("(&news:* -@all +subscribe +psubscribe +ssubscribe +unsubscribe +punsubscribe +sunsubscribe)",
                "(&alerts -@all +publish +spublish)");
    }

    @Test
    void monitorsTheNodesAPolicyNames()
    {
        Plan plan = plan(new RedisSnapshots().allow(List.of("alice"), List.of("monitor"), "node", "P:*"), "alice");
        assertThat(plan.monitored()).as("node names compare without case").containsExactly("p:6379");
    }
}
