// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

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
 * A running agent: the signed-policy agent that keeps the Elasticsearch service's snapshot and ships access events, the
 * sync that keeps Elasticsearch's roles, role mappings and native users' roles in step with it, and the reader of the
 * audit log files. Each runs on a timer; a sync or read that fails is tried again at the next turn.
 */
public final class ElasticsearchAgent
        implements AutoCloseable
{
    /** The oldest Elasticsearch release the agent supports, as major and minor: the oldest it is tested with. */
    static final int[] MINIMUM = {8, 19};

    private static final Logger LOG = Logger.getLogger(ElasticsearchAgent.class.getName());
    private static final Pattern RELEASE = Pattern.compile("(\\d+)\\.(\\d+).*");

    private final AgentRuntime runtime;
    private final Sync sync;
    private final @Nullable AuditLog log;

    private ElasticsearchAgent(AgentRuntime runtime, Sync sync, @Nullable AuditLog log)
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
     * @throws ElasticsearchFailure when Elasticsearch cannot be reached or refuses the agent's user
     * @throws IllegalStateException when the cluster is older than the agent supports or runs without security
     */
    public static ElasticsearchAgent start(AgentConfig config)
    {
        return start(config, RestElasticsearch::new);
    }

    static ElasticsearchAgent start(AgentConfig config, Function<AgentConfig.Connection, Elasticsearch> connect)
    {
        Elasticsearch syncing = connect.apply(config.connection());
        try {
            JsonNode root = syncing.get("/", Map.of());
            String unsupported = unsupported(root.path("version").path("distribution").asText(""), root.path("version").path("number").asText(""));
            if (unsupported != null) {
                throw new IllegalStateException(unsupported);
            }
            if (!syncing.get("/_xpack", Map.of()).path("features").path("security").path("enabled").asBoolean(true)) {
                throw new IllegalStateException("the cluster runs without security, which the GrantForge agent enforces policies with");
            }
            String version = root.path("version").path("number").asText();
            String user = syncing.get("/_security/_authenticate", Map.of()).path("username").asText("");
            return start(config, version, user, syncing);
        }
        catch (RuntimeException failed) {
            syncing.close();
            throw failed;
        }
    }

    private static ElasticsearchAgent start(AgentConfig config, String version, String user, Elasticsearch syncing)
    {
        AgentRuntime runtime = AgentRuntime.start(config.agent(), config.foldWindow(), "Elasticsearch", LOG, ElasticsearchFailure.class);
        runtime.closes(syncing::close);
        Sync sync = new Sync(syncing, config, runtime.agent()::snapshot, Clock.systemUTC());
        AgentConfig.AuditFiles files = config.auditLog();
        AuditLog log = files == null ? null : new AuditLog(files, user, config.agent().cacheDirectory().resolve("elasticsearch-audit-log.position"),
                runtime.agent()::snapshot, sync::groupsOf, sync::inventory, runtime.audit()::record);
        long every = config.syncInterval().toMillis();
        runtime.every("sync with the policies", Duration.ZERO, config.syncInterval(), sync::run);
        if (log != null && files != null) {
            runtime.every("read the audit log", files.interval(), files.interval(), log::poll);
        }
        LOG.info("The GrantForge Elasticsearch agent keeps Elasticsearch " + version + " at " + config.connection().hosts() + " in step with its policies"
                + " every " + every + " ms as " + user + (files == null ? "" : " and reads the audit log files " + files.files()));
        return new ElasticsearchAgent(runtime, sync, log);
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
        if ("opensearch".equals(distribution)) {
            return "the cluster is an OpenSearch cluster, which the GrantForge OpenSearch agent keeps; this agent keeps Elasticsearch 8.19 or later";
        }
        Matcher release = RELEASE.matcher(number);
        if (!release.matches()) {
            return "Elasticsearch names its release " + number + ", which the GrantForge agent cannot read";
        }
        int major = Integer.parseInt(release.group(1));
        int minor = Integer.parseInt(release.group(2));
        if (major < MINIMUM[0] || major == MINIMUM[0] && minor < MINIMUM[1]) {
            return "the cluster is Elasticsearch " + number + "; the GrantForge agent needs Elasticsearch 8.19 or later";
        }
        return null;
    }

    /**
     * Brings Elasticsearch in step now, rather than at the next turn.
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

    /** Stops the timers, sends or spools the events and lets go of Elasticsearch; Elasticsearch keeps what it was given. */
    @Override
    public void close()
    {
        runtime.close();
    }
}
