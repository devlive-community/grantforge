// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import org.apache.pulsar.broker.authentication.AuthenticationDataSource;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.naming.TopicName;
import org.apache.pulsar.common.policies.data.AuthAction;
import org.apache.pulsar.common.policies.data.BrokerOperation;
import org.apache.pulsar.common.policies.data.ClusterOperation;
import org.apache.pulsar.common.policies.data.NamespaceOperation;
import org.apache.pulsar.common.policies.data.PolicyName;
import org.apache.pulsar.common.policies.data.PolicyOperation;
import org.apache.pulsar.common.policies.data.TenantOperation;
import org.apache.pulsar.common.policies.data.TopicOperation;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrantForgePulsarAuthorizerTest
{
    private static final TopicName PAYMENTS = TopicName.get("persistent://sales/orders/payments-partition-1");
    private static final NamespaceName ORDERS = NamespaceName.get("sales/orders");

    @TempDir
    Path temporary;

    private final TestRuntime test = new TestRuntime(new PulsarSnapshots()
            .allow(List.of("alice"), List.of("produce"), "tenant", "sales", "namespace", "orders")
            .allow(List.of("alice"), List.of("consume"), "tenant", "sales", "namespace", "orders", "topic", "payments", "subscription", "billing")
            .allow(List.of("alice"), List.of("functions", "packages"), "tenant", "sales", "namespace", "orders")
            .allow(List.of("bob"), List.of("admin_tenant"), "tenant", "sales")
            .allow(List.of("root"), List.of("admin"), "cluster", "west")
            .allowFrom(List.of("10.0.0.0/8"), List.of("carol"), List.of("produce"), "tenant", "sales")
            .deny(List.of("bob"), List.of("produce"), "tenant", "sales", "namespace", "orders", "topic", "audit")
            .snapshot());
    private final GrantForgePulsarAuthorizer authorizer = new GrantForgePulsarAuthorizer(test.runtime(), Set.of("broker", "proxy"), "west");

    private static boolean result(CompletableFuture<Boolean> future)
    {
        return future.join();
    }

    private static AuthenticationDataSource from(String address, @Nullable String subscription)
    {
        AuthenticationDataSource data = mock(AuthenticationDataSource.class);
        when(data.hasDataFromPeer()).thenReturn(true);
        when(data.getPeerAddress()).thenReturn(new InetSocketAddress(address, 6650));
        when(data.hasSubscription()).thenReturn(subscription != null && !subscription.isEmpty());
        when(data.getSubscription()).thenReturn(subscription);
        return data;
    }

    private Map<String, Object> lastEvent()
    {
        List<AccessEvent> events = test.events();
        assertThat(events).isNotEmpty();
        return EventFields.of(events.get(events.size() - 1));
    }

    @Test
    void decidesProducingAtEveryLevelOfTheTopic()
    {
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, "alice", from("10.1.1.1", null)))).isTrue();
        Map<String, Object> event = lastEvent();
        assertThat(event).containsEntry("user", "alice").containsEntry("resource", "persistent://sales/orders/payments").containsEntry("outcome", "ALLOWED")
                .containsEntry("action", "produce").containsEntry("clientIp", "10.1.1.1").containsEntry("resourceType", "topic");
        assertThat(result(authorizer.canProduceAsync(TopicName.get("sales/orders/audit"), "bob", null))).isFalse();
        assertThat(lastEvent()).containsEntry("outcome", "DENIED").containsEntry("user", "bob");
        assertThat(result(authorizer.canProduceAsync(TopicName.get("sales/returns/refunds"), "alice", null))).isFalse();
    }

    @Test
    void decidesConsumingThroughTheSubscription()
    {
        assertThat(result(authorizer.canConsumeAsync(PAYMENTS, "alice", null, "billing"))).isTrue();
        assertThat(lastEvent()).containsEntry("resource", "persistent://sales/orders/payments subscription billing")
                .containsEntry("resourceType", "subscription");
        assertThat(result(authorizer.canConsumeAsync(PAYMENTS, "alice", null, "audit"))).isFalse();
        assertThat(result(authorizer.canConsumeAsync(PAYMENTS, "alice", null, " "))).isFalse();
        assertThat(result(authorizer.allowTopicOperationAsync(PAYMENTS, "alice", TopicOperation.RESET_CURSOR, from("10.0.0.1", "billing")))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "reset cursor");
        assertThat(result(authorizer.allowTopicOperationAsync(PAYMENTS, "alice", TopicOperation.SUBSCRIBE, from("10.0.0.1", null)))).isFalse();
        assertThat(result(authorizer.allowTopicOperationAsync(PAYMENTS, "alice", TopicOperation.CONSUME, null))).isFalse();
    }

    @Test
    void looksUpForWhoProducesOrConsumes()
    {
        assertThat(result(authorizer.canLookupAsync(PAYMENTS, "alice", null))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "lookup").containsEntry("accessType", "produce");
        assertThat(result(authorizer.allowTopicOperationAsync(PAYMENTS, "alice", TopicOperation.GET_STATS, null))).isTrue();
        assertThat(result(authorizer.allowTopicOperationAsync(PAYMENTS, "alice", TopicOperation.PRODUCE, null))).isTrue();
        assertThat(result(authorizer.canLookupAsync(PAYMENTS, "dave", null))).isFalse();
    }

    @Test
    void leavesTopicAdministrationToItsAdministrators()
    {
        assertThat(result(authorizer.allowTopicOperationAsync(PAYMENTS, "alice", TopicOperation.TERMINATE, null))).isFalse();
        assertThat(result(authorizer.allowTopicOperationAsync(PAYMENTS, "bob", TopicOperation.TERMINATE, null))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "terminate").containsEntry("accessType", "admin_topic");
        assertThat(result(authorizer.allowTopicPolicyOperationAsync(PAYMENTS, "alice", PolicyName.RETENTION, PolicyOperation.READ, null))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "read retention policy");
        assertThat(result(authorizer.allowTopicPolicyOperationAsync(PAYMENTS, "alice", PolicyName.RETENTION, PolicyOperation.WRITE, null))).isFalse();
        assertThat(result(authorizer.allowTopicPolicyOperationAsync(PAYMENTS, "bob", PolicyName.RETENTION, PolicyOperation.WRITE, null))).isTrue();
    }

    @Test
    void decidesNamespacesOperations()
    {
        assertThat(result(authorizer.allowNamespaceOperationAsync(ORDERS, "alice", NamespaceOperation.GET_TOPICS, null))).isTrue();
        assertThat(result(authorizer.allowNamespaceOperationAsync(ORDERS, "alice", NamespaceOperation.PACKAGES, null))).isTrue();
        assertThat(result(authorizer.allowNamespaceOperationAsync(ORDERS, "alice", NamespaceOperation.CLEAR_BACKLOG, null))).isFalse();
        assertThat(result(authorizer.allowNamespaceOperationAsync(ORDERS, "alice", NamespaceOperation.CREATE_TOPIC, null))).isFalse();
        assertThat(result(authorizer.allowNamespaceOperationAsync(ORDERS, "bob", NamespaceOperation.CREATE_TOPIC, null))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "create topic").containsEntry("resource", "namespace sales/orders");
        assertThat(result(authorizer.allowNamespacePolicyOperationAsync(ORDERS, PolicyName.BACKLOG, PolicyOperation.READ, "alice", null))).isTrue();
        assertThat(result(authorizer.allowNamespacePolicyOperationAsync(ORDERS, PolicyName.BACKLOG, PolicyOperation.WRITE, "alice", null))).isFalse();
        assertThat(result(authorizer.allowNamespacePolicyOperationAsync(ORDERS, PolicyName.BACKLOG, PolicyOperation.WRITE, "bob", null))).isTrue();
    }

    @Test
    void decidesFunctionsSourcesAndSinks()
    {
        assertThat(result(authorizer.allowFunctionOpsAsync(ORDERS, "alice", null))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "functions").containsEntry("resourceType", "namespace");
        assertThat(result(authorizer.allowSourceOpsAsync(ORDERS, "alice", null))).isFalse();
        assertThat(result(authorizer.allowSinkOpsAsync(ORDERS, "alice", null))).isFalse();
        assertThat(result(authorizer.allowSinkOpsAsync(ORDERS, "bob", null))).isTrue();
    }

    @Test
    void decidesTenantsAndTheirAdministrators()
    {
        assertThat(result(authorizer.allowTenantOperationAsync("sales", "bob", TenantOperation.CREATE_NAMESPACE, null))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "create namespace").containsEntry("resource", "tenant sales");
        assertThat(result(authorizer.allowTenantOperationAsync("sales", "bob", TenantOperation.DELETE_TENANT, null))).isFalse();
        assertThat(lastEvent()).containsEntry("resource", "cluster west").containsEntry("request", "tenant sales");
        assertThat(result(authorizer.allowTenantOperationAsync("sales", "root", TenantOperation.DELETE_TENANT, null))).isTrue();
        int before = test.events().size();
        assertThat(result(authorizer.isTenantAdmin("sales", "bob", null, null))).isTrue();
        assertThat(result(authorizer.isTenantAdmin("hr", "bob", null, null))).isFalse();
        assertThat(test.events()).as("only the allowance is reported").hasSize(before + 1);
    }

    @Test
    void makesTheClustersAdministratorsSuperusers()
    {
        int before = test.events().size();
        assertThat(result(authorizer.isSuperUser("alice", null, null))).isFalse();
        assertThat(test.events()).as("a refusal of the question is not reported").hasSize(before);
        assertThat(result(authorizer.isSuperUser("root", null, null))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "administer cluster").containsEntry("resource", "cluster west");
        assertThat(result(authorizer.allowClusterOperationAsync("west", ClusterOperation.UPDATE_CLUSTER, "root", null))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "update cluster");
        assertThat(result(authorizer.allowClusterOperationAsync("east", ClusterOperation.UPDATE_CLUSTER, "root", null))).isFalse();
        assertThat(result(authorizer.allowClusterOperationAsync(null, ClusterOperation.LIST_CLUSTERS, "root", null))).isTrue();
        assertThat(result(authorizer.allowBrokerOperationAsync("west", "broker-1:8080", BrokerOperation.HEALTH_CHECK, "root", null))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "health check").containsEntry("request", "broker broker-1:8080");
        assertThat(result(authorizer.allowBrokerOperationAsync("west", null, BrokerOperation.SHUTDOWN, "alice", null))).isFalse();
        assertThat(result(authorizer.allowClusterPolicyOperationAsync("west", "root", PolicyName.ALL, PolicyOperation.WRITE, null))).isTrue();
        assertThat(lastEvent()).containsEntry("action", "write all policy");
        assertThat(result(authorizer.allowClusterPolicyOperationAsync(" ", "alice", PolicyName.ALL, PolicyOperation.READ, null))).isFalse();
    }

    @Test
    void holdsForTheClientsAddress()
    {
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, "carol", from("10.9.9.9", null)))).isTrue();
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, "carol", from("203.0.113.5", null)))).isFalse();
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, "carol", null))).isFalse();
    }

    @Test
    void leavesTheBrokersAndProxiesUncheckedAndRefusesTheAnonymous()
    {
        int before = test.events().size();
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, "broker", null))).isTrue();
        assertThat(result(authorizer.isSuperUser("proxy", null, null))).isTrue();
        assertThat(test.events()).hasSize(before);
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, null, null))).isFalse();
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, "", null))).isFalse();
    }

    @Test
    void refusesEverythingWithoutPoliciesAndNothingWhileTheServiceIsNotInUse()
    {
        test.snapshot(null);
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, "alice", null))).isFalse();
        test.snapshot(new PulsarSnapshots().disabled().snapshot());
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, "dave", null))).isTrue();
        assertThat(result(new GrantForgePulsarAuthorizer().canProduceAsync(PAYMENTS, "alice", null))).isFalse();
    }

    @Test
    void refusesWhenDecidingBreaks()
    {
        Snapshot broken = mock(Snapshot.class);
        when(broken.serviceType()).thenReturn("pulsar");
        when(broken.serviceEnabled()).thenThrow(new IllegalStateException("corrupt"));
        test.snapshot(broken);
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, "alice", null))).isFalse();
        doReturn(true).when(broken).serviceEnabled();
        when(broken.decide(any())).thenThrow(new IllegalStateException("corrupt"));
        assertThat(result(authorizer.canProduceAsync(PAYMENTS, "alice", null))).isFalse();
    }

    @Test
    void refusesGrantingThroughPulsar()
    {
        for (CompletableFuture<Void> grant : List.of(authorizer.grantPermissionAsync(ORDERS, Set.of(AuthAction.produce), "alice", ""),
                authorizer.grantPermissionAsync(PAYMENTS, Set.of(AuthAction.consume), "alice", ""),
                authorizer.grantSubscriptionPermissionAsync(ORDERS, "billing", Set.of("alice"), ""),
                authorizer.revokeSubscriptionPermissionAsync(ORDERS, "billing", "alice", ""))) {
            assertThatThrownBy(grant::get).isInstanceOf(ExecutionException.class).hasMessageContaining("grant it in GrantForge");
        }
    }

    @Test
    void readsItsVersion() throws IOException
    {
        assertThat(GrantForgePulsarAuthorizer.version()).isNotBlank();
        assertThat(GrantForgePulsarAuthorizer.version(new ByteArrayInputStream("grantforge.version= 1.2 \n".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("1.2");
        assertThatThrownBy(() -> GrantForgePulsarAuthorizer.version(null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> GrantForgePulsarAuthorizer.version(new ByteArrayInputStream("other=1".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IOException.class);
    }

    @Test
    void namesWhyItRefusesAndOperationsInWords()
    {
        assertThat(GrantForgePulsarAuthorizer.words("GET_REPLICATED_SUBSCRIPTION_STATUS")).isEqualTo("get replicated subscription status");
        assertThat(authorizer).hasToString("GrantForge");
        authorizer.close();
    }

    @Test
    void startsOneAgentForAllOfABrokersProviders() throws IOException
    {
        Properties properties = new Properties();
        GrantForgePulsarAuthorizer missing = new GrantForgePulsarAuthorizer();
        assertThatThrownBy(() -> missing.initialize(Set.of("broker"), Set.of("proxy"), "west", properties)).isInstanceOf(IOException.class)
                .hasMessageContaining("grantforge.");
        TestRuntime.settings(temporary).forEach(properties::setProperty);
        properties.setProperty("grantforge.server.url", "http://127.0.0.1:1/");
        properties.setProperty("grantforge.connect.timeout.ms", "100");
        properties.setProperty("brokerServicePort", "6650");
        GrantForgePulsarAuthorizer first = new GrantForgePulsarAuthorizer();
        first.initialize(Set.of("broker"), Set.of("proxy"), "west", properties);
        GrantForgePulsarAuthorizer second = new GrantForgePulsarAuthorizer();
        second.initialize(Set.of("broker"), Set.of("proxy"), "west", properties);
        assertThat(result(first.canProduceAsync(PAYMENTS, "broker", null))).isTrue();
        assertThat(result(second.isSuperUser("proxy", null, null))).isTrue();
        assertThat(result(first.canProduceAsync(PAYMENTS, "alice", null))).as("no policies yet").isFalse();
    }
}
