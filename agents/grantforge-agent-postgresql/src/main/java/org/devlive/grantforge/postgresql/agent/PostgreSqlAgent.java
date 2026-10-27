// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.sync.AgentRuntime;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A running agent: the signed-policy agent that keeps the PostgreSQL service's snapshot and ships access events, the
 * sync that keeps PostgreSQL's roles, privileges and row security policies in step with it, and the reader of the
 * server log. Each runs on a timer, on a connection of its own; a sync or read that fails is tried again at the next
 * turn.
 */
public final class PostgreSqlAgent
        implements AutoCloseable
{
    /** The oldest PostgreSQL release the agent supports, as {@code server_version_num}: the oldest it is tested with. */
    static final int MINIMUM = 150_000;

    private static final Logger LOG = Logger.getLogger(PostgreSqlAgent.class.getName());

    private final AgentRuntime runtime;
    private final Sync sync;
    private final @Nullable ServerLogAudit log;

    private PostgreSqlAgent(AgentRuntime runtime, Sync sync, @Nullable ServerLogAudit log)
    {
        this.runtime = runtime;
        this.sync = sync;
        this.log = log;
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
        AgentRuntime runtime = AgentRuntime.start(config.agent(), config.foldWindow(), "PostgreSQL", LOG, PostgreSqlFailure.class);
        runtime.closes(syncing::close);
        if (reading != null) {
            runtime.closes(reading::close);
        }
        Sync sync = new Sync(syncing, config, runtime.agent()::snapshot, Clock.systemUTC());
        ServerLogAudit log = reading == null || logSettings == null ? null : new ServerLogAudit(reading, config.connection().database(), logSettings,
                config.agent().cacheDirectory().resolve("postgresql-server-log.position"), runtime.agent()::snapshot, sync::groupsOf, sync::inventoryOf,
                runtime.audit()::record);
        long every = config.syncInterval().toMillis();
        runtime.every("sync with the policies", Duration.ZERO, config.syncInterval(), sync::run);
        if (log != null && logSettings != null) {
            runtime.every("read the server log", logSettings.interval(), logSettings.interval(), log::poll);
        }
        LOG.info("The GrantForge PostgreSQL agent keeps PostgreSQL " + version + " at " + config.connection().hosts() + " in step with its policies"
                + " every " + every + " ms" + (log == null ? "" : " and reads its server log"));
        return new PostgreSqlAgent(runtime, sync, log);
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
        return runtime.agent();
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
        runtime.close();
    }
}
