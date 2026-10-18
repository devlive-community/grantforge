// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

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
 * The agent of one Kafka process: its settings and the signed-policy agent that keeps the service's snapshot and ships
 * its access events. A process that is broker and controller at once creates an authorizer for each role; both share
 * the agent, which starts with the first and stops with the last. Settings are named {@code grantforge.*} and read from
 * the process's own configuration, as Kafka hands it to its authorizer.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public final class KafkaAgentRuntime
{
    /** The prefix of the agent's settings. */
    public static final String PREFIX = "grantforge.";

    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "kafka";

    private final ReentrantLock lifecycle = new ReentrantLock();
    private final Function<AgentSettings, GrantForgeAgent> factory;
    private volatile @Nullable GrantForgeAgent agent;
    private int users;

    /** Creates a runtime that uses the real signed-policy agent and its background audit shipper. */
    public KafkaAgentRuntime()
    {
        this(configuration -> GrantForgeAgent.start(configuration, Collections.emptyMap()));
    }

    KafkaAgentRuntime(Function<AgentSettings, GrantForgeAgent> factory)
    {
        this.factory = factory;
    }

    /**
     * Takes the agent for one authorizer, starting it for the first.
     *
     * @param configuration the {@code grantforge.*} settings
     * @param agentVersion the agent's version
     * @return whether this call started it
     * @throws IOException if the token or the pinned signing key cannot be read
     * @throws IllegalArgumentException if a setting is missing or invalid
     */
    public boolean acquire(Map<String, String> configuration, String agentVersion) throws IOException
    {
        lifecycle.lock();
        try {
            if (agent != null) {
                users++;
                return false;
            }
            String version = agentVersion.trim();
            if (version.isEmpty() || agentVersion.length() > 64) {
                throw new IllegalArgumentException("the agent version must be 1 to 64 characters");
            }
            agent = factory.apply(parse(configuration, agentVersion));
            users = 1;
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
            throw new IllegalArgumentException("missing Kafka agent setting " + key);
        }
        return value;
    }

    private static Duration duration(Map<String, String> values, String key, long fallback, boolean refresh)
    {
        long minimum = refresh ? 1000 : 1;
        return Duration.ofMillis(number(values, key, fallback, minimum, refresh ? Long.MAX_VALUE : Integer.MAX_VALUE));
    }

    /**
     * Reads a whole-number setting.
     *
     * @param values the settings
     * @param key its name after {@code grantforge.}
     * @param fallback its value when it is not set
     * @param minimum the smallest value allowed
     * @param maximum the largest value allowed
     * @return the value
     * @throws IllegalArgumentException if it is no whole number or out of range
     */
    static long number(Map<String, String> values, String key, long fallback, long minimum, long maximum)
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
     * Returns the current snapshot.
     *
     * @return the snapshot, or {@code null} while there is none yet
     * @throws IllegalStateException when stopped
     */
    // This method borrows the shared agent; only release() owns closing it.
    @SuppressWarnings("PMD.CloseResource")
    public @Nullable Snapshot snapshot()
    {
        GrantForgeAgent running = agent;
        if (running == null) {
            throw new IllegalStateException("the GrantForge Kafka agent is not running");
        }
        return running.snapshot();
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

    /** Gives back the agent of one authorizer; the last stops it, flushing or spooling its events. */
    // Clearing the published reference is the shutdown transition; this lifecycle method owns the resource.
    @SuppressWarnings({"PMD.CloseResource", "PMD.NullAssignment"})
    public void release()
    {
        lifecycle.lock();
        try {
            GrantForgeAgent running = agent;
            if (running == null) {
                return;
            }
            users--;
            if (users > 0) {
                return;
            }
            agent = null;
            running.close();
        }
        finally {
            lifecycle.unlock();
        }
    }
}
