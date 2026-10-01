// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.id;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;

/**
 * Generates time-sorted 64-bit IDs (TSID).
 *
 * <p>Layout, from the most significant bit: 1 sign bit (always 0), 41 bits of milliseconds since
 * {@link #EPOCH} (about 69 years), {@value #NODE_BITS} bits of node and {@value #COUNTER_BITS} bits of a
 * per-millisecond counter. IDs from one generator are strictly increasing, so ordering by ID orders by
 * creation time, which keeps B-tree inserts at the right edge of the index on every database.
 *
 * <p>Each application instance of a cluster must use a distinct node (0-{@value #MAX_NODE}); two
 * instances sharing a node can produce the same ID in the same millisecond.
 *
 * <p>Instances are thread-safe.
 */
public final class TsidGenerator
{
    /** Custom epoch; IDs cannot be generated for earlier clock values. */
    public static final long EPOCH = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    /** Number of bits holding the node. */
    public static final int NODE_BITS = 10;
    /** Number of bits holding the per-millisecond counter. */
    public static final int COUNTER_BITS = 12;
    /** Largest valid node value. */
    public static final int MAX_NODE = (1 << NODE_BITS) - 1;

    private static final long MAX_COUNTER = (1L << COUNTER_BITS) - 1;
    private static final long MAX_ELAPSED = (1L << (Long.SIZE - 1 - NODE_BITS - COUNTER_BITS)) - 1;

    private final long node;
    private final LongSupplier clock;
    // A ReentrantLock rather than synchronized: it does not pin virtual threads on JDK 21-23.
    private final ReentrantLock lock = new ReentrantLock();

    // Guarded by lock.
    private long lastMillis = Long.MIN_VALUE;
    private long counter;

    /**
     * Creates a generator using the system clock.
     *
     * @param node this instance's node, 0-{@value #MAX_NODE}
     * @throws IllegalArgumentException if {@code node} is out of range
     */
    public TsidGenerator(int node)
    {
        this(node, System::currentTimeMillis);
    }

    /**
     * Creates a generator with an explicit clock (used by tests).
     *
     * @param node this instance's node, 0-{@value #MAX_NODE}
     * @param clock returns the current time in epoch milliseconds
     * @throws IllegalArgumentException if {@code node} is out of range
     */
    TsidGenerator(int node, LongSupplier clock)
    {
        if (node < 0 || node > MAX_NODE) {
            throw new IllegalArgumentException("node must be between 0 and " + MAX_NODE + " but was " + node);
        }
        this.node = node;
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns the node this generator writes into every ID.
     *
     * @return the node, 0-{@value #MAX_NODE}
     */
    public int node()
    {
        return (int) node;
    }

    /**
     * Returns the next ID.
     *
     * @return a positive ID greater than every ID previously returned by this generator
     * @throws IllegalStateException if the clock is before {@link #EPOCH} or the timestamp range is exhausted
     */
    public long next()
    {
        long now = clock.getAsLong();
        lock.lock();
        try {
            return nextLocked(now);
        }
        finally {
            lock.unlock();
        }
    }

    private long nextLocked(long now)
    {
        if (now <= lastMillis) {
            // Same millisecond, or the clock moved backwards (NTP adjustment): stay on the last
            // millisecond and count up; when the counter is exhausted, borrow the next millisecond
            // instead of blocking. Either way IDs remain strictly increasing.
            counter++;
            if (counter > MAX_COUNTER) {
                counter = 0;
                lastMillis++;
            }
        }
        else {
            lastMillis = now;
            counter = 0;
        }
        long elapsed = lastMillis - EPOCH;
        if (elapsed < 0 || elapsed > MAX_ELAPSED) {
            throw new IllegalStateException("clock value " + lastMillis + " is outside the TSID range");
        }
        return (elapsed << (NODE_BITS + COUNTER_BITS)) | (node << COUNTER_BITS) | counter;
    }

    /**
     * Returns the creation time encoded in an ID.
     *
     * @param id an ID produced by any {@code TsidGenerator}
     * @return the encoded instant (millisecond precision)
     */
    public static Instant timestampOf(long id)
    {
        return Instant.ofEpochMilli((id >>> (NODE_BITS + COUNTER_BITS)) + EPOCH);
    }

    /**
     * Returns the node encoded in an ID.
     *
     * @param id an ID produced by any {@code TsidGenerator}
     * @return the node, 0-{@value #MAX_NODE}
     */
    public static int nodeOf(long id)
    {
        return (int) ((id >>> COUNTER_BITS) & MAX_NODE);
    }
}
