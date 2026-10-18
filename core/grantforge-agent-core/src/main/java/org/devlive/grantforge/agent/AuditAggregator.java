// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Folds identical allowed accesses into one event per time window, for systems that check every request, such as a Kafka
 * broker for each produce and fetch: the event of the window says how many accesses it stands for and when the first
 * and the last happened. Denied accesses pass through at once, one event each, so refusals are never delayed or
 * merged. Accesses are identical when they share everything but the moment: user, client address, resource, access
 * type, action, outcome, deciding policy and request. At most a set number of different accesses wait; beyond it the
 * oldest is sent early. Thread-safe.
 */
// Agents run in the systems they protect, not in a web container; the timer thread is the aggregator's own and close()
// stops it.
@SuppressWarnings({"PMD.DoNotUseThreads", "PMD.CloseResource"})
public final class AuditAggregator
        implements AutoCloseable
{
    /** The most different accesses that wait for their window, unless said. */
    public static final int DEFAULT_MAX_PENDING = 10_000;

    private final Duration window;
    private final Consumer<AccessEvent> sink;
    private final Clock clock;
    private final int maxPending;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final @Nullable ScheduledExecutorService timer;

    /** Identical accesses waiting for their window to end. */
    private static final class Pending
    {
        final AccessEvent first;
        int count = 1;
        Instant last;

        Pending(AccessEvent first)
        {
            this.first = first;
            this.last = first.occurredAt();
        }

        AccessEvent event()
        {
            return count == 1 ? first : first.folded(count, last);
        }
    }

    AuditAggregator(Duration window, Consumer<AccessEvent> sink, Clock clock, int maxPending, @Nullable ScheduledExecutorService timer)
    {
        if (window.isNegative()) {
            throw new IllegalArgumentException("the window must not be negative");
        }
        if (maxPending < 1) {
            throw new IllegalArgumentException("at least one access must be able to wait");
        }
        this.window = window;
        this.sink = requireNonNull(sink, "sink");
        this.clock = requireNonNull(clock, "clock");
        this.maxPending = maxPending;
        this.timer = timer;
    }

    /**
     * Starts an aggregator that sends the events of ended windows from a background thread.
     *
     * @param window how long identical allowed accesses are folded; zero sends every access at once
     * @param sink where events go, such as {@link GrantForgeAgent#record}
     * @return the aggregator; close it to send what waits
     */
    public static AuditAggregator start(Duration window, Consumer<AccessEvent> sink)
    {
        if (window.isZero()) {
            return new AuditAggregator(window, sink, Clock.systemUTC(), DEFAULT_MAX_PENDING, null);
        }
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "grantforge-audit-aggregator");
            thread.setDaemon(true);
            return thread;
        });
        AuditAggregator aggregator = new AuditAggregator(window, sink, Clock.systemUTC(), DEFAULT_MAX_PENDING, timer);
        long period = Math.max(100, window.toMillis() / 2);
        timer.scheduleWithFixedDelay(aggregator::flush, period, period, TimeUnit.MILLISECONDS);
        return aggregator;
    }

    /**
     * Records an access: a denied one, or any with a zero window, is sent at once; an allowed one waits for its window.
     *
     * @param event the access
     */
    public void record(AccessEvent event)
    {
        if (!event.allowed() || window.isZero()) {
            sink.accept(event);
            return;
        }
        AccessEvent early = null;
        lock.lock();
        try {
            String key = event.foldingKey();
            Pending waiting = pending.get(key);
            if (waiting != null) {
                waiting.count = waiting.count == Integer.MAX_VALUE ? Integer.MAX_VALUE : waiting.count + 1;
                if (event.occurredAt().isAfter(waiting.last)) {
                    waiting.last = event.occurredAt();
                }
                return;
            }
            if (pending.size() >= maxPending) {
                Iterator<Pending> oldest = pending.values().iterator();
                early = oldest.next().event();
                oldest.remove();
            }
            pending.put(key, new Pending(event));
        }
        finally {
            lock.unlock();
        }
        if (early != null) {
            sink.accept(early);
        }
    }

    /** Sends the events whose window ended. */
    public void flush()
    {
        send(false);
    }

    private void send(boolean all)
    {
        List<AccessEvent> due = new ArrayList<>();
        Instant end = clock.instant().minus(window);
        lock.lock();
        try {
            Iterator<Pending> waiting = pending.values().iterator();
            while (waiting.hasNext()) {
                Pending next = waiting.next();
                if (all || !next.first.occurredAt().isAfter(end)) {
                    due.add(next.event());
                    waiting.remove();
                }
            }
        }
        finally {
            lock.unlock();
        }
        for (AccessEvent event : due) {
            sink.accept(event);
        }
    }

    /**
     * Returns how many different accesses wait for their window.
     *
     * @return the count
     */
    public int pending()
    {
        lock.lock();
        try {
            return pending.size();
        }
        finally {
            lock.unlock();
        }
    }

    /** Stops the background thread and sends everything that waits. */
    @Override
    public void close()
    {
        ScheduledExecutorService running = timer;
        if (running != null) {
            running.shutdownNow();
        }
        send(true);
    }
}
