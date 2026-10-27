// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.sync.AgentRuntime;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A running agent: the signed-policy agent that keeps the ClickHouse service's snapshot and ships access events, the
 * sync that keeps ClickHouse's roles, grants and row policies in step with it, and the reader of the query log. Each
 * runs on a timer; a sync or read that fails is tried again at the next turn.
 */
public final class ClickHouseAgent
        implements AutoCloseable
{
    /** The oldest ClickHouse release the agent supports: the oldest it is tested with. */
    static final int[] MINIMUM = {24, 8};

    private static final Logger LOG = Logger.getLogger(ClickHouseAgent.class.getName());
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)");

    private final AgentRuntime runtime;
    private final Sync sync;
    private final @Nullable QueryLogAudit log;

    private ClickHouseAgent(AgentRuntime runtime, Sync sync, @Nullable QueryLogAudit log)
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
        AgentRuntime runtime = AgentRuntime.start(config.agent(), config.foldWindow(), "ClickHouse", LOG, ClickHouseFailure.class);
        runtime.closes(syncing::close);
        if (reading != null) {
            runtime.closes(reading::close);
        }
        Sync sync = new Sync(syncing, config, runtime.agent()::snapshot, Clock.systemUTC());
        QueryLogAudit log = reading == null || logSettings == null ? null : new QueryLogAudit(reading, logSettings,
                config.agent().cacheDirectory().resolve("clickhouse-query-log.position"), runtime.agent()::snapshot, sync::groupsOf, sync::columnsOf,
                runtime.audit()::record);
        long every = config.syncInterval().toMillis();
        runtime.every("sync with the policies", Duration.ZERO, config.syncInterval(), sync::run);
        if (log != null && logSettings != null) {
            runtime.every("read the query log", logSettings.interval(), logSettings.interval(), log::poll);
        }
        LOG.info("The GrantForge ClickHouse agent keeps ClickHouse " + version + " at " + config.connection().urls() + " in step with its policies"
                + " every " + every + " ms" + (log == null ? "" : " and reads its query log"));
        return new ClickHouseAgent(runtime, sync, log);
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
        return runtime.agent();
    }

    /** Stops the timers, sends or spools the events and lets go of ClickHouse; ClickHouse keeps what it was given. */
    @Override
    public void close()
    {
        runtime.close();
    }
}
