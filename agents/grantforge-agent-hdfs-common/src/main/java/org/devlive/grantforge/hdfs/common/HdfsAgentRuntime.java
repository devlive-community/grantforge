// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.SigningKey;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns the Hadoop-independent agent lifecycle, settings, captured policy sessions and optional monitoring hooks. */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public final class HdfsAgentRuntime
        implements AutoCloseable
{
    private static final Logger LOG = Logger.getLogger(HdfsAgentRuntime.class.getName());
    private static final String PREFIX = "grantforge.hdfs.";
    private final ReentrantLock lifecycle = new ReentrantLock();
    private final Function<AgentSettings, GrantForgeAgent> factory;
    private volatile @Nullable GrantForgeAgent agent;
    private volatile @Nullable AgentSettings settings;
    private volatile boolean nativeFallback;
    private volatile HdfsMetrics metrics = HdfsMetrics.NONE;

    /** Creates a runtime that uses the real signed-policy agent and its background audit shipper. */
    public HdfsAgentRuntime()
    {
        this(configuration -> GrantForgeAgent.start(configuration, Collections.emptyMap()));
    }

    HdfsAgentRuntime(Function<AgentSettings, GrantForgeAgent> factory)
    {
        this.factory = factory;
    }

    /**
     * Starts once with a copy of the adapter's resolved configuration; subsequent starts are idempotent until stopped.
     *
     * @param configuration resolved Hadoop configuration values, including the native permission and bypass flags
     * @param agentVersion the adapter's build-filtered version label
     * @throws IOException if the token or pinned signing-key file cannot be read
     * @throws IllegalArgumentException if settings are missing, invalid or disable native enforcement callbacks
     */
    public void start(Map<String, String> configuration, String agentVersion) throws IOException
    {
        lifecycle.lock();
        try {
            if (agent != null) {
                return;
            }
            Map<String, String> values = new HashMap<>(configuration);
            if (!flag(values, "dfs.permissions.enabled", true)) {
                throw new IllegalArgumentException("dfs.permissions.enabled must be true for the NameNode to call the agent");
            }
            String bypass = values.get("dfs.namenode.inode.attributes.provider.bypass.users");
            if (bypass != null && !HdfsAuthorizationContext.blank(bypass)) {
                throw new IllegalArgumentException("dfs.namenode.inode.attributes.provider.bypass.users must be empty for enforcement");
            }
            if (HdfsAuthorizationContext.blank(agentVersion) || agentVersion.length() > 64) {
                throw new IllegalArgumentException("the agent version must be 1 to 64 characters");
            }
            AgentSettings parsed = parse(values, agentVersion);
            nativeFallback = flag(values, PREFIX + "native.fallback", false);
            agent = factory.apply(parsed);
            settings = parsed;
        }
        finally {
            lifecycle.unlock();
        }
    }

    private static AgentSettings parse(Map<String, String> values, String version) throws IOException
    {
        String token = read(required(values, PREFIX + "token.file"), StandardCharsets.UTF_8).trim();
        int batch = (int) number(values, "audit.batch.size", 500, 1, AgentSettings.MAX_BATCH);
        int queue = (int) number(values, "audit.queue.capacity", 10000, 1, 1_000_000);
        if (queue < batch) {
            throw new IllegalArgumentException(PREFIX + "audit.queue.capacity must be at least " + PREFIX + "audit.batch.size");
        }
        AgentSettings.Builder builder = AgentSettings.builder().server(URI.create(required(values, PREFIX + "server.url")))
                .token(token).instance(required(values, PREFIX + "instance"))
                .cacheDirectory(Paths.get(required(values, PREFIX + "cache.dir"))).agentVersion(version)
                .timeouts(duration(values, "connect.timeout.ms", 5000, false), duration(values, "read.timeout.ms", 8000, false))
                .refreshInterval(duration(values, "refresh.interval.ms", 30000, true))
                .audit(batch, Duration.ofMillis(number(values, "audit.flush.interval.ms", 5000, 1, Integer.MAX_VALUE)), queue)
                .spoolLimitBytes(number(values, "audit.spool.limit.bytes", 64L * 1024 * 1024, 0, Long.MAX_VALUE));
        String key = values.get(PREFIX + "signing.key.file");
        if (key != null && !HdfsAuthorizationContext.blank(key)) {
            builder.trustedKey(SigningKey.of(read(key.trim(), StandardCharsets.US_ASCII)));
        }
        return builder.build();
    }

    private static String read(String path, java.nio.charset.Charset charset) throws IOException
    {
        return new String(Files.readAllBytes(Paths.get(path)), charset);
    }

    private static String required(Map<String, String> values, String key)
    {
        String value = values.get(key);
        if (value == null || HdfsAuthorizationContext.blank(value)) {
            throw new IllegalArgumentException("missing NameNode setting " + key);
        }
        return value.trim();
    }

    private static boolean flag(Map<String, String> values, String key, boolean fallback)
    {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(trimmed);
    }

    private static Duration duration(Map<String, String> values, String key, long fallback, boolean refresh)
    {
        long minimum = refresh ? 1000 : 1;
        return Duration.ofMillis(number(values, key, fallback, minimum, refresh ? Long.MAX_VALUE : Integer.MAX_VALUE));
    }

    private static long number(Map<String, String> values, String key, long fallback, long minimum, long maximum)
    {
        String configured = values.get(PREFIX + key);
        long value;
        try {
            value = configured == null ? fallback : Long.parseLong(configured.trim());
        }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(PREFIX + key + " must be an integer", invalid);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(PREFIX + key + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    /**
     * Returns the immutable settings of the last successful start, for adapter-specific metrics tags.
     *
     * @return the settings
     * @throws IllegalStateException before the first successful start
     */
    public AgentSettings settings()
    {
        AgentSettings configured = settings;
        if (configured == null) {
            throw new IllegalStateException("the GrantForge HDFS agent has not started");
        }
        return configured;
    }

    /**
     * Returns whether an undetermined policy check may retain a successful native permission decision.
     *
     * @return the configured fallback flag, false before startup
     */
    public boolean nativeFallback()
    {
        return nativeFallback;
    }

    /**
     * Returns the current optional monitoring hooks.
     *
     * @return the monitor, or the no-op monitor
     */
    public HdfsMetrics metrics()
    {
        return metrics;
    }

    /**
     * Installs adapter-specific monitoring hooks without changing authorization decisions.
     *
     * @param value the hooks, or null to disable monitoring
     */
    public void setMetrics(@Nullable HdfsMetrics value)
    {
        metrics = value == null ? HdfsMetrics.NONE : value;
    }

    /**
     * Captures one immutable policy snapshot for a complete callback, preventing grants from mixed policy versions.
     *
     * @return the captured decision function, undetermined if no snapshot exists
     * @throws IllegalStateException when stopped or bound to a service whose type is not hdfs
     */
    // This method borrows the shared agent; only stop() owns closing it.
    @SuppressWarnings("PMD.CloseResource")
    public Function<AccessRequest, AgentDecision> session()
    {
        GrantForgeAgent running = agent;
        if (running == null) {
            throw new IllegalStateException("the GrantForge HDFS agent is not running");
        }
        Snapshot snapshot = running.snapshot();
        if (snapshot != null && !"hdfs".equals(snapshot.serviceType())) {
            throw new IllegalStateException("the NameNode agent token is bound to a service whose type is not hdfs");
        }
        HdfsMetrics monitor = metrics;
        try {
            if (snapshot == null) {
                monitor.missingSnapshot();
            }
            monitor.snapshot(snapshot == null ? 0 : snapshot.policyVersion(), running.queuedEvents(), running.droppedEvents(),
                    running.serverReachable());
        }
        catch (RuntimeException unavailable) {
            LOG.log(Level.FINE, "HDFS agent monitoring is unavailable", unavailable);
        }
        return snapshot == null ? request -> AgentDecision.withoutSnapshot() : snapshot::decide;
    }

    /**
     * Creates the policy overlay used after the adapter's native permission checks have succeeded.
     *
     * @return the overlay; it captures a session lazily once for each authorization callback
     */
    public HdfsAuthorizer authorizer()
    {
        return new HdfsAuthorizer(this::session, this::record, this::nativeFallback, this::metrics);
    }

    // Audit callbacks borrow the shared agent; shutdown alone closes it.
    @SuppressWarnings("PMD.CloseResource")
    private void record(AccessEvent event)
    {
        GrantForgeAgent running = agent;
        if (running != null) {
            running.record(event);
        }
    }

    /** Stops the background agent and flushes or spools its events; permission checks thereafter fail closed. */
    // Clearing the published reference is the shutdown transition; this lifecycle method owns the resource.
    @SuppressWarnings({"PMD.CloseResource", "PMD.NullAssignment"})
    public void stop()
    {
        lifecycle.lock();
        try {
            GrantForgeAgent running = agent;
            agent = null;
            if (running != null) {
                running.close();
            }
            metrics = HdfsMetrics.NONE;
        }
        finally {
            lifecycle.unlock();
        }
    }

    /** Stops the runtime; equivalent to {@link #stop()}. */
    @Override
    public void close()
    {
        stop();
    }
}
