// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.SigningKey;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * The agent of one HiveServer2: its settings and the signed-policy agent that keeps the service's snapshot and ships
 * its access events. Settings are named {@code grantforge.*}, which HiveServer2 reads from {@code hive-site.xml} or
 * {@code hiveserver2-site.xml} as it reads any setting it does not know itself.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public final class HiveAgentRuntime
        implements AutoCloseable
{
    /** The prefix of the agent's settings. */
    public static final String PREFIX = "grantforge.";

    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "hive";

    private final ReentrantLock lifecycle = new ReentrantLock();
    private final Function<AgentSettings, GrantForgeAgent> factory;
    private volatile @Nullable GrantForgeAgent agent;

    /** Creates a runtime that uses the real signed-policy agent and its background audit shipper. */
    public HiveAgentRuntime()
    {
        this(configuration -> GrantForgeAgent.start(configuration, Collections.emptyMap()));
    }

    HiveAgentRuntime(Function<AgentSettings, GrantForgeAgent> factory)
    {
        this.factory = factory;
    }

    /**
     * Starts once; later starts do nothing until it is stopped.
     *
     * @param configuration the {@code grantforge.*} settings
     * @param agentVersion the agent's version
     * @return whether this call started it
     * @throws IOException if the token or the pinned signing key cannot be read
     * @throws IllegalArgumentException if a setting is missing or invalid
     */
    public boolean start(Map<String, String> configuration, String agentVersion) throws IOException
    {
        lifecycle.lock();
        try {
            if (agent != null) {
                return false;
            }
            String version = agentVersion.trim();
            if (version.isEmpty() || agentVersion.length() > 64) {
                throw new IllegalArgumentException("the agent version must be 1 to 64 characters");
            }
            agent = factory.apply(parse(configuration, agentVersion));
            return true;
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
        String keyPath = key == null ? "" : key.trim();
        if (!keyPath.isEmpty()) {
            builder.trustedKey(SigningKey.of(read(keyPath, StandardCharsets.US_ASCII)));
        }
        return builder.build();
    }

    private static String read(String path, Charset charset) throws IOException
    {
        return new String(Files.readAllBytes(Paths.get(path)), charset);
    }

    private static String required(Map<String, String> values, String key)
    {
        String configured = values.get(key);
        String value = configured == null ? "" : configured.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("missing Hive agent setting " + key);
        }
        return value;
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
     * Captures the current snapshot for one statement, so it is checked against one policy version.
     *
     * @return the policies, which refuse everything while there is no snapshot yet
     * @throws IllegalStateException when stopped, or bound to a service whose type is not hive
     */
    // This method borrows the shared agent; only stop() owns closing it.
    @SuppressWarnings("PMD.CloseResource")
    public Policies policies()
    {
        GrantForgeAgent running = agent;
        if (running == null) {
            throw new IllegalStateException("the GrantForge Hive agent is not running");
        }
        Snapshot snapshot = running.snapshot();
        if (snapshot != null && !SERVICE_TYPE.equals(snapshot.serviceType())) {
            throw new IllegalStateException("the Hive agent token is bound to a service whose type is not hive");
        }
        return Policies.of(snapshot);
    }

    /**
     * Queues an access event for the server.
     *
     * @param event the event
     * @return whether it was queued; {@code false} when stopped or the queue is full
     */
    public boolean record(AccessEvent event)
    {
        GrantForgeAgent running = agent;
        return running != null && running.record(event);
    }

    /** Stops the agent and flushes or spools its events; checks thereafter refuse. */
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
        }
        finally {
            lifecycle.unlock();
        }
    }

    @Override
    public void close()
    {
        stop();
    }
}
