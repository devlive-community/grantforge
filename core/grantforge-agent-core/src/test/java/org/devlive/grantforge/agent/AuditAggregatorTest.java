// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditAggregatorTest
{
    private static final Instant NOON = Instant.parse("2026-10-17T12:00:00Z");

    /** A clock the test moves. */
    private static final class Hands
            extends Clock
    {
        private Instant now = NOON;

        @Override
        public ZoneOffset getZone()
        {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone)
        {
            return this;
        }

        @Override
        public Instant instant()
        {
            return now;
        }
    }

    private final List<AccessEvent> sent = new CopyOnWriteArrayList<>();
    private final Hands clock = new Hands();

    private static AccessEvent produce(String user, boolean allowed, Instant when)
    {
        return AccessEvent.builder(user, "orders", "write", allowed).occurredAt(when).resourceType("topic").action("PRODUCE").build();
    }

    private AuditAggregator aggregator(int maxPending)
    {
        return new AuditAggregator(Duration.ofSeconds(60), sent::add, clock, maxPending, null);
    }

    @Test
    void foldsAllowedAccessesOfAWindowAndSendsRefusalsAtOnce()
    {
        AuditAggregator aggregator = aggregator(100);
        aggregator.record(produce("alice", true, NOON));
        aggregator.record(produce("alice", true, NOON.plusSeconds(20)));
        aggregator.record(produce("alice", true, NOON.plusSeconds(10)));
        aggregator.record(produce("bob", false, NOON.plusSeconds(30)));
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).fields()).containsEntry("user", "bob").containsEntry("outcome", "DENIED");
        assertThat(aggregator.pending()).isEqualTo(1);

        clock.now = NOON.plusSeconds(59);
        aggregator.flush();
        assertThat(sent).as("the window has not ended").hasSize(1);
        clock.now = NOON.plusSeconds(60);
        aggregator.flush();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).count()).isEqualTo(3);
        assertThat(sent.get(1).fields()).containsEntry("lastOccurredAt", "2026-10-17T12:00:20Z")
                .containsEntry("occurredAt", "2026-10-17T12:00:00Z");
        assertThat(aggregator.pending()).isZero();
    }

    @Test
    void aSingleAccessIsSentAsItIs()
    {
        AuditAggregator aggregator = aggregator(100);
        AccessEvent once = produce("alice", true, NOON);
        aggregator.record(once);
        aggregator.close();
        assertThat(sent).containsExactly(once);
    }

    @Test
    void sendsTheOldestEarlyWhenTooManyWait()
    {
        AuditAggregator aggregator = aggregator(2);
        aggregator.record(produce("alice", true, NOON));
        aggregator.record(produce("alice", true, NOON));
        aggregator.record(produce("bob", true, NOON));
        aggregator.record(produce("carol", true, NOON));
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).fields()).containsEntry("user", "alice").containsEntry("count", 2);
        aggregator.close();
        assertThat(sent).extracting(event -> event.fields().get("user")).containsExactly("alice", "bob", "carol");
    }

    @Test
    void aZeroWindowSendsEveryAccess()
    {
        AuditAggregator aggregator = AuditAggregator.start(Duration.ZERO, sent::add);
        aggregator.record(produce("alice", true, NOON));
        aggregator.record(produce("alice", true, NOON));
        assertThat(sent).hasSize(2);
        assertThat(aggregator.pending()).isZero();
        aggregator.close();
    }

    @Test
    void theBackgroundThreadSendsEndedWindows() throws InterruptedException
    {
        AuditAggregator aggregator = AuditAggregator.start(Duration.ofMillis(200), sent::add);
        aggregator.record(produce("alice", true, Instant.now()));
        aggregator.record(produce("alice", true, Instant.now()));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (sent.isEmpty() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).count()).isEqualTo(2);
        aggregator.close();
    }

    @Test
    void closingStopsTheTimerAndSendsWhatWaits()
    {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        AuditAggregator aggregator = new AuditAggregator(Duration.ofHours(1), sent::add, clock, 10, timer);
        aggregator.record(produce("alice", true, NOON));
        aggregator.close();
        assertThat(timer.isShutdown()).isTrue();
        assertThat(sent).hasSize(1);
    }

    @Test
    void refusesUnusableSettings()
    {
        assertThatThrownBy(() -> new AuditAggregator(Duration.ofSeconds(-1), sent::add, clock, 10, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditAggregator(Duration.ofSeconds(1), sent::add, clock, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
