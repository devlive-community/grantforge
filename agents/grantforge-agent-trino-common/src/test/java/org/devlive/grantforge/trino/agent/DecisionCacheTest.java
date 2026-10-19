// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionCacheTest
{
    private static final List<String> KEY = List.of("at", "alice", "analysts", "catalog", "sales", "select");

    private final AtomicLong nanos = new AtomicLong();
    private final AtomicInteger decided = new AtomicInteger();

    private static Snapshot snapshot(long version)
    {
        return new TrinoSnapshots().version(version).allow(List.of("alice"), List.of("select"), "catalog", "sales").snapshot();
    }

    private Function<List<String>, AgentDecision> decider(Snapshot snapshot)
    {
        return key -> {
            decided.incrementAndGet();
            return snapshot.decide(AccessRequest.builder(key.get(1), key.get(5)).resource(key.get(3), key.get(4)).build());
        };
    }

    @Test
    void keepsTheDecisionsOfASnapshot()
    {
        DecisionCache cache = new DecisionCache(100, Duration.ofMinutes(1), nanos::get);
        Snapshot snapshot = snapshot(1);
        assertThat(cache.kept()).isZero();
        assertThat(cache.decide(snapshot, KEY, decider(snapshot)).allowed()).isTrue();
        assertThat(cache.decide(snapshot, KEY, decider(snapshot)).allowed()).isTrue();
        assertThat(decided).hasValue(1);
        assertThat(cache.kept()).isOne();
    }

    @Test
    void startsAfreshWithANewSnapshotAndLeavesItToOlderOnes()
    {
        DecisionCache cache = new DecisionCache(100, Duration.ofMinutes(1), nanos::get);
        Snapshot first = snapshot(1);
        Snapshot second = snapshot(2);
        cache.decide(first, KEY, decider(first));
        cache.decide(second, KEY, decider(second));
        assertThat(decided).as("a new snapshot decides again").hasValue(2);
        cache.decide(first, KEY, decider(first));
        cache.decide(first, KEY, decider(first));
        assertThat(decided).as("a thread with the older snapshot decides without the cache").hasValue(4);
        cache.decide(second, KEY, decider(second));
        assertThat(decided).as("the newer snapshot's decisions stay").hasValue(4);
        Snapshot again = snapshot(2);
        cache.decide(again, KEY, decider(again));
        assertThat(decided).as("the same version parsed anew is a new snapshot").hasValue(5);
    }

    @Test
    void forgetsDecisionsAfterTheirTime()
    {
        DecisionCache cache = new DecisionCache(100, Duration.ofSeconds(10), nanos::get);
        Snapshot snapshot = snapshot(1);
        cache.decide(snapshot, KEY, decider(snapshot));
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(9));
        cache.decide(snapshot, KEY, decider(snapshot));
        assertThat(decided).hasValue(1);
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(2));
        cache.decide(snapshot, KEY, decider(snapshot));
        assertThat(decided).hasValue(2);
    }

    @Test
    void keepsAtMostItsSizeAndNothingWithZero()
    {
        DecisionCache bounded = new DecisionCache(2, Duration.ofMinutes(1), nanos::get);
        Snapshot snapshot = snapshot(1);
        for (String catalog : List.of("a", "b", "c", "d")) {
            bounded.decide(snapshot, List.of("at", "alice", "", "catalog", catalog, "select"), decider(snapshot));
        }
        assertThat(bounded.kept()).isLessThanOrEqualTo(2);
        DecisionCache none = new DecisionCache(0, Duration.ofMinutes(1));
        none.decide(snapshot, KEY, decider(snapshot));
        none.decide(snapshot, KEY, decider(snapshot));
        assertThat(decided).hasValue(6);
        assertThat(none.kept()).isZero();
    }

    @Test
    void refusesANegativeSizeAndNoTime()
    {
        assertThatThrownBy(() -> new DecisionCache(-1, Duration.ofSeconds(1))).hasMessageContaining("negative");
        assertThatThrownBy(() -> new DecisionCache(1, Duration.ZERO)).hasMessageContaining("some time");
        assertThatThrownBy(() -> new DecisionCache(1, Duration.ofSeconds(-1))).hasMessageContaining("some time");
    }
}
