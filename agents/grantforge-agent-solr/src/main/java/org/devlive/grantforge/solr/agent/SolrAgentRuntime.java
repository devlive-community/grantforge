// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import com.github.benmanes.caffeine.cache.Ticker;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.AuditAggregator;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.SigningKey;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.ConditionEvaluator;
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
import java.util.function.Function;

/**
 * The agent of one Solr node: its settings, the signed-policy agent that keeps the service's snapshot and ships its access
 * events, the decisions kept for the checks the node repeats and the folding of repeated accesses. Settings are named
 * {@code grantforge.*} and read from the authorization section of the cluster's {@code security.json}.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
final class SolrAgentRuntime
        implements AutoCloseable
{
    /** The prefix of the agent's settings. */
    static final String PREFIX = "grantforge.";

    /** The setting with the most decisions kept; zero keeps none. */
    static final String CACHE_SIZE = "decision.cache.size";
    /** The setting with how long a decision is kept, in milliseconds. */
    static final String CACHE_TTL = "decision.cache.ttl.ms";
    /** The setting with how long identical allowed accesses are folded into one event, in milliseconds; zero folds none. */
    static final String AUDIT_WINDOW = "audit.fold.window.ms";

    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "solr";

    /** The conditions the agent evaluates, by evaluator name. */
    static final Map<String, ConditionEvaluator> EVALUATORS = Collections.<String, ConditionEvaluator>singletonMap(AddressRange.TYPE,
            new AddressRange());

    private static final Logger LOG = LoggerFactory.getLogger(SolrAgentRuntime.class);

    private final GrantForgeAgent agent;
    private final AuditAggregator audit;
    private final DecisionCache decisions;
    private final AtomicLong warnedVersion = new AtomicLong(Long.MIN_VALUE);
    private volatile boolean closed;

    private SolrAgentRuntime(GrantForgeAgent agent, AuditAggregator audit, DecisionCache decisions)
    {
        this.agent = agent;
        this.audit = audit;
        this.decisions = decisions;
    }

    /**
     * Starts the agent with the real signed-policy agent and its background audit shipper.
     *
     * @param configuration the {@code grantforge.*} settings
     * @param agentVersion the agent's version
     * @return the running agent
     * @throws IOException if the token or the pinned signing key cannot be read
     * @throws IllegalArgumentException if a setting is missing or invalid
     */
    static SolrAgentRuntime start(Map<String, String> configuration, String agentVersion) throws IOException
    {
        return start(configuration, agentVersion, settings -> GrantForgeAgent.start(settings, EVALUATORS), Ticker.systemTicker());
    }

    static SolrAgentRuntime start(Map<String, String> configuration, String agentVersion, Function<AgentSettings, GrantForgeAgent> factory,
            Ticker ticker) throws IOException
    {
        String version = agentVersion.trim();
        if (version.isEmpty() || agentVersion.length() > 64) {
            throw new IllegalArgumentException("the agent version must be 1 to 64 characters");
        }
        long size = number(configuration, CACHE_SIZE, 100_000, 0, 10_000_000);
        Duration ttl = Duration.ofMillis(number(configuration, CACHE_TTL, 10_000, 1, 3_600_000));
        Duration window = Duration.ofMillis(number(configuration, AUDIT_WINDOW, 10_000, 0, 3_600_000));
        GrantForgeAgent started = factory.apply(parse(configuration, version));
        return new SolrAgentRuntime(started, AuditAggregator.start(window, started::record), new DecisionCache(size, ttl, ticker));
    }

    private static AgentSettings parse(Map<String, String> values, String version) throws IOException
    {
        String token = read(required(values, PREFIX + "token.file"), StandardCharsets.UTF_8).trim();
        int batch = (int) number(values, "audit.batch.size", 500, 1, AgentSettings.MAX_BATCH);
        int queue = (int) number(values, "audit.queue.capacity", 10_000, 1, 1_000_000);
        if (queue < batch) {
            throw new IllegalArgumentException(PREFIX + "audit.queue.capacity must be at least " + PREFIX + "audit.batch.size");
        }
        AgentSettings.Builder builder = AgentSettings.builder().server(URI.create(required(values, PREFIX + "server.url")))
                .token(token).instance(required(values, PREFIX + "instance"))
                .cacheDirectory(Paths.get(required(values, PREFIX + "cache.dir"))).agentVersion(version)
                .timeouts(duration(values, "connect.timeout.ms", 5000, false), duration(values, "read.timeout.ms", 8000, false))
                .refreshInterval(duration(values, "refresh.interval.ms", 30_000, true))
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
            throw new IllegalArgumentException("missing Solr agent setting " + key);
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
     * Returns the policies one check is decided against: those of the current snapshot, which refuse everything while
     * there is none, once closed, or when the token is bound to a service of another type.
     *
     * @return the policies
     */
    Policies policies()
    {
        Snapshot snapshot = closed ? null : agent.snapshot();
        if (snapshot != null && !SERVICE_TYPE.equals(snapshot.serviceType())) {
            long version = snapshot.policyVersion();
            if (warnedVersion.getAndSet(version) != version) {
                LOG.error("The Solr agent token is bound to service {} of type {}, not solr; requests are refused", snapshot.service(),
                        snapshot.serviceType());
            }
            return new Policies(null, decisions);
        }
        return new Policies(snapshot, decisions);
    }

    /**
     * Reports an access event, folded with identical allowed ones.
     *
     * @param event the event
     */
    void audit(AccessEvent event)
    {
        if (!closed) {
            audit.record(event);
        }
    }

    /** Sends the folded events and stops the agent. */
    @Override
    public void close()
    {
        if (closed) {
            return;
        }
        closed = true;
        audit.close();
        agent.close();
    }

    /**
     * Returns whether the agent was closed.
     *
     * @return {@code true} once closed
     */
    boolean closed()
    {
        return closed;
    }
}
