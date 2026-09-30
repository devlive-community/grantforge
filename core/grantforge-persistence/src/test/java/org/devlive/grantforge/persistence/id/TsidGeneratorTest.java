// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.id;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TsidGeneratorTest
{
    private static final long NOON = Instant.parse("2026-09-30T12:00:00Z").toEpochMilli();

    @ParameterizedTest
    @ValueSource(ints = {-1, 1024, Integer.MAX_VALUE})
    void rejectsNodesOutOfRange(int node)
    {
        assertThatIllegalArgumentException().isThrownBy(() -> new TsidGenerator(node));
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void rejectsNullClock()
    {
        assertThatNullPointerException().isThrownBy(() -> new TsidGenerator(1, null));
    }

    @Test
    void encodesTimestampAndNode()
    {
        long id = new TsidGenerator(TsidGenerator.MAX_NODE, () -> NOON).next();

        assertThat(id).isPositive();
        assertThat(TsidGenerator.timestampOf(id)).isEqualTo(Instant.ofEpochMilli(NOON));
        assertThat(TsidGenerator.nodeOf(id)).isEqualTo(TsidGenerator.MAX_NODE);
    }

    @Test
    void idsWithinOneMillisecondAreStrictlyIncreasing()
    {
        TsidGenerator generator = new TsidGenerator(3, () -> NOON);
        long first = generator.next();
        long second = generator.next();

        assertThat(second).isEqualTo(first + 1);
        assertThat(TsidGenerator.timestampOf(second)).isEqualTo(TsidGenerator.timestampOf(first));
    }

    @Test
    void exhaustedCounterBorrowsTheNextMillisecond()
    {
        TsidGenerator generator = new TsidGenerator(0, () -> NOON);
        long previous = generator.next();
        for (int i = 1; i < (1 << TsidGenerator.COUNTER_BITS); i++) {
            long id = generator.next();
            assertThat(id).isGreaterThan(previous);
            previous = id;
        }
        long borrowed = generator.next();

        assertThat(borrowed).isGreaterThan(previous);
        assertThat(TsidGenerator.timestampOf(borrowed)).isEqualTo(Instant.ofEpochMilli(NOON + 1));
    }

    @Test
    void clockMovingBackwardsNeverProducesSmallerIds()
    {
        AtomicLong clock = new AtomicLong(NOON);
        TsidGenerator generator = new TsidGenerator(5, clock::get);
        long before = generator.next();
        clock.set(NOON - 5_000);
        long after = generator.next();
        clock.set(NOON + 1);
        long later = generator.next();

        assertThat(after).isGreaterThan(before);
        assertThat(later).isGreaterThan(after);
        assertThat(TsidGenerator.timestampOf(later)).isEqualTo(Instant.ofEpochMilli(NOON + 1));
    }

    @Test
    void rejectsClockBeforeEpoch()
    {
        TsidGenerator generator = new TsidGenerator(0, () -> TsidGenerator.EPOCH - 1);

        assertThatIllegalStateException().isThrownBy(generator::next).withMessageContaining("outside the TSID range");
    }

    @Test
    void rejectsClockBeyondTheTimestampRange()
    {
        TsidGenerator generator = new TsidGenerator(0, () -> TsidGenerator.EPOCH + (1L << 41));

        assertThatIllegalStateException().isThrownBy(generator::next);
    }

    @Test
    void concurrentCallersNeverReceiveDuplicates() throws Exception
    {
        TsidGenerator generator = new TsidGenerator(7);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        int threads = 8;
        int perThread = 20_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.next());
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }
        finally {
            pool.shutdownNow();
        }

        assertThat(ids).hasSize(threads * perThread);
    }
}
