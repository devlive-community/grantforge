// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import com.github.benmanes.caffeine.cache.Ticker;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.AuditAggregator;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.SigningKey;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.Policy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * The agent of one HBase process: its settings, the signed-policy agent that keeps the service's snapshot and ships its
 * access events, the decisions kept for the requests the process repeats and the folding of repeated accesses. HBase
 * creates a coprocessor for the master, for the region server and for every region it opens; all of them share the
 * agent, which starts with the first and stops with the last. Settings are named {@code grantforge.*} and read from the
 * process's own configuration, {@code hbase-site.xml}.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public final class HBaseAgentRuntime
{
    /** The prefix of the agent's settings. */
    public static final String PREFIX = "grantforge.";

    /** The setting with the most decisions kept; zero keeps none. */
    static final String CACHE_SIZE = "decision.cache.size";
    /** The setting with how long a decision is kept, in milliseconds. */
    static final String CACHE_TTL = "decision.cache.ttl.ms";
    /** The setting with how long identical allowed accesses are folded into one event, in milliseconds; zero folds none. */
    static final String AUDIT_WINDOW = "audit.fold.window.ms";

    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "hbase";

    private static final Logger LOG = LoggerFactory.getLogger(HBaseAgentRuntime.class);

    private final ReentrantLock lifecycle = new ReentrantLock();
    private final Function<AgentSettings, GrantForgeAgent> factory;
    private final Ticker ticker;
    private final AtomicLong warnedVersion = new AtomicLong(Long.MIN_VALUE);
    private volatile @Nullable GrantForgeAgent agent;
    private volatile @Nullable AuditAggregator audit;
    private volatile DecisionCache decisions = new DecisionCache(0, Duration.ofSeconds(1));
    private volatile @Nullable Finer finer;
    private int users;

    /** Whether the policies of one snapshot name column families or columns. */
    private static final class Finer
    {
        final Snapshot snapshot;
        final boolean names;

        Finer(Snapshot snapshot, boolean names)
        {
            this.snapshot = snapshot;
            this.names = names;
        }
    }

    /** Creates a runtime that uses the real signed-policy agent and its background audit shipper. */
    public HBaseAgentRuntime()
    {
        this(configuration -> GrantForgeAgent.start(configuration, Collections.emptyMap()), Ticker.systemTicker());
    }

    HBaseAgentRuntime(Function<AgentSettings, GrantForgeAgent> factory, Ticker ticker)
    {
        this.factory = factory;
        this.ticker = ticker;
    }

    /**
     * Takes the agent for one coprocessor, starting it for the first.
     *
     * @param configuration the {@code grantforge.*} settings
     * @param agentVersion the agent's version
     * @return whether this call started it
     * @throws IOException if the token or the pinned signing key cannot be read
     * @throws IllegalArgumentException if a setting is missing or invalid
     */
    // The aggregator is the runtime's own; release() closes it with the agent.
    @SuppressWarnings("PMD.CloseResource")
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
            long size = number(configuration, CACHE_SIZE, 100_000, 0, 10_000_000);
            Duration ttl = Duration.ofMillis(number(configuration, CACHE_TTL, 10_000, 1, 3_600_000));
            Duration window = Duration.ofMillis(number(configuration, AUDIT_WINDOW, 10_000, 0, 3_600_000));
            GrantForgeAgent started = factory.apply(parse(configuration, agentVersion));
            decisions = new DecisionCache(size, ttl, ticker);
            audit = AuditAggregator.start(window, started::record);
            agent = started;
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
            throw new IllegalArgumentException("missing HBase agent setting " + key);
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
     * Returns the policies one request is checked against: those of the current snapshot, which refuse everything while
     * there is none, when stopped, or when the token is bound to a service of another type.
     *
     * @return the policies
     */
    // This method borrows the shared agent; only release() owns closing it.
    @SuppressWarnings("PMD.CloseResource")
    Policies policies()
    {
        GrantForgeAgent running = agent;
        Snapshot snapshot = running == null ? null : running.snapshot();
        if (snapshot != null && !SERVICE_TYPE.equals(snapshot.serviceType())) {
            long version = snapshot.policyVersion();
            if (warnedVersion.getAndSet(version) != version) {
                LOG.error("The HBase agent token is bound to service {} of type {}, not hbase; requests are refused", snapshot.service(),
                        snapshot.serviceType());
            }
            return new Policies(null, decisions, false);
        }
        return new Policies(snapshot, decisions, snapshot != null && namesColumns(snapshot));
    }

    /** Whether a snapshot's policies name column families or columns, which reads must then be filtered by. */
    // A snapshot is the same when it is the same object, as the agent replaces it whole.
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private boolean namesColumns(Snapshot snapshot)
    {
        Finer known = finer;
        if (known != null && known.snapshot == snapshot) {
            return known.names;
        }
        boolean names = false;
        for (Policy policy : snapshot.policies()) {
            if (policy.resources().containsKey(HBaseResource.COLUMN_FAMILY)) {
                names = true;
                break;
            }
        }
        finer = new Finer(snapshot, names);
        return names;
    }

    /**
     * Reports an access event, folded with identical allowed ones.
     *
     * @param event the event
     */
    // This method borrows the shared aggregator; only release() owns closing it.
    @SuppressWarnings("PMD.CloseResource")
    void audit(AccessEvent event)
    {
        AuditAggregator aggregator = audit;
        if (aggregator != null) {
            aggregator.record(event);
        }
    }

    /**
     * Returns whether the agent runs.
     *
     * @return {@code true} between the first acquire and the last release
     */
    boolean running()
    {
        return agent != null;
    }

    /** Gives back the agent of one coprocessor; the last sends the folded events and stops the agent. */
    // Clearing the published references is the shutdown transition; this lifecycle method owns the resources.
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
            AuditAggregator aggregator = audit;
            audit = null;
            if (aggregator != null) {
                aggregator.close();
            }
            agent = null;
            finer = null;
            running.close();
        }
        finally {
            lifecycle.unlock();
        }
    }
}
