// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.AuditAggregator;
import org.devlive.grantforge.agent.GrantForgeAgent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * What runs while an agent runs: the GrantForge agent that keeps the service's snapshot and ships access events, the
 * aggregator that folds identical accesses into one event, and the timers of the sync and of the audit reading. A turn
 * that fails is logged and the next turn tries again: a failure of the system, which the agent expects when the system
 * is away, in one line, anything else with its stack. Closing stops the timers, then the audit, then the GrantForge
 * agent, then what the agent registered, such as its connections; the system keeps what it was given.
 */
// A standalone service process: it runs its own timer threads.
@SuppressWarnings("PMD.DoNotUseThreads")
public final class AgentRuntime
        implements AutoCloseable
{
    private final GrantForgeAgent agent;
    private final AuditAggregator audit;
    private final ScheduledExecutorService timer;
    private final String name;
    // The name, not the logger: the agent's own static field holds the logger, which LogManager returns by name.
    private final String log;
    private final Class<? extends RuntimeException> expected;
    private final List<Runnable> closers = new ArrayList<>();

    private AgentRuntime(GrantForgeAgent agent, AuditAggregator audit, ScheduledExecutorService timer, String name, Logger log,
            Class<? extends RuntimeException> expected)
    {
        this.agent = agent;
        this.audit = audit;
        this.timer = timer;
        this.name = name;
        this.log = log.getName();
        this.expected = expected;
    }

    /**
     * Starts the GrantForge agent, the audit and two timer threads.
     *
     * @param settings the GrantForge agent's settings
     * @param foldWindow the window identical allowed accesses fold within
     * @param name the protected system's name, such as {@code ClickHouse}, for the log and the threads
     * @param log the agent's logger
     * @param expected the failure the agent's system throws when it fails or is away
     * @return the runtime; {@link #close} it to stop
     */
    public static AgentRuntime start(AgentSettings settings, Duration foldWindow, String name, Logger log,
            Class<? extends RuntimeException> expected)
    {
        GrantForgeAgent agent = GrantForgeAgent.start(settings, Map.of());
        AuditAggregator audit = AuditAggregator.start(foldWindow, agent::record);
        String prefix = "grantforge-" + name.toLowerCase(Locale.ROOT) + "-";
        AtomicInteger thread = new AtomicInteger();
        ScheduledExecutorService timer = Executors.newScheduledThreadPool(2, task -> {
            Thread worker = new Thread(task, prefix + thread.incrementAndGet());
            worker.setDaemon(true);
            return worker;
        });
        return new AgentRuntime(agent, audit, timer, name, log, expected);
    }

    /**
     * Returns the GrantForge agent, which holds the snapshot and records events.
     *
     * @return it
     */
    public GrantForgeAgent agent()
    {
        return agent;
    }

    /**
     * Returns the audit, which folds identical allowed accesses before the GrantForge agent ships them.
     *
     * @return it
     */
    public AuditAggregator audit()
    {
        return audit;
    }

    /**
     * Runs a task at a fixed delay between the end of one turn and the start of the next.
     *
     * @param what what the task does, completing "could not ...", such as {@code sync with the policies}
     * @param first when the first turn starts
     * @param delay the time between turns
     * @param task the task
     */
    public void every(String what, Duration first, Duration delay, Runnable task)
    {
        timer.scheduleWithFixedDelay(() -> guarded(what, task), first.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Registers something to let go of when the agent stops, after the GrantForge agent; in the order registered.
     *
     * @param closer what lets go of it, such as a connection's close
     */
    public void closes(Runnable closer)
    {
        closers.add(closer);
    }

    // A timer task that throws stops running; each turn logs its failure and the next turn tries again.
    void guarded(String what, Runnable task)
    {
        try {
            task.run();
        }
        catch (RuntimeException failed) {
            Logger logger = Logger.getLogger(log);
            if (expected.isInstance(failed)) {
                logger.warning("The GrantForge " + name + " agent could not " + what + ": " + failed.getMessage());
            }
            else {
                logger.log(Level.SEVERE, "The GrantForge " + name + " agent could not " + what, failed);
            }
        }
    }

    /** Stops the timers, sends or spools the events and lets go of what was registered. */
    @Override
    public void close()
    {
        timer.shutdown();
        try {
            if (!timer.awaitTermination(10, TimeUnit.SECONDS)) {
                timer.shutdownNow();
            }
        }
        catch (InterruptedException interrupted) {
            timer.shutdownNow();
            Thread.currentThread().interrupt();
        }
        audit.close();
        agent.close();
        closers.forEach(Runnable::run);
    }
}
