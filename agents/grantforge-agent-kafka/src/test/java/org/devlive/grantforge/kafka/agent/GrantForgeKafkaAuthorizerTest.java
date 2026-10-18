// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

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
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.server.authorizer.AclCreateResult;
import org.apache.kafka.server.authorizer.AclDeleteResult;
import org.apache.kafka.server.authorizer.Action;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.authorizer.AuthorizationResult;
import org.apache.kafka.server.authorizer.AuthorizerServerInfo;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrantForgeKafkaAuthorizerTest
{
    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    @TempDir
    Path temporary;

    private final GrantForgeAgent agent = mock(GrantForgeAgent.class);
    private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();
    private final List<AccessEvent> events = new CopyOnWriteArrayList<>();
    private final Function<AgentSettings, GrantForgeAgent> factory = settings -> agent;
    private final KafkaAgentRuntime runtime = new KafkaAgentRuntime(factory);
    private final GrantForgeKafkaAuthorizer authorizer = new GrantForgeKafkaAuthorizer(runtime, Clock.fixed(NOW, ZoneOffset.UTC), () -> 0L);

    @BeforeEach
    void servesTheSnapshotAndCollectsEvents()
    {
        when(agent.snapshot()).thenAnswer(invocation -> snapshot.get());
        when(agent.record(any(AccessEvent.class))).thenAnswer(invocation -> events.add(invocation.getArgument(0)));
    }

    @AfterEach
    void closes()
    {
        authorizer.close();
    }

    private Map<String, Object> settings(String... values) throws Exception
    {
        Map<String, Object> configs = new HashMap<>(KafkaAgentRuntimeTest.settings(temporary));
        configs.put("super.users", " User:admin ; ;User:ANONYMOUS");
        configs.put("grantforge.audit.fold.window.ms", "0");
        configs.put("broker.id", 1);
        configs.put("grantforge.nothing", null);
        for (int index = 0; index < values.length; index += 2) {
            configs.put(values[index], values[index + 1]);
        }
        return configs;
    }

    private void configure(String... values) throws Exception
    {
        authorizer.configure(settings(values));
    }

    private static KafkaSnapshots policies()
    {
        return new KafkaSnapshots()
                .allow(List.of("alice"), List.of("write"), "topic", "orders")
                .allow(List.of("@analysts"), List.of("read"), "topic", "orders")
                .allow(List.of("alice"), List.of("read"), "group", "billing")
                .deny(List.of("mallory"), List.of("write"), "topic", "*")
                .group("analysts", "bob");
    }

    private static AuthorizableRequestContext context(String user)
    {
        return context(new KafkaPrincipal(KafkaPrincipal.USER_TYPE, user), 0);
    }

    private static AuthorizableRequestContext context(KafkaPrincipal principal, int requestType)
    {
        AuthorizableRequestContext context = mock(AuthorizableRequestContext.class);
        when(context.principal()).thenReturn(principal);
        when(context.clientAddress()).thenReturn(InetAddress.getLoopbackAddress());
        when(context.requestType()).thenReturn(requestType);
        return context;
    }

    private static Action action(AclOperation operation, ResourceType type, String name)
    {
        return new Action(operation, new ResourcePattern(type, name, PatternType.LITERAL), 1, true, true);
    }

    private List<AuthorizationResult> authorize(String user, Action... actions)
    {
        return authorizer.authorize(context(user), List.of(actions));
    }

    @Test
    void decidesEachActionByThePoliciesAndAuditsIt() throws Exception
    {
        configure();
        snapshot.set(policies().snapshot());
        assertThat(authorize("alice", action(AclOperation.WRITE, ResourceType.TOPIC, "orders"), action(AclOperation.DESCRIBE,
                ResourceType.TOPIC, "orders"), action(AclOperation.READ, ResourceType.TOPIC, "orders"), action(AclOperation.READ,
                ResourceType.GROUP, "billing"))).containsExactly(AuthorizationResult.ALLOWED, AuthorizationResult.ALLOWED,
                AuthorizationResult.DENIED, AuthorizationResult.ALLOWED);
        assertThat(authorize("bob", action(AclOperation.READ, ResourceType.TOPIC, "orders"))).as("by the snapshot's groups")
                .containsExactly(AuthorizationResult.ALLOWED);
        assertThat(authorize("mallory", action(AclOperation.WRITE, ResourceType.TOPIC, "orders"))).containsExactly(AuthorizationResult.DENIED);
        assertThat(events).hasSize(6);
        Map<String, @Nullable Object> written = EventFields.of(events.get(0));
        assertThat(written).containsEntry("user", "alice").containsEntry("resource", "orders").containsEntry("resourceType", "topic")
                .containsEntry("accessType", "write").containsEntry("outcome", "ALLOWED").containsEntry("policyId", "1")
                .containsEntry("enforcer", "GRANTFORGE").containsEntry("action", "PRODUCE")
                .containsEntry("clientIp", InetAddress.getLoopbackAddress().getHostAddress());
        Map<String, @Nullable Object> refused = EventFields.of(events.get(2));
        assertThat(refused).containsEntry("accessType", "read").containsEntry("outcome", "DENIED").containsEntry("policyId", null)
                .containsEntry("enforcer", "GRANTFORGE");
        assertThat(EventFields.of(events.get(5))).containsEntry("user", "mallory").containsEntry("policyId", "4");
    }

    @Test
    void auditsOnlyWhatKafkaAsksToLog() throws Exception
    {
        configure();
        snapshot.set(policies().snapshot());
        ResourcePattern orders = new ResourcePattern(ResourceType.TOPIC, "orders", PatternType.LITERAL);
        ResourcePattern payments = new ResourcePattern(ResourceType.TOPIC, "payments", PatternType.LITERAL);
        assertThat(authorizer.authorize(context("alice"), List.of(new Action(AclOperation.DESCRIBE, orders, 1, false, true),
                new Action(AclOperation.DESCRIBE, payments, 1, true, false)))).containsExactly(AuthorizationResult.ALLOWED,
                AuthorizationResult.DENIED);
        assertThat(events).isEmpty();
    }

    @Test
    void refusesWhatThePluginCannotName() throws Exception
    {
        configure();
        snapshot.set(policies().snapshot());
        Action prefixed = new Action(AclOperation.WRITE, new ResourcePattern(ResourceType.TOPIC, "ord", PatternType.PREFIXED), 1, true, true);
        assertThat(authorize("alice", prefixed, action(AclOperation.UNKNOWN, ResourceType.TOPIC, "orders"), action(AclOperation.WRITE,
                ResourceType.UNKNOWN, "orders"))).containsOnly(AuthorizationResult.DENIED);
        assertThat(events).extracting(event -> EventFields.of(event).get("accessType")).containsExactly("write", "UNKNOWN", "write");
        assertThat(events).extracting(event -> EventFields.of(event).get("resourceType")).containsExactly("topic", "topic", "UNKNOWN");
        assertThat(EventFields.of(events.get(0))).containsEntry("enforcer", "GRANTFORGE").containsEntry("policyVersion", null);
    }

    @Test
    void letsSuperUsersDoEverythingUnaudited() throws Exception
    {
        configure();
        assertThat(authorizer.authorize(context(new KafkaPrincipal(KafkaPrincipal.USER_TYPE, "admin"), 0), List.of(action(AclOperation.ALTER,
                ResourceType.CLUSTER, "kafka-cluster"), action(AclOperation.DELETE, ResourceType.TOPIC, "orders"))))
                .containsOnly(AuthorizationResult.ALLOWED);
        assertThat(authorizer.authorizeByResourceType(context("ANONYMOUS"), AclOperation.WRITE, ResourceType.TOPIC))
                .isEqualTo(AuthorizationResult.ALLOWED);
        assertThat(authorizer.authorize(context(new KafkaPrincipal("Group", "admin"), 0), List.of(action(AclOperation.ALTER,
                ResourceType.CLUSTER, "kafka-cluster")))).as("another principal type of the same name").containsOnly(AuthorizationResult.DENIED);
        assertThat(events).hasSize(1);
        assertThat(GrantForgeKafkaAuthorizer.superUsers(" User:a;User:b ;;")).containsExactlyInAnyOrder("User:a", "User:b");
        assertThat(GrantForgeKafkaAuthorizer.superUsers("")).isEmpty();
    }

    @Test
    void refusesWithoutASnapshotAndLeavesAServiceNotInUseUnchecked() throws Exception
    {
        configure();
        Action write = action(AclOperation.WRITE, ResourceType.TOPIC, "orders");
        assertThat(authorize("alice", write)).containsExactly(AuthorizationResult.DENIED);
        assertThat(EventFields.of(events.get(0))).containsEntry("outcome", "DENIED");
        assertThat(authorizer.authorizeByResourceType(context("alice"), AclOperation.WRITE, ResourceType.TOPIC))
                .isEqualTo(AuthorizationResult.DENIED);
        snapshot.set(policies().disabled().snapshot());
        assertThat(authorize("mallory", write)).containsExactly(AuthorizationResult.ALLOWED);
        assertThat(authorizer.authorizeByResourceType(context("mallory"), AclOperation.WRITE, ResourceType.TOPIC))
                .isEqualTo(AuthorizationResult.ALLOWED);
        assertThat(authorizer.acls(AclBindingFilter.ANY)).isEmpty();
        assertThat(authorizer.aclCount()).isZero();
        assertThat(events).hasSize(1);
    }

    @Test
    void refusesEverythingWhenTheTokenIsBoundToAnotherServiceType() throws Exception
    {
        configure();
        snapshot.set(policies().serviceType("hdfs").snapshot());
        assertThat(authorize("alice", action(AclOperation.WRITE, ResourceType.TOPIC, "orders"))).containsExactly(AuthorizationResult.DENIED);
        assertThat(authorize("alice", action(AclOperation.WRITE, ResourceType.TOPIC, "orders"))).containsExactly(AuthorizationResult.DENIED);
        assertThat(authorizer.acls(AclBindingFilter.ANY)).isEmpty();
    }

    @Test
    void keepsDecisionsOfTheSnapshotAndAppliesAPolicyChangeAtOnce() throws Exception
    {
        configure();
        Snapshot first = spy(policies().snapshot());
        snapshot.set(first);
        Action write = action(AclOperation.WRITE, ResourceType.TOPIC, "orders");
        for (int round = 0; round < 3; round++) {
            assertThat(authorize("alice", write)).containsExactly(AuthorizationResult.ALLOWED);
        }
        verify(first, times(1)).decide(any(AccessRequest.class));
        snapshot.set(new KafkaSnapshots().version(2).snapshot());
        assertThat(authorize("alice", write)).containsExactly(AuthorizationResult.DENIED);
    }

    @Test
    void decidesEveryRequestWithoutACache() throws Exception
    {
        configure("grantforge.decision.cache.size", "0");
        Snapshot spied = spy(policies().snapshot());
        snapshot.set(spied);
        authorize("alice", action(AclOperation.WRITE, ResourceType.TOPIC, "orders"));
        authorize("alice", action(AclOperation.WRITE, ResourceType.TOPIC, "orders"));
        verify(spied, times(2)).decide(any(AccessRequest.class));
    }

    @Test
    void refusesWhatTheEngineCannotDecide() throws Exception
    {
        configure();
        Snapshot broken = spy(policies().snapshot());
        doThrow(new IllegalArgumentException("broken")).when(broken).decide(any(AccessRequest.class));
        doThrow(new IllegalArgumentException("broken")).when(broken).decideAnyResource(any(AccessRequest.class));
        snapshot.set(broken);
        assertThat(authorize("alice", action(AclOperation.WRITE, ResourceType.TOPIC, "orders"))).containsExactly(AuthorizationResult.DENIED);
        assertThat(authorizer.authorizeByResourceType(context("alice"), AclOperation.WRITE, ResourceType.TOPIC))
                .isEqualTo(AuthorizationResult.DENIED);
    }

    @Test
    void answersWhetherAUserMayActOnSomeResourceOfAType() throws Exception
    {
        configure();
        snapshot.set(policies().snapshot());
        assertThat(authorizer.authorizeByResourceType(context("alice"), AclOperation.WRITE, ResourceType.TOPIC))
                .as("an idempotent producer may start").isEqualTo(AuthorizationResult.ALLOWED);
        assertThat(authorizer.authorizeByResourceType(context("alice"), AclOperation.WRITE, ResourceType.TOPIC))
                .isEqualTo(AuthorizationResult.ALLOWED);
        assertThat(authorizer.authorizeByResourceType(context("bob"), AclOperation.WRITE, ResourceType.TOPIC))
                .isEqualTo(AuthorizationResult.DENIED);
        assertThat(authorizer.authorizeByResourceType(context("bob"), AclOperation.READ, ResourceType.TOPIC))
                .isEqualTo(AuthorizationResult.ALLOWED);
        assertThat(authorizer.authorizeByResourceType(context("mallory"), AclOperation.WRITE, ResourceType.TOPIC))
                .as("denied every topic").isEqualTo(AuthorizationResult.DENIED);
        assertThat(authorizer.authorizeByResourceType(context("alice"), AclOperation.WRITE, ResourceType.GROUP))
                .isEqualTo(AuthorizationResult.DENIED);
        assertThatThrownBy(() -> authorizer.authorizeByResourceType(context("alice"), AclOperation.ANY, ResourceType.TOPIC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> authorizer.authorizeByResourceType(context("alice"), AclOperation.WRITE, ResourceType.ANY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> authorizer.authorizeByResourceType(context("alice"), AclOperation.UNKNOWN, ResourceType.TOPIC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> authorizer.authorizeByResourceType(context("alice"), AclOperation.WRITE, ResourceType.UNKNOWN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(events).as("Kafka's question of a type is no access").isEmpty();
    }

    @Test
    void showsThePoliciesAsReadOnlyAcls() throws Exception
    {
        configure();
        snapshot.set(policies().snapshot());
        assertThat(authorizer.acls(AclBindingFilter.ANY)).hasSize(4);
        assertThat(authorizer.aclCount()).isEqualTo(4);
        AuthorizableRequestContext admin = context("admin");
        AclBinding binding = authorizer.acls(AclBindingFilter.ANY).iterator().next();
        List<? extends CompletionStage<AclCreateResult>> created = authorizer.createAcls(admin, List.of(binding, binding));
        assertThat(created).hasSize(2);
        for (CompletionStage<AclCreateResult> result : created) {
            assertThat(result.toCompletableFuture().getNow(null).exception()).get().isInstanceOf(InvalidRequestException.class)
                    .extracting(Throwable::getMessage).asString().contains("GrantForge policies");
        }
        List<? extends CompletionStage<AclDeleteResult>> deleted = authorizer.deleteAcls(admin, List.of(AclBindingFilter.ANY));
        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).toCompletableFuture().getNow(null).exception()).get().isInstanceOf(InvalidRequestException.class);
        assertThat(authorizer.acls(AclBindingFilter.ANY)).as("nothing changed").hasSize(4);
        snapshot.set(null);
        assertThat(authorizer.aclCount()).isZero();
    }

    private static AuthorizerServerInfo server()
    {
        AuthorizerServerInfo info = mock(AuthorizerServerInfo.class);
        Endpoint external = new Endpoint("EXTERNAL", SecurityProtocol.SASL_PLAINTEXT, "localhost", 9092);
        Endpoint controller = new Endpoint("CONTROLLER", SecurityProtocol.PLAINTEXT, "localhost", 9093);
        when(info.endpoints()).thenReturn(List.of(external, controller));
        when(info.earlyStartListeners()).thenReturn(List.of("CONTROLLER"));
        return info;
    }

    private static CompletableFuture<Void> future(Map<Endpoint, ? extends CompletionStage<Void>> futures, String listener)
    {
        return futures.entrySet().stream().filter(entry -> entry.getKey().listenerName().orElse("").equals(listener)).findFirst()
                .orElseThrow().getValue().toCompletableFuture();
    }

    @Test
    void opensListenersOnceTheFirstSnapshotIsThere() throws Exception
    {
        configure();
        Map<Endpoint, ? extends CompletionStage<Void>> futures = authorizer.start(server());
        assertThat(future(futures, "CONTROLLER")).as("early listeners start at once").isCompleted();
        CompletableFuture<Void> external = future(futures, "EXTERNAL");
        TimeUnit.MILLISECONDS.sleep(300);
        assertThat(external).isNotDone();
        snapshot.set(policies().snapshot());
        external.get(5, TimeUnit.SECONDS);
        assertThat(authorizer.start(server()).values()).as("a snapshot is there").allMatch(future -> future.toCompletableFuture().isDone());
    }

    @Test
    void opensListenersAfterTheStartTimeoutWithoutASnapshot() throws Exception
    {
        configure("grantforge.start.timeout.ms", "200");
        CompletableFuture<Void> external = future(authorizer.start(server()), "EXTERNAL");
        external.get(5, TimeUnit.SECONDS);
        assertThat(authorize("alice", action(AclOperation.WRITE, ResourceType.TOPIC, "orders"))).containsExactly(AuthorizationResult.DENIED);
    }

    @Test
    void foldsIdenticalAllowedAccessesAndReportsRefusalsAtOnce() throws Exception
    {
        configure("grantforge.audit.fold.window.ms", "60000");
        snapshot.set(policies().snapshot());
        Action write = action(AclOperation.WRITE, ResourceType.TOPIC, "orders");
        for (int round = 0; round < 5; round++) {
            authorize("alice", write);
        }
        authorize("mallory", write);
        assertThat(events).hasSize(1);
        assertThat(EventFields.of(events.get(0))).containsEntry("user", "mallory");
        authorizer.close();
        assertThat(events).hasSize(2);
        assertThat(EventFields.of(events.get(1))).containsEntry("user", "alice").containsEntry("count", 5);
    }

    @Test
    void refusesInvalidSettings() throws Exception
    {
        for (String[] invalid : List.of(new String[] {"grantforge.decision.cache.size", "-1"}, new String[] {"grantforge.decision.cache.ttl.ms",
                "0"}, new String[] {"grantforge.audit.fold.window.ms", "x"}, new String[] {"grantforge.start.timeout.ms", "600001"},
                new String[] {"grantforge.token.file", temporary.resolve("missing").toString()})) {
            GrantForgeKafkaAuthorizer refused = new GrantForgeKafkaAuthorizer(runtime, Clock.systemUTC(), () -> 0L);
            assertThatThrownBy(() -> refused.configure(settings(invalid))).isInstanceOf(ConfigException.class)
                    .hasMessageContaining("GrantForge cannot start the Kafka agent");
            refused.close();
        }
        assertThatThrownBy(runtime::snapshot).as("no agent was started").isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givesBackTheAgentWhenClosed() throws Exception
    {
        configure();
        GrantForgeKafkaAuthorizer controller = new GrantForgeKafkaAuthorizer(runtime, Clock.systemUTC(), () -> 0L);
        controller.configure(settings());
        controller.close();
        assertThat(runtime.snapshot()).as("the broker's authorizer still holds the agent").isNull();
        authorizer.close();
        authorizer.close();
        verify(agent).close();
        assertThat(authorize("alice", action(AclOperation.WRITE, ResourceType.TOPIC, "orders"))).containsExactly(AuthorizationResult.DENIED);
        assertThat(authorizer.start(server()).values()).as("a stopped agent holds nothing back")
                .allMatch(future -> future.toCompletableFuture().isDone());
    }

    @Test
    void knowsItsVersion()
    {
        assertThat(GrantForgeKafkaAuthorizer.version()).isNotBlank().doesNotContain("${");
    }
}
