// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.sync.AgentRuntime;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A running agent: the signed-policy agent that keeps the Doris service's snapshot and ships access events, the sync that
 * keeps Doris's roles, grants and row policies in step with it, and the reader of the audit log. Each runs on a timer;
 * a sync or read that fails is tried again at the next turn.
 */
public final class DorisAgent
        implements AutoCloseable
{
    /** The oldest Doris release the agent supports: the oldest it is tested with. */
    static final int[] MINIMUM = {2, 1};

    private static final Logger LOG = Logger.getLogger(DorisAgent.class.getName());
    private static final Pattern VERSION = Pattern.compile("doris-(\\d+)\\.(\\d+)(?:\\.(\\d+))*");

    private final AgentRuntime runtime;
    private final Sync sync;
    private final @Nullable AuditLog log;

    private DorisAgent(AgentRuntime runtime, Sync sync, @Nullable AuditLog log)
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
     * @throws DorisFailure when no frontend can be reached or Doris refuses the agent's user
     * @throws IllegalStateException when the cluster is older than the agent supports
     */
    public static DorisAgent start(AgentConfig config)
    {
        return start(config, JdbcDoris::new);
    }

    static DorisAgent start(AgentConfig config, Function<AgentConfig.Connection, Doris> connect)
    {
        Doris syncing = connect.apply(config.connection());
        try {
            List<Row> rows = syncing.query("SHOW VARIABLES LIKE 'version_comment'");
            String version = rows.isEmpty() ? "" : rows.get(0).string("Value");
            String unsupported = unsupported(version);
            if (unsupported != null) {
                throw new IllegalStateException(unsupported);
            }
            AgentConfig.AuditLog logSettings = config.auditLog();
            if (logSettings == null) {
                return start(config, release(version), syncing, null, null);
            }
            Doris reading = connect.apply(config.connection());
            try {
                return start(config, release(version), syncing, reading, logSettings);
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

    private static DorisAgent start(AgentConfig config, String version, Doris syncing, @Nullable Doris reading,
            AgentConfig.@Nullable AuditLog logSettings)
    {
        AgentRuntime runtime = AgentRuntime.start(config.agent(), config.foldWindow(), "Doris", LOG, DorisFailure.class);
        runtime.closes(syncing::close);
        if (reading != null) {
            runtime.closes(reading::close);
        }
        Sync sync = new Sync(syncing, config, runtime.agent()::snapshot, Clock.systemUTC());
        AuditLog log = reading == null || logSettings == null ? null : new AuditLog(reading, logSettings,
                config.agent().cacheDirectory().resolve("doris-audit-log.position"), runtime.agent()::snapshot, sync::groupsOf, sync::columnsOf,
                runtime.audit()::record);
        runtime.every("sync with the policies", Duration.ZERO, config.syncInterval(), sync::run);
        if (log != null && logSettings != null) {
            runtime.every("read the audit log", logSettings.interval(), logSettings.interval(), log::poll);
        }
        LOG.info("The GrantForge Doris agent keeps Apache Doris " + version + " at " + config.connection().hosts() + " in step with its policies every "
                + config.syncInterval().toMillis() + " ms" + (log == null ? "" : " and reads its audit log"));
        return new DorisAgent(runtime, sync, log);
    }

    /**
     * Says why a cluster cannot be kept by the agent.
     *
     * @param version the variable {@code version_comment}, such as {@code doris version doris-4.1.5-release-917b20be838}
     * @return the reason, or {@code null} for a supported release
     */
    static @Nullable String unsupported(String version)
    {
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.find()) {
            return "the Doris version cannot be read: " + version;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        if (major < MINIMUM[0] || major == MINIMUM[0] && minor < MINIMUM[1]) {
            return "the cluster is Apache Doris " + release(version) + "; the GrantForge agent needs Apache Doris " + MINIMUM[0] + "." + MINIMUM[1]
                    + " or later";
        }
        return null;
    }

    /** The release in the version comment, such as {@code 4.1.5}. */
    static String release(String version)
    {
        Matcher matcher = Pattern.compile("doris-(\\d+(?:\\.\\d+)+)").matcher(version);
        return matcher.find() ? matcher.group(1) : version;
    }

    /**
     * Brings Doris in step now, rather than at the next turn.
     *
     * @return what changed, or {@code null} without a snapshot
     */
    Reconciler.@Nullable Outcome syncNow()
    {
        return sync.run();
    }

    /**
     * Reads the audit log now, rather than at the next turn.
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

    /** Stops the timers, sends or spools the events and lets go of Doris; Doris keeps what it was given. */
    @Override
    public void close()
    {
        runtime.close();
    }
}
