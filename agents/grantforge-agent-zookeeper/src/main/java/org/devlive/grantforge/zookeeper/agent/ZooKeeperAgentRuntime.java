// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.SigningKey;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * The agent of one ZooKeeper server: its settings, the signed-policy agent that keeps the service's snapshot,
 * and the authorizer that answers the server's ACL checks. ACL callbacks do not report final access audit events. Settings are named {@code grantforge.*},
 * which a server takes from {@code zoo.cfg} as it takes any setting it does not know itself.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public final class ZooKeeperAgentRuntime
        implements AutoCloseable
{
    /** The prefix of the agent's settings. */
    public static final String PREFIX = "grantforge.";

    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "zookeeper";

    private final ReentrantLock lifecycle = new ReentrantLock();
    private final Function<AgentSettings, GrantForgeAgent> factory;
    private volatile @Nullable GrantForgeAgent agent;
    private volatile boolean shortNames = true;

    /** Creates a runtime that uses the real signed-policy agent and its background audit shipper. */
    public ZooKeeperAgentRuntime()
    {
        this(configuration -> GrantForgeAgent.start(configuration, Collections.emptyMap()));
    }

    ZooKeeperAgentRuntime(Function<AgentSettings, GrantForgeAgent> factory)
    {
        this.factory = factory;
    }

    /**
     * Starts once; later starts do nothing until it is stopped.
     *
     * @param configuration the {@code grantforge.*} settings
     * @param agentVersion the agent's version
     * @throws IOException if the token or the pinned signing key cannot be read
     * @throws IllegalArgumentException if a setting is missing or invalid
     */
    public void start(Map<String, String> configuration, String agentVersion) throws IOException
    {
        lifecycle.lock();
        try {
            if (agent != null) {
                return;
            }
            Map<String, String> values = new HashMap<>(configuration);
            String version = agentVersion.trim();
            if (version.isEmpty() || agentVersion.length() > 64) {
                throw new IllegalArgumentException("the agent version must be 1 to 64 characters");
            }
            String mapping = values.containsKey(PREFIX + "principal.mapping") ? values.get(PREFIX + "principal.mapping").trim()
                    .toLowerCase(Locale.ROOT) : "short";
            if (!"short".equals(mapping) && !"full".equals(mapping)) {
                throw new IllegalArgumentException(PREFIX + "principal.mapping must be short or full");
            }
            AgentSettings parsed = parse(values, agentVersion);
            shortNames = "short".equals(mapping);
            agent = factory.apply(parsed);
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
            throw new IllegalArgumentException("missing ZooKeeper agent setting " + key);
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
     * Captures the current snapshot for one question, so it is answered by one policy version.
     *
     * @return the decision function, undetermined while there is no snapshot yet
     * @throws IllegalStateException when stopped, or bound to a service whose type is not zookeeper
     */
    // This method borrows the shared agent; only stop() owns closing it.
    @SuppressWarnings("PMD.CloseResource")
    public Function<AccessRequest, AgentDecision> session()
    {
        GrantForgeAgent running = agent;
        if (running == null) {
            throw new IllegalStateException("the GrantForge ZooKeeper agent is not running");
        }
        Snapshot snapshot = running.snapshot();
        if (snapshot == null) {
            return request -> AgentDecision.withoutSnapshot();
        }
        if (!SERVICE_TYPE.equals(snapshot.serviceType())) {
            throw new IllegalStateException("the ZooKeeper agent token is bound to a service whose type is not zookeeper");
        }
        return snapshot::decide;
    }

    /**
     * Creates the authorizer that answers the server's ACL checks.
     *
     * @return the authorizer
     */
    public ZooKeeperAuthorizer authorizer()
    {
        return new ZooKeeperAuthorizer(this::session, shortNames);
    }

    /** Stops the agent and flushes or spools its events; ACL checks thereafter refuse. */
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
