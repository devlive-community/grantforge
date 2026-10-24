// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.AuthenticationFactory;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.common.policies.data.AuthAction;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shaded agent jar in a real broker of one Pulsar release, against a GrantForge server of the tests' own: producing
 * where a namespace's policy allows and a topic's denies, consuming through the subscriptions a policy names, a tenant's
 * and the cluster's administrators, policy items that hold for some client addresses only, granting through Pulsar
 * refused, access events, and a policy change without a restart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class PulsarAgentContract
{
    private static final String PAYMENTS = "persistent://sales/orders/payments";

    private @Nullable AgentServer server;
    private @Nullable PulsarBroker broker;

    /**
     * The release under test.
     *
     * @return such as {@code 4.2.5}
     */
    abstract String version();

    private static PulsarSnapshots policies()
    {
        return new PulsarSnapshots()
                .allow(List.of("@analysts"), List.of("produce"), "tenant", "sales", "namespace", "orders")
                .deny(List.of("alice"), List.of("produce"), "tenant", "sales", "namespace", "orders", "topic", "audit")
                .allow(List.of("alice"), List.of("consume"), "tenant", "sales", "namespace", "orders", "topic", "*", "subscription", "billing-*")
                .allow(List.of("bob"), List.of("admin_tenant"), "tenant", "sales")
                .allow(List.of("root"), List.of("admin"), "cluster", "standalone")
                .allowFrom(List.of("0.0.0.0/0"), List.of("erin"), List.of("produce"), "tenant", "public")
                .allowFrom(List.of("203.0.113.0/24"), List.of("frank"), List.of("produce"), "tenant", "public")
                .group("analysts", "alice");
    }

    @BeforeAll
    void start() throws Exception
    {
        AgentServer started = new AgentServer(policies());
        server = started;
        Testcontainers.exposeHostPorts(started.port());
        PulsarBroker running = new PulsarBroker(version(), Map.of("authorizationProvider", GrantForgePulsarAuthorizer.class.getName(),
                "grantforge.server.url", "http://host.testcontainers.internal:" + started.port() + "/", "grantforge.token.file",
                "/pulsar/conf/grantforge.token", "grantforge.instance", "broker", "grantforge.cache.dir", "/pulsar/data/grantforge",
                "grantforge.signing.key.file", "/pulsar/conf/grantforge.key", "grantforge.refresh.interval.ms", "1000",
                "grantforge.audit.flush.interval.ms", "200", "grantforge.audit.fold.window.ms", "0"))
                .file("/pulsar/conf/grantforge.token", AgentServer.TOKEN.getBytes(StandardCharsets.UTF_8))
                .file("/pulsar/conf/grantforge.key", started.publicKey().getBytes(StandardCharsets.US_ASCII));
        running.container().withCopyFileToContainer(MountableFile.forHostPath(Path.of(requireNonNull(System.getProperty("grantforge.pulsar.agent.jar"),
                "grantforge.pulsar.agent.jar"))), "/pulsar/lib/grantforge-agent-pulsar.jar");
        broker = running;
        running.start();
        try {
            prepare();
        }
        catch (AssertionError | PulsarAdminException failed) {
            String logs = running.container().getLogs();
            throw new AssertionError(failed.getMessage() + "; the broker said:\n" + logs.substring(Math.max(0, logs.length() - 20_000)), failed);
        }
    }

    private void prepare() throws Exception
    {
        try (PulsarAdmin admin = admin(PulsarBroker.SUPERUSER)) {
            eventually("the broker made its own tenant", () -> {
                try {
                    return admin.tenants().getTenants().contains("public");
                }
                catch (PulsarAdminException notYet) {
                    return false;
                }
            });
            admin.tenants().createTenant("sales", TenantInfo.builder().allowedClusters(Set.of("standalone")).build());
            admin.namespaces().createNamespace("sales/orders");
            admin.namespaces().createNamespace("sales/returns");
            admin.topics().createPartitionedTopic(PAYMENTS, 2);
        }
        eventually("the agent takes the policies", () -> produces("alice", PAYMENTS));
    }

    @AfterAll
    void stop()
    {
        if (broker != null) {
            broker.close();
        }
        if (server != null) {
            server.close();
        }
    }

    private PulsarBroker broker()
    {
        return requireNonNull(broker, "broker");
    }

    private PulsarAdmin admin(String role) throws PulsarClientException
    {
        return PulsarAdmin.builder().serviceHttpUrl(broker().webServiceUrl()).authentication(AuthenticationFactory.token(broker().token(role)))
                .requestTimeout(20, TimeUnit.SECONDS).build();
    }

    private PulsarClient client(String role) throws PulsarClientException
    {
        return PulsarClient.builder().serviceUrl(broker().serviceUrl()).authentication(AuthenticationFactory.token(broker().token(role)))
                .operationTimeout(15, TimeUnit.SECONDS).build();
    }

    private void produce(String role, String topic) throws PulsarClientException
    {
        try (PulsarClient client = client(role); Producer<byte[]> producer = client.newProducer().topic(topic).sendTimeout(15, TimeUnit.SECONDS).create()) {
            producer.send("event".getBytes(StandardCharsets.UTF_8));
        }
    }

    private boolean produces(String role, String topic)
    {
        try {
            produce(role, topic);
            return true;
        }
        catch (PulsarClientException refused) {
            return false;
        }
    }

    private void consume(String role, String topic, String subscription) throws PulsarClientException
    {
        try (PulsarClient client = client(role); Consumer<byte[]> consumer = client.newConsumer().topic(topic).subscriptionName(subscription).subscribe()) {
            assertThat(consumer.getSubscription()).isEqualTo(subscription);
        }
    }

    private static void eventually(String what, BooleanSupplier condition) throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting until " + what);
            }
            Thread.sleep(500);
        }
    }

    @Test
    void producesWhereTheNamespaceAllowsAndATopicDoesNotDeny() throws Exception
    {
        produce("alice", PAYMENTS);
        produce("alice", "persistent://sales/orders/created-by-alice");
        assertThatThrownBy(() -> produce("alice", "persistent://sales/orders/audit")).as("a denial on the topic")
                .isInstanceOf(PulsarClientException.AuthorizationException.class);
        assertThatThrownBy(() -> produce("alice", "persistent://sales/returns/refunds")).isInstanceOf(PulsarClientException.AuthorizationException.class);
        assertThatThrownBy(() -> produce("mallory", PAYMENTS)).isInstanceOf(PulsarClientException.AuthorizationException.class);
    }

    @Test
    void consumesOnlyThroughTheSubscriptionsAPolicyNames() throws Exception
    {
        consume("alice", PAYMENTS, "billing-eu");
        assertThatThrownBy(() -> consume("alice", PAYMENTS, "audit")).isInstanceOf(PulsarClientException.AuthorizationException.class);
    }

    @Test
    void administersATenantAsItsAdministrator() throws Exception
    {
        try (PulsarAdmin bob = admin("bob")) {
            bob.namespaces().createNamespace("sales/bobs");
            assertThat(bob.namespaces().getNamespaces("sales")).contains("sales/bobs", "sales/orders");
            bob.topics().createNonPartitionedTopic("persistent://sales/bobs/plans");
        }
        try (PulsarAdmin alice = admin("alice")) {
            assertThatThrownBy(() -> alice.namespaces().createNamespace("sales/alices")).isInstanceOf(PulsarAdminException.class);
            assertThatThrownBy(() -> alice.topics().terminateTopic("persistent://sales/bobs/plans")).isInstanceOf(PulsarAdminException.class);
        }
    }

    @Test
    void makesTheClustersAdministratorsSuperusers() throws Exception
    {
        try (PulsarAdmin root = admin("root")) {
            root.tenants().createTenant("marketing", TenantInfo.builder().allowedClusters(Set.of("standalone")).build());
            assertThat(root.tenants().getTenants()).contains("marketing", "sales");
            root.namespaces().createNamespace("marketing/campaigns");
        }
        try (PulsarAdmin bob = admin("bob")) {
            assertThatThrownBy(() -> bob.tenants().createTenant("bobs", TenantInfo.builder().allowedClusters(Set.of("standalone")).build()))
                    .isInstanceOf(PulsarAdminException.class);
        }
        produce("root", "persistent://marketing/campaigns/launch");
    }

    @Test
    void holdsForTheClientAddressesAPolicyNames() throws Exception
    {
        produce("erin", "persistent://public/default/anywhere");
        assertThatThrownBy(() -> produce("frank", "persistent://public/default/elsewhere")).isInstanceOf(PulsarClientException.AuthorizationException.class);
    }

    @Test
    void refusesGrantingThroughPulsar() throws Exception
    {
        try (PulsarAdmin admin = admin(PulsarBroker.SUPERUSER)) {
            assertThatThrownBy(() -> admin.namespaces().grantPermissionOnNamespace("sales/orders", "mallory", Set.of(AuthAction.produce)))
                    .isInstanceOf(PulsarAdminException.class).hasMessageContaining("GrantForge");
        }
    }

    @Test
    void appliesAPolicyChangeWithoutARestart() throws Exception
    {
        AgentServer running = requireNonNull(server, "server");
        assertThat(produces("grace", "persistent://sales/returns/refunds")).isFalse();
        running.publish(policies().allow(List.of("grace"), List.of("produce"), "tenant", "sales", "namespace", "returns"));
        eventually("grace may produce to returns", () -> produces("grace", "persistent://sales/returns/refunds"));
        running.publish(policies());
        eventually("grace may no longer", () -> !produces("grace", "persistent://sales/returns/refunds"));
    }

    @Test
    void reportsAccessesToTheServer() throws Exception
    {
        assertThat(produces("alice", "persistent://sales/orders/audit")).isFalse();
        AgentServer running = requireNonNull(server, "server");
        eventually("the refusal arrives", () -> running.events().stream().anyMatch(PulsarAgentContract::refused));
        JsonNode event = running.events().stream().filter(PulsarAgentContract::refused).findFirst().orElseThrow();
        assertThat(event.path("user").asText()).isEqualTo("alice");
        assertThat(event.path("accessType").asText()).isEqualTo("produce");
        assertThat(event.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(event.path("clientIp").asText()).isNotEmpty();
        assertThat(event.path("resourceType").asText()).isEqualTo("topic");
    }

    private static boolean refused(JsonNode event)
    {
        return "persistent://sales/orders/audit".equals(event.path("resource").asText()) && "DENIED".equals(event.path("outcome").asText());
    }
}
