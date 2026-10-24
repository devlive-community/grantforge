// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import org.apache.pulsar.broker.ServiceConfiguration;
import org.apache.pulsar.broker.authentication.AuthenticationDataSource;
import org.apache.pulsar.broker.authorization.AuthorizationProvider;
import org.apache.pulsar.broker.resources.PulsarResources;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.naming.TopicName;
import org.apache.pulsar.common.policies.data.AuthAction;
import org.apache.pulsar.common.policies.data.NamespaceOperation;
import org.apache.pulsar.common.policies.data.PolicyName;
import org.apache.pulsar.common.policies.data.PolicyOperation;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.apache.pulsar.common.policies.data.TenantOperation;
import org.apache.pulsar.common.policies.data.TopicOperation;
import org.apache.pulsar.common.util.RestException;
import org.devlive.grantforge.agent.AccessEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pulsar's authorization provider in GrantForge's terms, set with {@code authorizationProvider} and enabled with
 * {@code authorizationEnabled}. Producing needs {@code produce} and consuming {@code consume}, through the subscription
 * too; functions, sources, sinks and packages need their own access types, and the administration of topics, namespaces
 * and tenants needs {@code admin_topic}, {@code admin_namespace} and {@code admin_tenant}. Each is decided at the
 * cluster, the tenant, the namespace, the topic and the subscription in turn, as Pulsar's own namespace grants cover the
 * topics in them. {@code admin} on the cluster makes a role one of Pulsar's superusers. Every check goes with the
 * client's address, for the policy items that hold for some addresses only, and is reported.
 *
 * <p>The roles of {@code superUserRoles} and {@code proxyRoles} are not checked: they are the brokers' own and those of
 * the proxies, which Pulsar checks the original client of too. Pulsar's own grants and tenant admin roles are not used,
 * and granting through Pulsar is refused.
 */
@SuppressWarnings("PMD.AvoidUsingVolatile")
public class GrantForgePulsarAuthorizer
        implements AuthorizationProvider
{
    static final String PRODUCE = "produce";
    static final String CONSUME = "consume";
    static final String FUNCTIONS = "functions";
    static final String SOURCES = "sources";
    static final String SINKS = "sinks";
    static final String PACKAGES = "packages";
    static final String ADMIN_TOPIC = "admin_topic";
    static final String ADMIN_NAMESPACE = "admin_namespace";
    static final String ADMIN_TENANT = "admin_tenant";
    static final String ADMIN = "admin";

    private static final Logger LOG = LoggerFactory.getLogger(GrantForgePulsarAuthorizer.class);
    private static final int METHOD_NOT_ALLOWED = 405;
    private static final String VERSION_FILE = "/META-INF/grantforge/pulsar-agent-version.properties";

    /** Pulsar's tenant operations that are the cluster's, as its superusers have them. */
    private static final Set<String> CLUSTER_TENANT_OPERATIONS = Set.of("LIST_TENANTS", "CREATE_TENANT", "UPDATE_TENANT", "DELETE_TENANT");

    /** Pulsar's topic operations that a subscription's consumers do. */
    private static final Set<String> CONSUMING = Set.of("GET_SUBSCRIPTIONS", "CONSUME", "SUBSCRIBE", "UNSUBSCRIBE", "SKIP", "EXPIRE_MESSAGES",
            "PEEK_MESSAGES", "RESET_CURSOR", "GET_BACKLOG_SIZE", "SET_REPLICATED_SUBSCRIPTION_STATUS", "GET_REPLICATED_SUBSCRIPTION_STATUS");

    private static final ReentrantLock STARTING = new ReentrantLock();
    private static volatile @Nullable PulsarAgentRuntime shared;

    private volatile @Nullable PulsarAgentRuntime runtime;
    private volatile Set<String> unchecked = Set.of();
    private volatile String cluster = "";

    /** Creates the provider Pulsar loads. */
    public GrantForgePulsarAuthorizer()
    {
        // Started by initialize.
    }

    GrantForgePulsarAuthorizer(PulsarAgentRuntime runtime, Set<String> unchecked, String cluster)
    {
        this.runtime = runtime;
        this.unchecked = Set.copyOf(unchecked);
        this.cluster = cluster;
    }

    /**
     * Starts the agent from the broker's {@code grantforge.*} settings. A broker loads its provider more than once, for its
     * binary protocol, its web service and its functions worker, so one agent serves them all; it stops with the
     * broker's JVM, sending the events it folded.
     */
    @Override
    public void initialize(ServiceConfiguration conf, PulsarResources pulsarResources) throws IOException
    {
        initialize(conf.getSuperUserRoles(), conf.getProxyRoles(), String.valueOf(conf.getClusterName()), conf.getProperties());
    }

    /**
     * Starts the agent, or joins the one the broker started already.
     *
     * @param superUsers the broker's {@code superUserRoles}
     * @param proxies the broker's {@code proxyRoles}
     * @param clusterName the broker's cluster
     * @param properties the broker's settings, the {@code grantforge.*} ones among them
     * @throws IOException if the agent cannot start
     */
    void initialize(Set<String> superUsers, Set<String> proxies, String clusterName, Properties properties) throws IOException
    {
        Set<String> roles = new HashSet<>(superUsers);
        roles.addAll(proxies);
        unchecked = Set.copyOf(roles);
        cluster = clusterName;
        runtime = shared(properties);
        LOG.info("GrantForge decides Pulsar's access in cluster {}; {} are not checked", cluster, unchecked);
    }

    // The runtime lives as long as the broker, which stops it through the shutdown hook.
    @SuppressWarnings("PMD.DoNotUseThreads")
    private static PulsarAgentRuntime shared(Properties properties) throws IOException
    {
        STARTING.lock();
        try {
            PulsarAgentRuntime running = shared;
            if (running != null) {
                return running;
            }
            Map<String, String> settings = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith(PulsarAgentRuntime.PREFIX)) {
                    settings.put(key, properties.getProperty(key));
                }
            }
            PulsarAgentRuntime started = PulsarAgentRuntime.start(settings, version());
            Runtime.getRuntime().addShutdownHook(new Thread(started::close, "grantforge-pulsar-agent-stop"));
            shared = started;
            return started;
        }
        catch (IllegalArgumentException broken) {
            throw new IOException("GrantForge cannot start the Pulsar agent: " + broken.getMessage(), broken);
        }
        finally {
            STARTING.unlock();
        }
    }

    /**
     * Returns the agent's version, as the build wrote it.
     *
     * @return such as {@code 2026.3.0}
     * @throws IOException if the jar has no version
     */
    static String version() throws IOException
    {
        try (InputStream in = GrantForgePulsarAuthorizer.class.getResourceAsStream(VERSION_FILE)) {
            return version(in);
        }
    }

    /**
     * Reads the agent's version from the build's properties.
     *
     * @param in the properties, or {@code null} when the jar has none
     * @return the version
     * @throws IOException if there is none
     */
    static String version(@Nullable InputStream in) throws IOException
    {
        if (in == null) {
            throw new IOException("the agent jar has no " + VERSION_FILE);
        }
        Properties properties = new Properties();
        properties.load(in);
        String version = properties.getProperty("grantforge.version");
        String value = version == null ? "" : version.strip();
        if (value.isEmpty()) {
            throw new IOException(VERSION_FILE + " names no grantforge.version");
        }
        return value;
    }

    /** {@code admin} on the cluster makes a role one of Pulsar's superusers; only an allowance is reported. */
    @Override
    public CompletableFuture<Boolean> isSuperUser(@Nullable String role, @Nullable AuthenticationDataSource authenticationData,
            @Nullable ServiceConfiguration serviceConfiguration)
    {
        return done(decide(role, authenticationData, PulsarResource.cluster(cluster), "administer cluster", null, false, ADMIN));
    }

    /** {@code admin_tenant} on the tenant makes a role its admin; Pulsar's own admin roles are not used. */
    @Override
    public CompletableFuture<Boolean> isTenantAdmin(String tenant, @Nullable String role, @Nullable TenantInfo tenantInfo,
            @Nullable AuthenticationDataSource authenticationData)
    {
        return done(decide(role, authenticationData, PulsarResource.tenant(cluster, tenant), "administer tenant", null, false, ADMIN_TENANT));
    }

    @Override
    public CompletableFuture<Boolean> canProduceAsync(TopicName topicName, @Nullable String role, @Nullable AuthenticationDataSource authenticationData)
    {
        return done(decide(role, authenticationData, PulsarResource.topic(cluster, topicName, null), "produce", null, true, PRODUCE));
    }

    @Override
    public CompletableFuture<Boolean> canConsumeAsync(TopicName topicName, @Nullable String role, @Nullable AuthenticationDataSource authenticationData,
            @Nullable String subscription)
    {
        return done(decide(role, authenticationData, PulsarResource.topic(cluster, topicName, text(subscription)), "consume", null, true, CONSUME));
    }

    @Override
    public CompletableFuture<Boolean> canLookupAsync(TopicName topicName, @Nullable String role, @Nullable AuthenticationDataSource authenticationData)
    {
        return done(decide(role, authenticationData, PulsarResource.topic(cluster, topicName, null), "lookup", null, true, PRODUCE, CONSUME));
    }

    @Override
    public CompletableFuture<Boolean> allowFunctionOpsAsync(NamespaceName namespaceName, @Nullable String role,
            @Nullable AuthenticationDataSource authenticationData)
    {
        return done(decide(role, authenticationData, PulsarResource.namespace(cluster, namespaceName), "functions", null, true, FUNCTIONS));
    }

    @Override
    public CompletableFuture<Boolean> allowSourceOpsAsync(NamespaceName namespaceName, @Nullable String role,
            @Nullable AuthenticationDataSource authenticationData)
    {
        return done(decide(role, authenticationData, PulsarResource.namespace(cluster, namespaceName), "sources", null, true, SOURCES));
    }

    @Override
    public CompletableFuture<Boolean> allowSinkOpsAsync(NamespaceName namespaceName, @Nullable String role,
            @Nullable AuthenticationDataSource authenticationData)
    {
        return done(decide(role, authenticationData, PulsarResource.namespace(cluster, namespaceName), "sinks", null, true, SINKS));
    }

    @Override
    public CompletableFuture<Void> grantPermissionAsync(NamespaceName namespace, Set<AuthAction> actions, String role, String authDataJson)
    {
        return refusedGrant("namespace " + namespace);
    }

    @Override
    public CompletableFuture<Void> grantSubscriptionPermissionAsync(NamespaceName namespace, String subscriptionName, Set<String> roles,
            String authDataJson)
    {
        return refusedGrant("subscription " + subscriptionName + " of namespace " + namespace);
    }

    @Override
    public CompletableFuture<Void> revokeSubscriptionPermissionAsync(NamespaceName namespace, String subscriptionName, String role,
            String authDataJson)
    {
        return refusedGrant("subscription " + subscriptionName + " of namespace " + namespace);
    }

    @Override
    public CompletableFuture<Void> grantPermissionAsync(TopicName topicName, Set<AuthAction> actions, String role, String authDataJson)
    {
        return refusedGrant("topic " + topicName);
    }

    /**
     * Pulsar's own grants are not used, so granting through Pulsar would do nothing: it is refused, as a method the admin
     * API does not allow, saying where to grant instead.
     */
    private static CompletableFuture<Void> refusedGrant(String what)
    {
        return CompletableFuture.failedFuture(new RestException(METHOD_NOT_ALLOWED,
                "GrantForge policies decide access to " + what + "; grant it in GrantForge instead of Pulsar"));
    }

    @Override
    public CompletableFuture<Boolean> allowTenantOperationAsync(String tenantName, @Nullable String role, TenantOperation operation,
            @Nullable AuthenticationDataSource authData)
    {
        String name = operation.name();
        boolean clusterWide = CLUSTER_TENANT_OPERATIONS.contains(name);
        PulsarResource resource = clusterWide ? PulsarResource.cluster(cluster) : PulsarResource.tenant(cluster, tenantName);
        return done(decide(role, authData, resource, words(name), clusterWide ? "tenant " + tenantName : null, true,
                clusterWide ? ADMIN : ADMIN_TENANT));
    }

    @Override
    public CompletableFuture<Boolean> allowNamespaceOperationAsync(NamespaceName namespaceName, @Nullable String role, NamespaceOperation operation,
            @Nullable AuthenticationDataSource authData)
    {
        String name = operation.name();
        String[] accessTypes = switch (name) {
            case "PACKAGES" -> new String[] {PACKAGES};
            case "GET_TOPIC", "GET_TOPICS", "GET_BUNDLE" -> new String[] {PRODUCE, CONSUME};
            case "UNSUBSCRIBE", "CLEAR_BACKLOG", "TRIM_TOPIC" -> new String[] {CONSUME};
            default -> new String[] {ADMIN_NAMESPACE};
        };
        return done(decide(role, authData, PulsarResource.namespace(cluster, namespaceName), words(name), null, true, accessTypes));
    }

    @Override
    public CompletableFuture<Boolean> allowNamespacePolicyOperationAsync(NamespaceName namespaceName, PolicyName policy, PolicyOperation operation,
            @Nullable String role, @Nullable AuthenticationDataSource authData)
    {
        boolean read = "READ".equals(operation.name());
        return done(decide(role, authData, PulsarResource.namespace(cluster, namespaceName), policyAction(read, policy.name()), null, true,
                read ? new String[] {PRODUCE, CONSUME} : new String[] {ADMIN_NAMESPACE}));
    }

    @Override
    public CompletableFuture<Boolean> allowTopicOperationAsync(TopicName topicName, @Nullable String role, TopicOperation operation,
            @Nullable AuthenticationDataSource authData)
    {
        String name = operation.name();
        if (CONSUMING.contains(name)) {
            String subscription = authData != null && authData.hasSubscription() ? text(authData.getSubscription()) : null;
            return done(decide(role, authData, PulsarResource.topic(cluster, topicName, subscription), words(name), null, true, CONSUME));
        }
        String[] accessTypes = switch (name) {
            case "PRODUCE" -> new String[] {PRODUCE};
            case "LOOKUP", "GET_STATS", "GET_METADATA" -> new String[] {PRODUCE, CONSUME};
            default -> new String[] {ADMIN_TOPIC};
        };
        return done(decide(role, authData, PulsarResource.topic(cluster, topicName, null), words(name), null, true, accessTypes));
    }

    @Override
    public CompletableFuture<Boolean> allowTopicPolicyOperationAsync(TopicName topicName, @Nullable String role, PolicyName policy,
            PolicyOperation operation, @Nullable AuthenticationDataSource authData)
    {
        boolean read = "READ".equals(operation.name());
        return done(decide(role, authData, PulsarResource.topic(cluster, topicName, null), policyAction(read, policy.name()), null, true,
                read ? new String[] {PRODUCE, CONSUME} : new String[] {ADMIN_TOPIC}));
    }

    // Pulsar 3.0 has no broker, cluster or cluster policy operations and never asks these; their types are only passed on,
    // so that the class loads there without them.
    @Override
    public CompletableFuture<Boolean> allowBrokerOperationAsync(@Nullable String clusterName, @Nullable String brokerId,
            org.apache.pulsar.common.policies.data.BrokerOperation brokerOperation, @Nullable String role,
            @Nullable AuthenticationDataSource authData)
    {
        return administer(clusterName, String.valueOf(brokerOperation), brokerId == null ? null : "broker " + brokerId, role, authData);
    }

    @Override
    public CompletableFuture<Boolean> allowClusterOperationAsync(@Nullable String clusterName,
            org.apache.pulsar.common.policies.data.ClusterOperation clusterOperation, @Nullable String role,
            @Nullable AuthenticationDataSource authData)
    {
        return administer(clusterName, String.valueOf(clusterOperation), null, role, authData);
    }

    @Override
    public CompletableFuture<Boolean> allowClusterPolicyOperationAsync(@Nullable String clusterName, @Nullable String role, PolicyName policy,
            PolicyOperation operation, @Nullable AuthenticationDataSource authData)
    {
        return administer(clusterName, policyAction("READ".equals(operation.name()), policy.name()), null, role, authData);
    }

    private CompletableFuture<Boolean> administer(@Nullable String clusterName, String operation, @Nullable String request, @Nullable String role,
            @Nullable AuthenticationDataSource authData)
    {
        String named = text(clusterName);
        return done(decide(role, authData, PulsarResource.cluster(named == null ? cluster : named), words(operation), request, true, ADMIN));
    }

    /** The agent is the broker's and stops with it; one of the broker's providers closing leaves it running for the others. */
    @Override
    public void close()
    {
        // Stopped by the shutdown hook initialize added.
    }

    /**
     * Decides a check: the brokers' and proxies' own roles pass, a role without policies is refused, and otherwise the
     * policies tell. A refusal is reported when it refuses the request itself, and an allowance always.
     */
    // The runtime is borrowed; the shutdown hook closes it.
    @SuppressWarnings("PMD.CloseResource")
    boolean decide(@Nullable String role, @Nullable AuthenticationDataSource authData, PulsarResource resource, String action,
            @Nullable String request, boolean refusals, String... accessTypes)
    {
        if (role == null || role.isEmpty()) {
            return false;
        }
        if (unchecked.contains(role)) {
            return true;
        }
        PulsarAgentRuntime running = runtime;
        if (running == null) {
            return false;
        }
        try {
            Policies policies = running.policies();
            if (!policies.enforced()) {
                return true;
            }
            Caller caller = Caller.of(role, authData);
            Policies.Verdict verdict = policies.decide(caller, resource, accessTypes);
            if (verdict.allowed() || refusals) {
                report(running, caller, resource, verdict, action, request);
            }
            else if (LOG.isDebugEnabled()) {
                LOG.debug("{} may not {} {}: {}", role, action, resource.display(), why(verdict));
            }
            return verdict.allowed();
        }
        catch (RuntimeException broken) {
            LOG.warn("GrantForge refuses {} to {} {}: {}", role, action, resource.display(), broken.getMessage());
            return false;
        }
    }

    /** Reports a check; Pulsar words its own refusal, so the log says which policy refused it. */
    private static void report(PulsarAgentRuntime running, Caller caller, PulsarResource resource, Policies.Verdict verdict, String action,
            @Nullable String request)
    {
        if (!verdict.allowed()) {
            LOG.info("GrantForge refuses {} to {} {}: {}", caller.user(), action, resource.display(), why(verdict));
        }
        running.audit(AccessEvent.builder(caller.user(), resource.display(), verdict.accessType(), verdict.allowed()).decidedBy(verdict.decision())
                .enforcedByGrantForge().clientIp(caller.clientIp()).resourceType(resource.level()).action(action).request(request).build());
    }

    /**
     * Returns why the policies refuse something, for Pulsar's own message to go with it.
     *
     * @param verdict the verdict
     * @return such as {@code GrantForge policy 7 denies it}
     */
    static String why(Policies.Verdict verdict)
    {
        Long policy = verdict.decision().policyId();
        return verdict.denied() && policy != null ? "GrantForge policy " + policy + " denies it" : "no GrantForge policy allows it";
    }

    private static CompletableFuture<Boolean> done(boolean allowed)
    {
        return CompletableFuture.completedFuture(allowed);
    }

    /** An operation's name in words: {@code RESET_CURSOR} is {@code reset cursor}. */
    static String words(String operation)
    {
        return operation.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String policyAction(boolean read, String policy)
    {
        return (read ? "read " : "write ") + words(policy) + " policy";
    }

    private static @Nullable String text(@Nullable String value)
    {
        String text = value == null ? "" : value.strip();
        return text.isEmpty() ? null : text;
    }

    @Override
    public String toString()
    {
        return "GrantForge";
    }
}
