// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

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

/**
 * A running agent: the signed-policy agent that keeps the PostgreSQL service's snapshot and ships access events, the
 * sync that keeps PostgreSQL's roles, privileges and row security policies in step with it, and the reader of the
 * server log. Each runs on a timer, on a connection of its own; a sync or read that fails is tried again at the next
 * turn.
 */
// A standalone service process: it runs its own timer threads.
@SuppressWarnings("PMD.DoNotUseThreads")
public final class PostgreSqlAgent
        implements AutoCloseable
{
    /** The oldest PostgreSQL release the agent supports, as {@code server_version_num}: the oldest it is tested with. */
    static final int MINIMUM = 150_000;

    private static final Logger LOG = Logger.getLogger(PostgreSqlAgent.class.getName());

    private final GrantForgeAgent agent;
    private final AuditAggregator audit;
    private final PostgreSql syncing;
    private final @Nullable PostgreSql reading;
    private final Sync sync;
    private final @Nullable ServerLogAudit log;
    private final ScheduledExecutorService timer;

    private PostgreSqlAgent(GrantForgeAgent agent, AuditAggregator audit, PostgreSql syncing, @Nullable PostgreSql reading, Sync sync,
            @Nullable ServerLogAudit log, ScheduledExecutorService timer)
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
     * @throws PostgreSqlFailure when PostgreSQL cannot be reached or refuses the agent's user
     * @throws IllegalStateException when the server is older than the agent supports
     */
    public static PostgreSqlAgent start(AgentConfig config)
    {
        return start(config, JdbcPostgreSql::new);
    }

    static PostgreSqlAgent start(AgentConfig config, Function<AgentConfig.Connection, PostgreSql> connect)
    {
        PostgreSql syncing = connect.apply(config.connection());
        try {
            Row server = syncing.query(config.connection().database(), "SELECT current_setting('server_version') AS version,"
                    + " current_setting('server_version_num')::int AS number, (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) AS superuser,"
                    + " current_user AS self").get(0);
            String unsupported = unsupported((int) server.number("number"), server.string("version"));
            if (unsupported != null) {
                throw new IllegalStateException(unsupported);
            }
            if (!server.flag("superuser")) {
                LOG.warning("The GrantForge agent's PostgreSQL user " + server.string("self") + " is no superuser; it needs to own or be a member of"
                        + " the owners of the tables it keeps, to manage roles and to read the server log, and logs what PostgreSQL refuses it");
            }
            AgentConfig.ServerLog logSettings = config.serverLog();
            if (logSettings == null) {
                return start(config, server.string("version"), syncing, null, null);
            }
            PostgreSql reading = connect.apply(config.connection());
            try {
                return start(config, server.string("version"), syncing, reading, logSettings);
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

    private static PostgreSqlAgent start(AgentConfig config, String version, PostgreSql syncing, @Nullable PostgreSql reading,
            AgentConfig.@Nullable ServerLog logSettings)
    {
        GrantForgeAgent agent = GrantForgeAgent.start(config.agent(), Map.of());
        AuditAggregator audit = AuditAggregator.start(config.foldWindow(), agent::record);
        Sync sync = new Sync(syncing, config, agent::snapshot, Clock.systemUTC());
        ServerLogAudit log = reading == null || logSettings == null ? null : new ServerLogAudit(reading, config.connection().database(), logSettings,
                config.agent().cacheDirectory().resolve("postgresql-server-log.position"), agent::snapshot, sync::groupsOf, sync::inventoryOf,
                audit::record);
        AtomicInteger thread = new AtomicInteger();
        ScheduledExecutorService timer = Executors.newScheduledThreadPool(2, task -> {
            Thread worker = new Thread(task, "grantforge-postgresql-" + thread.incrementAndGet());
            worker.setDaemon(true);
            return worker;
        });
        long every = config.syncInterval().toMillis();
        timer.scheduleWithFixedDelay(() -> guarded("sync with the policies", sync::run), 0, every, TimeUnit.MILLISECONDS);
        if (log != null && logSettings != null) {
            long interval = logSettings.interval().toMillis();
            timer.scheduleWithFixedDelay(() -> guarded("read the server log", log::poll), interval, interval, TimeUnit.MILLISECONDS);
        }
        LOG.info("The GrantForge PostgreSQL agent keeps PostgreSQL " + version + " at " + config.connection().hosts() + " in step with its policies"
                + " every " + every + " ms" + (log == null ? "" : " and reads its server log"));
        return new PostgreSqlAgent(agent, audit, syncing, reading, sync, log, timer);
    }

    /**
     * Says why a server cannot be kept by the agent.
     *
     * @param number what {@code server_version_num} returned
     * @param version what {@code server_version} returned
     * @return the reason, or {@code null} for a supported release
     */
    static @Nullable String unsupported(int number, String version)
    {
        if (number < MINIMUM) {
            return "the server is PostgreSQL " + version + "; the GrantForge agent needs PostgreSQL 15 or later";
        }
        return null;
    }

    // A timer task that throws stops running; each turn logs its failure and the next turn tries again.
    private static void guarded(String what, Runnable task)
    {
        try {
            task.run();
        }
        catch (PostgreSqlFailure failed) {
            LOG.warning("The GrantForge PostgreSQL agent could not " + what + ": " + failed.getMessage());
        }
        catch (RuntimeException broken) {
            LOG.log(Level.SEVERE, "The GrantForge PostgreSQL agent could not " + what, broken);
        }
    }

    /**
     * Brings PostgreSQL in step now, rather than at the next turn.
     *
     * @return what changed, or {@code null} without a snapshot
     */
    Reconciler.@Nullable Outcome syncNow()
    {
        return sync.run();
    }

    /**
     * Reads the server log now, rather than at the next turn.
     *
     * @return how many lines were read
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

    /**
     * Returns the role the agent keeps for a user.
     *
     * @param user the user
     * @return the role's name
     */
    String role(String user)
    {
        return sync.role(user);
    }

    /** Stops the timers, sends or spools the events and lets go of PostgreSQL; PostgreSQL keeps what it was given. */
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
