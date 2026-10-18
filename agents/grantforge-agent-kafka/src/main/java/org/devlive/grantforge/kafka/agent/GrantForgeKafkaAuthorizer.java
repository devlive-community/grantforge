// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

import com.github.benmanes.caffeine.cache.Ticker;
import org.apache.kafka.common.Endpoint;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.utils.SecurityUtils;
import org.apache.kafka.server.authorizer.AclCreateResult;
import org.apache.kafka.server.authorizer.AclDeleteResult;
import org.apache.kafka.server.authorizer.Action;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.authorizer.AuthorizationResult;
import org.apache.kafka.server.authorizer.Authorizer;
import org.apache.kafka.server.authorizer.AuthorizerServerInfo;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.AuditAggregator;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

/**
 * The authorizer of a Kafka broker or controller, checking every request against the GrantForge policies of its Kafka
 * service; name it in {@code authorizer.class.name}. Beyond the checks of each request it
 * <ul>
 *   <li>answers whether a user may act on some resource of a type, which Kafka asks before it lets an idempotent producer
 *   start without the cluster's {@code idempotent_write}, from the policies themselves rather than from a list of ACLs;</li>
 *   <li>shows the policies as read-only ACLs, so {@code kafka-acls --list} tells what GrantForge enforces, and refuses to
 *   create or delete ACLs, since policies are changed in GrantForge;</li>
 *   <li>keeps the decisions of the current snapshot for the requests a broker repeats on every produce and fetch;</li>
 *   <li>folds identical allowed accesses into one audit event per window, reporting refusals one by one at once.</li>
 * </ul>
 *
 * <p>The principals of {@code super.users} may do everything, as with Kafka's own authorizer, and are not audited. A
 * service that is not in use leaves Kafka unchecked, as it is without an authorizer; without a snapshot, everything
 * else is refused. Listeners that are not started early wait for the first snapshot, at most for
 * {@code grantforge.start.timeout.ms}.
 */
// Kafka runs the authorizer in its own process, not in a web container; the readiness thread and the audit aggregator are
// the authorizer's own and close() stops them.
@SuppressWarnings({"PMD.DoNotUseThreads", "PMD.AvoidUsingVolatile", "PMD.CloseResource"})
public final class GrantForgeKafkaAuthorizer
        implements Authorizer
{
    /** The setting with the most decisions kept; zero keeps none. */
    static final String CACHE_SIZE = "decision.cache.size";
    /** The setting with how long a decision is kept, in milliseconds. */
    static final String CACHE_TTL = "decision.cache.ttl.ms";
    /** The setting with how long identical allowed accesses are folded into one event, in milliseconds; zero folds none. */
    static final String AUDIT_WINDOW = "audit.fold.window.ms";
    /** The setting with how long listeners wait for the first snapshot, in milliseconds. */
    static final String START_TIMEOUT = "start.timeout.ms";

    private static final Logger LOG = LoggerFactory.getLogger(GrantForgeKafkaAuthorizer.class);
    private static final KafkaAgentRuntime RUNTIME = new KafkaAgentRuntime();
    private static final String MANAGED = "the ACLs of this cluster are GrantForge policies; change them in GrantForge";
    private static final String ANY_RESOURCE = "any";
    private static final String ONE_RESOURCE = "one";

    private final KafkaAgentRuntime runtime;
    private final Clock clock;
    private final Ticker ticker;
    private final AtomicLong warnedVersion = new AtomicLong(Long.MIN_VALUE);
    private Set<String> superUsers = Collections.emptySet();
    private DecisionCache decisions = new DecisionCache(0, Duration.ofSeconds(1));
    private @Nullable AuditAggregator audit;
    private Duration startTimeout = Duration.ofSeconds(30);
    private volatile @Nullable ScheduledExecutorService readiness;
    private final AtomicBoolean acquired = new AtomicBoolean();

    /** Creates the authorizer Kafka loads, with the agent of the process. */
    public GrantForgeKafkaAuthorizer()
    {
        this(RUNTIME, Clock.systemUTC(), Ticker.systemTicker());
    }

    GrantForgeKafkaAuthorizer(KafkaAgentRuntime runtime, Clock clock, Ticker ticker)
    {
        this.runtime = requireNonNull(runtime, "runtime");
        this.clock = requireNonNull(clock, "clock");
        this.ticker = requireNonNull(ticker, "ticker");
    }

    @Override
    public void configure(Map<String, ?> configs)
    {
        Map<String, String> settings = new HashMap<>();
        for (Map.Entry<String, ?> entry : configs.entrySet()) {
            if (entry.getKey().startsWith(KafkaAgentRuntime.PREFIX) && entry.getValue() != null) {
                settings.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        Object configured = configs.get("super.users");
        superUsers = superUsers(configured == null ? "" : String.valueOf(configured));
        try {
            long size = KafkaAgentRuntime.number(settings, CACHE_SIZE, 100_000, 0, 10_000_000);
            Duration ttl = Duration.ofMillis(KafkaAgentRuntime.number(settings, CACHE_TTL, 10_000, 1, 3_600_000));
            Duration window = Duration.ofMillis(KafkaAgentRuntime.number(settings, AUDIT_WINDOW, 10_000, 0, 3_600_000));
            startTimeout = Duration.ofMillis(KafkaAgentRuntime.number(settings, START_TIMEOUT, 30_000, 0, 600_000));
            decisions = new DecisionCache(size, ttl, ticker);
            if (runtime.acquire(settings, version())) {
                LOG.info("The GrantForge Kafka agent started");
            }
            acquired.set(true);
            audit = AuditAggregator.start(window, runtime::record);
        }
        catch (IOException | IllegalArgumentException invalid) {
            ConfigException refused = new ConfigException("GrantForge cannot start the Kafka agent: " + invalid.getMessage());
            refused.initCause(invalid);
            throw refused;
        }
    }

    /**
     * Reads {@code super.users}: principals such as {@code User:admin}, separated by semicolons.
     *
     * @param value the setting
     * @return the principals
     */
    static Set<String> superUsers(String value)
    {
        Set<String> principals = new HashSet<>();
        for (String principal : value.split(";")) {
            String name = principal.trim();
            if (!name.isEmpty()) {
                principals.add(name);
            }
        }
        return Collections.unmodifiableSet(principals);
    }

    @Override
    public Map<Endpoint, ? extends CompletionStage<Void>> start(AuthorizerServerInfo serverInfo)
    {
        CompletableFuture<Void> ready = ready();
        Collection<String> early = serverInfo.earlyStartListeners();
        Map<Endpoint, CompletableFuture<Void>> futures = new LinkedHashMap<>();
        for (Endpoint endpoint : serverInfo.endpoints()) {
            boolean now = endpoint.listenerName().isPresent() && early.contains(endpoint.listenerName().get());
            futures.put(endpoint, now ? CompletableFuture.<Void>completedFuture(null) : ready);
        }
        return futures;
    }

    /** Completes when the agent has its first snapshot, or after the start timeout. */
    private CompletableFuture<Void> ready()
    {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        if (loaded()) {
            ready.complete(null);
            return ready;
        }
        ScheduledExecutorService poll = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "grantforge-kafka-agent-ready");
            thread.setDaemon(true);
            return thread;
        });
        readiness = poll;
        long deadline = System.nanoTime() + startTimeout.toNanos();
        poll.scheduleWithFixedDelay(() -> {
            boolean loaded = loaded();
            if (loaded || System.nanoTime() - deadline >= 0) {
                if (!loaded) {
                    LOG.warn("GrantForge has no policy snapshot after {} ms; requests are refused until one arrives", startTimeout.toMillis());
                }
                ready.complete(null);
                poll.shutdown();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return ready;
    }

    private boolean loaded()
    {
        try {
            return runtime.snapshot() != null;
        }
        catch (IllegalStateException stopped) {
            return true;
        }
    }

    @Override
    public List<AuthorizationResult> authorize(AuthorizableRequestContext requestContext, List<Action> actions)
    {
        KafkaPrincipal principal = requestContext.principal();
        if (superUser(principal)) {
            return new ArrayList<>(Collections.nCopies(actions.size(), AuthorizationResult.ALLOWED));
        }
        List<AuthorizationResult> results = new ArrayList<>(actions.size());
        Snapshot snapshot = snapshot();
        boolean unchecked = snapshot != null && !snapshot.serviceEnabled();
        String user = principal.getName();
        String clientIp = clientIp(requestContext.clientAddress());
        String api = KafkaAccess.api(requestContext.requestType());
        for (Action action : actions) {
            if (unchecked) {
                results.add(AuthorizationResult.ALLOWED);
                continue;
            }
            ResourcePattern resource = action.resourcePattern();
            String level = KafkaAccess.level(resource.resourceType());
            String accessType = KafkaAccess.accessType(action.operation());
            AgentDecision decision = null;
            if (level != null && accessType != null && resource.patternType() == PatternType.LITERAL) {
                decision = snapshot == null ? AgentDecision.withoutSnapshot() : decide(snapshot, user, clientIp, level, resource.name(), accessType);
            }
            boolean allowed = decision != null && decision.allowed();
            results.add(allowed ? AuthorizationResult.ALLOWED : AuthorizationResult.DENIED);
            if (allowed ? action.logIfAllowed() : action.logIfDenied()) {
                AccessEvent.Builder event = AccessEvent.builder(user, resource.name(), accessType == null ? action.operation().name() : accessType,
                        allowed).clientIp(clientIp).resourceType(level == null ? resource.resourceType().name() : level).action(api);
                if (decision != null) {
                    event.decidedBy(decision);
                }
                record(event.enforcedByGrantForge().build());
            }
        }
        return results;
    }

    private AgentDecision decide(Snapshot snapshot, String user, @Nullable String clientIp, String level, String name, String accessType)
    {
        List<String> key = Arrays.asList(ONE_RESOURCE, user, String.valueOf(clientIp), level, name, accessType);
        return decisions.decide(snapshot, key, ignored -> {
            try {
                return snapshot.decide(request(user, clientIp, level, name, accessType));
            }
            catch (RuntimeException broken) {
                LOG.warn("GrantForge cannot decide {} on {} {} for {}: {}", accessType, level, name, user, broken.getMessage());
                return AgentDecision.withoutSnapshot();
            }
        });
    }

    private static AccessRequest request(String user, @Nullable String clientIp, String level, String name, String accessType)
    {
        AccessRequest.Builder request = AccessRequest.builder(user, accessType).resource(level, name);
        if (clientIp != null) {
            request.context(Collections.<String, Object>singletonMap("clientAddress", clientIp));
        }
        return request.build();
    }

    @Override
    public AuthorizationResult authorizeByResourceType(AuthorizableRequestContext requestContext, AclOperation op, ResourceType resourceType)
    {
        SecurityUtils.authorizeByResourceTypeCheckArgs(op, resourceType);
        KafkaPrincipal principal = requestContext.principal();
        if (superUser(principal)) {
            return AuthorizationResult.ALLOWED;
        }
        Snapshot snapshot = snapshot();
        String level = KafkaAccess.level(resourceType);
        String accessType = KafkaAccess.accessType(op);
        if (snapshot == null || level == null || accessType == null) {
            return AuthorizationResult.DENIED;
        }
        if (!snapshot.serviceEnabled()) {
            return AuthorizationResult.ALLOWED;
        }
        String user = principal.getName();
        String clientIp = clientIp(requestContext.clientAddress());
        List<String> key = Arrays.asList(ANY_RESOURCE, user, String.valueOf(clientIp), level, "", accessType);
        AgentDecision decision = decisions.decide(snapshot, key, ignored -> {
            try {
                return snapshot.decideAnyResource(request(user, clientIp, level, ResourcePattern.WILDCARD_RESOURCE, accessType));
            }
            catch (RuntimeException broken) {
                LOG.warn("GrantForge cannot decide {} on any {} for {}: {}", accessType, level, user, broken.getMessage());
                return AgentDecision.withoutSnapshot();
            }
        });
        return decision.allowed() ? AuthorizationResult.ALLOWED : AuthorizationResult.DENIED;
    }

    private boolean superUser(KafkaPrincipal principal)
    {
        return superUsers.contains(principal.getPrincipalType() + ":" + principal.getName());
    }

    private static @Nullable String clientIp(@Nullable InetAddress address)
    {
        return address == null ? null : address.getHostAddress();
    }

    /** The current snapshot, or {@code null} without one, when stopped, or for a service of another type. */
    private @Nullable Snapshot snapshot()
    {
        Snapshot snapshot;
        try {
            snapshot = runtime.snapshot();
        }
        catch (IllegalStateException stopped) {
            return null;
        }
        if (snapshot != null && !KafkaAgentRuntime.SERVICE_TYPE.equals(snapshot.serviceType())) {
            long version = snapshot.policyVersion();
            if (warnedVersion.getAndSet(version) != version) {
                LOG.error("The Kafka agent token is bound to service {} of type {}, not kafka; requests are refused", snapshot.service(),
                        snapshot.serviceType());
            }
            return null;
        }
        return snapshot;
    }

    private void record(AccessEvent event)
    {
        AuditAggregator aggregator = audit;
        if (aggregator == null) {
            runtime.record(event);
        }
        else {
            aggregator.record(event);
        }
    }

    @Override
    public List<? extends CompletionStage<AclCreateResult>> createAcls(AuthorizableRequestContext requestContext,
            List<AclBinding> aclBindings)
    {
        return Collections.nCopies(aclBindings.size(), CompletableFuture.completedFuture(new AclCreateResult(
                new InvalidRequestException(MANAGED))));
    }

    @Override
    public List<? extends CompletionStage<AclDeleteResult>> deleteAcls(AuthorizableRequestContext requestContext,
            List<AclBindingFilter> aclBindingFilters)
    {
        return Collections.nCopies(aclBindingFilters.size(), CompletableFuture.completedFuture(new AclDeleteResult(
                new InvalidRequestException(MANAGED))));
    }

    @Override
    public Iterable<AclBinding> acls(AclBindingFilter filter)
    {
        Snapshot snapshot = snapshot();
        if (snapshot == null || !snapshot.serviceEnabled()) {
            return Collections.emptyList();
        }
        return AclView.of(snapshot, filter, clock.instant());
    }

    @Override
    public int aclCount()
    {
        Snapshot snapshot = snapshot();
        return snapshot == null || !snapshot.serviceEnabled() ? 0 : AclView.of(snapshot, AclBindingFilter.ANY, clock.instant()).size();
    }

    /** Sends the access events that wait and gives back the agent, which the last authorizer of the process stops. */
    // Clearing the published references is the shutdown transition.
    @SuppressWarnings("PMD.NullAssignment")
    @Override
    public void close()
    {
        ScheduledExecutorService poll = readiness;
        readiness = null;
        if (poll != null) {
            poll.shutdownNow();
        }
        AuditAggregator aggregator = audit;
        audit = null;
        if (aggregator != null) {
            aggregator.close();
        }
        if (acquired.getAndSet(false)) {
            runtime.release();
        }
    }

    /**
     * Returns the agent's version.
     *
     * @return the GrantForge version it was built with
     */
    static String version()
    {
        Properties properties = new Properties();
        try (InputStream in = GrantForgeKafkaAuthorizer.class.getResourceAsStream("/META-INF/grantforge/kafka-agent-version.properties")) {
            if (in != null) {
                properties.load(in);
            }
        }
        catch (IOException unreadable) {
            LOG.debug("The agent version cannot be read", unreadable);
        }
        return properties.getProperty("grantforge.version", "unknown");
    }
}
