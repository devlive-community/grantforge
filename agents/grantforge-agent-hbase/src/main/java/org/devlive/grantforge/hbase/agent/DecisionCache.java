// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * The decisions of one snapshot, kept for the requests a region server repeats on every read and write. A new snapshot
 * starts afresh, so a policy change applies at once; decisions also expire after a while, for policies that apply only
 * in some periods. A thread that still holds an older snapshot decides without the cache rather than evict it.
 */
// A snapshot is the same when it is the same object: one parsed anew starts afresh even with the same version.
@SuppressWarnings({"PMD.AvoidUsingVolatile", "PMD.CompareObjectsWithEquals"})
final class DecisionCache
{
    private final long size;
    private final Duration ttl;
    private final Ticker ticker;
    private final ReentrantLock swap = new ReentrantLock();
    private volatile @Nullable Generation current;

    /** The decisions of one snapshot. */
    private static final class Generation
    {
        final Snapshot snapshot;
        final Cache<List<String>, AgentDecision> decisions;

        Generation(Snapshot snapshot, Cache<List<String>, AgentDecision> decisions)
        {
            this.snapshot = snapshot;
            this.decisions = decisions;
        }
    }

    /**
     * Creates a cache.
     *
     * @param size the most decisions kept; zero keeps none
     * @param ttl how long a decision is kept
     */
    DecisionCache(long size, Duration ttl)
    {
        this(size, ttl, Ticker.systemTicker());
    }

    DecisionCache(long size, Duration ttl, Ticker ticker)
    {
        if (size < 0) {
            throw new IllegalArgumentException("the size must not be negative");
        }
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("decisions must be kept for some time");
        }
        this.size = size;
        this.ttl = ttl;
        this.ticker = ticker;
    }

    /**
     * Returns the decision of a request under a snapshot, deciding it when it is not kept.
     *
     * @param snapshot the snapshot the request is decided under
     * @param key everything the decision depends on besides the snapshot
     * @param decider decides the request
     * @return the decision
     */
    AgentDecision decide(Snapshot snapshot, List<String> key, Function<List<String>, AgentDecision> decider)
    {
        Generation generation = size == 0 ? null : generation(snapshot);
        return generation == null ? decider.apply(key) : generation.decisions.get(key, decider);
    }

    private @Nullable Generation generation(Snapshot snapshot)
    {
        Generation now = current;
        if (now != null && now.snapshot == snapshot) {
            return now;
        }
        swap.lock();
        try {
            now = current;
            if (now != null && now.snapshot == snapshot) {
                return now;
            }
            if (now != null && snapshot.policyVersion() < now.snapshot.policyVersion()) {
                return null;
            }
            Generation next = new Generation(snapshot, Caffeine.newBuilder().maximumSize(size)
                    .expireAfterWrite(ttl.toNanos(), TimeUnit.NANOSECONDS).ticker(ticker).executor(Runnable::run)
                    .<List<String>, AgentDecision>build());
            current = next;
            if (now != null) {
                now.decisions.invalidateAll();
            }
            return next;
        }
        finally {
            swap.unlock();
        }
    }

    /**
     * Returns how many decisions are kept, for tests.
     *
     * @return the count
     */
    long kept()
    {
        Generation now = current;
        if (now == null) {
            return 0;
        }
        now.decisions.cleanUp();
        return now.decisions.estimatedSize();
    }
}
