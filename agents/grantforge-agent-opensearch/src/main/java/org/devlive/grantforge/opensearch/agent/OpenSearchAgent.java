// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
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
 * A running agent: the signed-policy agent that keeps the OpenSearch service's snapshot and ships access events, the
 * sync that keeps the security plugin's roles and role mappings in step with it, and the reader of the audit log. Each
 * runs on a timer, on a client of its own; a sync or read that fails is tried again at the next turn.
 */
public final class OpenSearchAgent
        implements AutoCloseable
{
    /** The oldest OpenSearch release the agent supports, as major and minor: the oldest it is tested with. */
    static final int[] MINIMUM = {2, 19};

    private static final Logger LOG = Logger.getLogger(OpenSearchAgent.class.getName());
    private static final Pattern RELEASE = Pattern.compile("(\\d+)\\.(\\d+).*");

    private final AgentRuntime runtime;
    private final Sync sync;
    private final @Nullable AuditLog log;

    private OpenSearchAgent(AgentRuntime runtime, Sync sync, @Nullable AuditLog log)
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
     * @throws OpenSearchFailure when OpenSearch cannot be reached or refuses the agent's user
     * @throws IllegalStateException when the cluster is older than the agent supports or has no security plugin
     */
    public static OpenSearchAgent start(AgentConfig config)
    {
        return start(config, RestOpenSearch::new);
    }

    static OpenSearchAgent start(AgentConfig config, Function<AgentConfig.Connection, OpenSearch> connect)
    {
        OpenSearch syncing = connect.apply(config.connection());
        try {
            JsonNode root = syncing.get("/", Map.of());
            String unsupported = unsupported(root.path("version").path("distribution").asText(""), root.path("version").path("number").asText(""));
            if (unsupported != null) {
                throw new IllegalStateException(unsupported);
            }
            JsonNode self;
            try {
                self = syncing.get("/_plugins/_security/authinfo", Map.of());
            }
            catch (OpenSearchFailure missing) {
                if (missing.status() == 400 || missing.status() == 404) {
                    throw new IllegalStateException("the cluster runs without the security plugin, which the GrantForge agent enforces policies with",
                            missing);
                }
                throw missing;
            }
            String version = root.path("version").path("number").asText();
            String user = self.path("user_name").asText("");
            AgentConfig.AuditLogSettings logSettings = config.auditLog();
            if (logSettings == null) {
                return start(config, version, user, syncing, null, null);
            }
            OpenSearch reading = connect.apply(config.connection());
            try {
                return start(config, version, user, syncing, reading, logSettings);
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

    private static OpenSearchAgent start(AgentConfig config, String version, String user, OpenSearch syncing, @Nullable OpenSearch reading,
            AgentConfig.@Nullable AuditLogSettings logSettings)
    {
        AgentRuntime runtime = AgentRuntime.start(config.agent(), config.foldWindow(), "OpenSearch", LOG, OpenSearchFailure.class);
        runtime.closes(syncing::close);
        if (reading != null) {
            runtime.closes(reading::close);
        }
        Sync sync = new Sync(syncing, config, runtime.agent()::snapshot, Clock.systemUTC());
        AuditLog log = reading == null || logSettings == null ? null : new AuditLog(reading, logSettings, user,
                config.agent().cacheDirectory().resolve("opensearch-audit-log.position"), runtime.agent()::snapshot, sync::groupsOf, sync::inventory,
                runtime.audit()::record, Clock.systemUTC());
        long every = config.syncInterval().toMillis();
        runtime.every("sync with the policies", Duration.ZERO, config.syncInterval(), sync::run);
        if (log != null && logSettings != null) {
            runtime.every("read the audit log", logSettings.interval(), logSettings.interval(), log::poll);
        }
        LOG.info("The GrantForge OpenSearch agent keeps OpenSearch " + version + " at " + config.connection().hosts() + " in step with its policies"
                + " every " + every + " ms as " + user + (log == null ? "" : " and reads its audit log"));
        return new OpenSearchAgent(runtime, sync, log);
    }

    /**
     * Says why a cluster cannot be kept by the agent.
     *
     * @param distribution what the cluster names its distribution
     * @param number its release
     * @return the reason, or {@code null} for a supported release
     */
    static @Nullable String unsupported(String distribution, String number)
    {
        if (!"opensearch".equals(distribution)) {
            return "the cluster is no OpenSearch cluster; the GrantForge agent keeps OpenSearch 2.19 or later";
        }
        Matcher release = RELEASE.matcher(number);
        if (!release.matches()) {
            return "OpenSearch names its release " + number + ", which the GrantForge agent cannot read";
        }
        int major = Integer.parseInt(release.group(1));
        int minor = Integer.parseInt(release.group(2));
        if (major < MINIMUM[0] || major == MINIMUM[0] && minor < MINIMUM[1]) {
            return "the cluster is OpenSearch " + number + "; the GrantForge agent needs OpenSearch 2.19 or later";
        }
        return null;
    }

    /**
     * Brings OpenSearch in step now, rather than at the next turn.
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
     * @return how many documents were read
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

    /** Stops the timers, sends or spools the events and lets go of OpenSearch; OpenSearch keeps what it was given. */
    @Override
    public void close()
    {
        runtime.close();
    }
}
