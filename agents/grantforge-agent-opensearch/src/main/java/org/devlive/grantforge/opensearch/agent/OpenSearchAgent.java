// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
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
 * A running agent: the signed-policy agent that keeps the OpenSearch service's snapshot and ships access events, the
 * sync that keeps the security plugin's roles and role mappings in step with it, and the reader of the audit log. Each
 * runs on a timer, on a client of its own; a sync or read that fails is tried again at the next turn.
 */
@SuppressWarnings("PMD.DoNotUseThreads")
public final class OpenSearchAgent
        implements AutoCloseable
{
    /** The oldest OpenSearch release the agent supports, as major and minor: the oldest it is tested with. */
    static final int[] MINIMUM = {2, 19};

    private static final Logger LOG = Logger.getLogger(OpenSearchAgent.class.getName());
    private static final Pattern RELEASE = Pattern.compile("(\\d+)\\.(\\d+).*");

    private final GrantForgeAgent agent;
    private final AuditAggregator audit;
    private final OpenSearch syncing;
    private final @Nullable OpenSearch reading;
    private final Sync sync;
    private final @Nullable AuditLog log;
    private final ScheduledExecutorService timer;

    private OpenSearchAgent(GrantForgeAgent agent, AuditAggregator audit, OpenSearch syncing, @Nullable OpenSearch reading, Sync sync,
            @Nullable AuditLog log, ScheduledExecutorService timer)
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
        GrantForgeAgent agent = GrantForgeAgent.start(config.agent(), Map.of());
        AuditAggregator audit = AuditAggregator.start(config.foldWindow(), agent::record);
        Sync sync = new Sync(syncing, config, agent::snapshot, Clock.systemUTC());
        AuditLog log = reading == null || logSettings == null ? null : new AuditLog(reading, logSettings, user,
                config.agent().cacheDirectory().resolve("opensearch-audit-log.position"), agent::snapshot, sync::groupsOf, sync::inventory,
                audit::record, Clock.systemUTC());
        AtomicInteger thread = new AtomicInteger();
        ScheduledExecutorService timer = Executors.newScheduledThreadPool(2, task -> {
            Thread worker = new Thread(task, "grantforge-opensearch-" + thread.incrementAndGet());
            worker.setDaemon(true);
            return worker;
        });
        long every = config.syncInterval().toMillis();
        timer.scheduleWithFixedDelay(() -> guarded("sync with the policies", sync::run), 0, every, TimeUnit.MILLISECONDS);
        if (log != null && logSettings != null) {
            long interval = logSettings.interval().toMillis();
            timer.scheduleWithFixedDelay(() -> guarded("read the audit log", log::poll), interval, interval, TimeUnit.MILLISECONDS);
        }
        LOG.info("The GrantForge OpenSearch agent keeps OpenSearch " + version + " at " + config.connection().hosts() + " in step with its policies"
                + " every " + every + " ms as " + user + (log == null ? "" : " and reads its audit log"));
        return new OpenSearchAgent(agent, audit, syncing, reading, sync, log, timer);
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

    // A timer task that throws stops running; each turn logs its failure and the next turn tries again.
    private static void guarded(String what, Runnable task)
    {
        try {
            task.run();
        }
        catch (OpenSearchFailure failed) {
            LOG.warning("The GrantForge OpenSearch agent could not " + what + ": " + failed.getMessage());
        }
        catch (RuntimeException broken) {
            LOG.log(Level.SEVERE, "The GrantForge OpenSearch agent could not " + what, broken);
        }
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

    /** Stops the timers, sends or spools the events and lets go of OpenSearch; OpenSearch keeps what it was given. */
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
