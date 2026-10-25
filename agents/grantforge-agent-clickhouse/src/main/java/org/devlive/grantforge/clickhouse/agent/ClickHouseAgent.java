// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.devlive.grantforge.agent.AuditAggregator;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A running agent: the signed-policy agent that keeps the ClickHouse service's snapshot and ships access events, the
 * sync that keeps ClickHouse's roles, grants and row policies in step with it, and the reader of the query log. Each
 * runs on a timer; a sync or read that fails is tried again at the next turn.
 */
// A standalone service process: it runs its own timer threads.
@SuppressWarnings("PMD.DoNotUseThreads")
public final class ClickHouseAgent
        implements AutoCloseable
{
    /** The oldest ClickHouse release the agent supports: the oldest it is tested with. */
    static final int[] MINIMUM = {24, 8};

    private static final Logger LOG = Logger.getLogger(ClickHouseAgent.class.getName());
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)");

    private final GrantForgeAgent agent;
    private final AuditAggregator audit;
    private final ClickHouse syncing;
    private final @Nullable ClickHouse reading;
    private final Sync sync;
    private final @Nullable QueryLogAudit log;
    private final ScheduledExecutorService timer;

    private ClickHouseAgent(GrantForgeAgent agent, AuditAggregator audit, ClickHouse syncing, @Nullable ClickHouse reading, Sync sync,
            @Nullable QueryLogAudit log, ScheduledExecutorService timer)
    {
        this.agent = agent;
        this.audit = audit;
        this.syncing = syncing;
        this.reading = reading;
        this.sync = sync;
        this.log = log;
        this.timer = timer;
    }

    /**
     * Starts the agent.
     *
     * @param config its settings
     * @return the running agent; {@link #close} it to stop
     * @throws ClickHouseFailure when ClickHouse cannot be reached or refuses the agent's user
     * @throws IllegalStateException when the server is older than the agent supports
     */
    public static ClickHouseAgent start(AgentConfig config)
    {
        return start(config, HttpClickHouse::new);
    }

    static ClickHouseAgent start(AgentConfig config, Function<AgentConfig.Connection, ClickHouse> connect)
    {
        ClickHouse syncing = connect.apply(config.connection());
        try {
            String version = syncing.query("SELECT version() AS version", Map.of()).get(0).string("version");
            String unsupported = unsupported(version);
            if (unsupported != null) {
                throw new IllegalStateException(unsupported);
            }
            AgentConfig.QueryLog logSettings = config.queryLog();
            if (logSettings == null) {
                return start(config, version, syncing, null, null);
            }
            ClickHouse reading = connect.apply(config.connection());
            try {
                return start(config, version, syncing, reading, logSettings);
            }
            catch (RuntimeException failed) {
                reading.close();
                throw failed;
            }
        }
        catch (RuntimeException failed) {
            syncing.close();
            throw failed;
        }
    }

    private static ClickHouseAgent start(AgentConfig config, String version, ClickHouse syncing, @Nullable ClickHouse reading,
            AgentConfig.@Nullable QueryLog logSettings)
    {
        GrantForgeAgent agent = GrantForgeAgent.start(config.agent(), Map.of());
        AuditAggregator audit = AuditAggregator.start(config.foldWindow(), agent::record);
        Sync sync = new Sync(syncing, config, agent::snapshot, Clock.systemUTC());
        QueryLogAudit log = reading == null || logSettings == null ? null : new QueryLogAudit(reading, logSettings,
                config.agent().cacheDirectory().resolve("clickhouse-query-log.position"), agent::snapshot, sync::groupsOf, sync::columnsOf,
                audit::record);
        AtomicInteger thread = new AtomicInteger();
        ScheduledExecutorService timer = Executors.newScheduledThreadPool(2, task -> {
            Thread worker = new Thread(task, "grantforge-clickhouse-" + thread.incrementAndGet());
            worker.setDaemon(true);
            return worker;
        });
        long every = config.syncInterval().toMillis();
        timer.scheduleWithFixedDelay(() -> guarded("sync with the policies", sync::run), 0, every, TimeUnit.MILLISECONDS);
        if (log != null && logSettings != null) {
            long interval = logSettings.interval().toMillis();
            timer.scheduleWithFixedDelay(() -> guarded("read the query log", log::poll), interval, interval, TimeUnit.MILLISECONDS);
        }
        LOG.info("The GrantForge ClickHouse agent keeps ClickHouse " + version + " at " + config.connection().urls() + " in step with its policies"
                + " every " + every + " ms" + (log == null ? "" : " and reads its query log"));
        return new ClickHouseAgent(agent, audit, syncing, reading, sync, log, timer);
    }

    /**
     * Says why a server cannot be kept by the agent.
     *
     * @param version what {@code version()} returned
     * @return the reason, or {@code null} for a supported release
     */
    static @Nullable String unsupported(String version)
    {
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.lookingAt()) {
            return "the ClickHouse version cannot be read: " + version;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        if (major < MINIMUM[0] || major == MINIMUM[0] && minor < MINIMUM[1]) {
            return "the server is ClickHouse " + version + "; the GrantForge agent needs ClickHouse " + MINIMUM[0] + "." + MINIMUM[1] + " or later";
        }
        return null;
    }

    // A timer task that throws stops running; each turn logs its failure and the next turn tries again.
    private static void guarded(String what, Runnable task)
    {
        try {
            task.run();
        }
        catch (ClickHouseFailure failed) {
            LOG.warning("The GrantForge ClickHouse agent could not " + what + ": " + failed.getMessage());
        }
        catch (RuntimeException broken) {
            LOG.log(Level.SEVERE, "The GrantForge ClickHouse agent could not " + what, broken);
        }
    }

    /**
     * Brings ClickHouse in step now, rather than at the next turn.
     *
     * @return what changed, or {@code null} without a snapshot
     */
    Reconciler.@Nullable Outcome syncNow()
    {
        return sync.run();
    }

    /**
     * Reads the query log now, rather than at the next turn.
     *
     * @return how many entries were read
     */
    int readLogNow()
    {
        return log == null ? 0 : log.poll();
    }

    /**
     * Returns the signed-policy agent.
     *
     * @return it
     */
    GrantForgeAgent agent()
    {
        return agent;
    }

    /** Stops the timers, sends or spools the events and lets go of ClickHouse; ClickHouse keeps what it was given. */
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
        syncing.close();
        if (reading != null) {
            reading.close();
        }
    }
}
