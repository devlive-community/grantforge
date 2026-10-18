// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.errors.ClusterAuthorizationException;
import org.apache.kafka.common.errors.GroupAuthorizationException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.Testcontainers;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.zip.ZipFile;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shaded agent inside a real Kafka broker and controller of one release, taking signed policies from a GrantForge
 * server: produce and consume allowed and refused per user and group, an idempotent producer that may only write to a
 * topic, topic listings filtered, the policies shown as read-only ACLs, repeated accesses folded into one audit event,
 * and a policy change applied without a restart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class KafkaAgentContract
{
    private static final String PLAIN = "org.apache.kafka.common.security.plain.PlainLoginModule required";

    private @Nullable AgentServer server;
    private @Nullable KafkaBroker broker;

    /**
     * The image under test.
     *
     * @return such as {@code apache/kafka:4.3.1}
     */
    abstract String image();

    private static KafkaSnapshots policies()
    {
        return new KafkaSnapshots()
                .allow(List.of("alice"), List.of("write"), "topic", "orders")
                .allow(List.of("@analysts"), List.of("read"), "topic", "orders")
                .allow(List.of("bob"), List.of("read"), "group", "billing")
                .allow(List.of("mallory"), List.of("describe"), "topic", "*")
                // A denial of write also denies describe, which write implies: mallory sees every topic but orders.
                .deny(List.of("mallory"), List.of("write"), "topic", "orders")
                .group("analysts", "bob");
    }

    @BeforeAll
    void start() throws Exception
    {
        Path jar = Path.of(System.getProperty("grantforge.kafka.agent.jar"));
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertThat(zip.getEntry("org/devlive/grantforge/kafka/agent/GrantForgeKafkaAuthorizer.class")).isNotNull();
            assertThat(zip.getEntry("org/devlive/grantforge/kafka/agent/internal/jackson/databind/ObjectMapper.class")).isNotNull();
            assertThat(zip.getEntry("org/devlive/grantforge/kafka/agent/internal/caffeine/cache/Caffeine.class")).isNotNull();
            assertThat(zip.getEntry("org/apache/kafka/server/authorizer/Authorizer.class")).isNull();
            assertThat(zip.getEntry("com/github/benmanes/caffeine/cache/Caffeine.class")).isNull();
            assertThat(zip.getEntry("org/slf4j/Logger.class")).isNull();
        }
        AgentServer agents = new AgentServer(policies());
        server = agents;
        Testcontainers.exposeHostPorts(agents.port());
        KafkaBroker started = new KafkaBroker(image(), "SASL_PLAINTEXT")
                .set("sasl.enabled.mechanisms", "PLAIN")
                .set("sasl.mechanism.inter.broker.protocol", "PLAIN")
                .set("listener.name.external.plain.sasl.jaas.config", PLAIN + " username=\"admin\" password=\"admin-secret\""
                        + " user_admin=\"admin-secret\" user_alice=\"alice-secret\" user_bob=\"bob-secret\" user_mallory=\"mallory-secret\";")
                .set("authorizer.class.name", GrantForgeKafkaAuthorizer.class.getName())
                .set("super.users", "User:admin;User:ANONYMOUS")
                .set("grantforge.server.url", "http://host.testcontainers.internal:" + agents.port())
                .set("grantforge.token.file", "/opt/grantforge/token")
                .set("grantforge.signing.key.file", "/opt/grantforge/signing.key")
                .set("grantforge.instance", "broker-it")
                .set("grantforge.cache.dir", "/tmp/grantforge-cache")
                .set("grantforge.refresh.interval.ms", "1000")
                .set("grantforge.audit.batch.size", "1")
                .set("grantforge.audit.flush.interval.ms", "200")
                .set("grantforge.audit.fold.window.ms", "2000");
        started.container().withCopyFileToContainer(MountableFile.forHostPath(jar), "/opt/kafka/libs/grantforge-agent-kafka.jar")
                .withCopyToContainer(Transferable.of(AgentServer.TOKEN.getBytes(StandardCharsets.UTF_8), 0644), "/opt/grantforge/token")
                .withCopyToContainer(Transferable.of(agents.publicKey().getBytes(StandardCharsets.US_ASCII), 0644), "/opt/grantforge/signing.key");
        broker = started;
        started.start();
        try (Admin admin = Admin.create(client("admin"))) {
            admin.createTopics(List.of(new NewTopic("orders", 1, (short) 1), new NewTopic("payments", 1, (short) 1))).all().get();
        }
    }

    @AfterAll
    void stop()
    {
        if (broker != null) {
            broker.stop();
        }
        if (server != null) {
            server.close();
        }
    }

    private Map<String, Object> client(String user)
    {
        Map<String, Object> settings = new HashMap<>();
        settings.put("bootstrap.servers", requireNonNull(broker, "broker").servers());
        settings.put("security.protocol", "SASL_PLAINTEXT");
        settings.put("sasl.mechanism", "PLAIN");
        settings.put("sasl.jaas.config", PLAIN + " username=\"" + user + "\" password=\"" + user + "-secret\";");
        settings.put("request.timeout.ms", 15000);
        settings.put("default.api.timeout.ms", 20000);
        return settings;
    }

    private KafkaProducer<String, String> producer(String user, boolean idempotent)
    {
        Map<String, Object> settings = client(user);
        settings.put("enable.idempotence", idempotent);
        settings.put("acks", "all");
        settings.put("max.block.ms", 15000);
        settings.put("delivery.timeout.ms", 20000);
        return new KafkaProducer<>(settings, new StringSerializer(), new StringSerializer());
    }

    private void send(String user, boolean idempotent, String topic, int records) throws Exception
    {
        try (KafkaProducer<String, String> producer = producer(user, idempotent)) {
            for (int index = 0; index < records; index++) {
                producer.send(new ProducerRecord<>(topic, "k" + index, "v" + index)).get(20, TimeUnit.SECONDS);
            }
        }
    }

    private static Throwable cause(ThrowingCall call)
    {
        try {
            call.run();
        }
        catch (ExecutionException failed) {
            return requireNonNull(failed.getCause(), "cause");
        }
        catch (Exception failed) {
            return failed;
        }
        throw new AssertionError("the call succeeded");
    }

    /** The failure of a call, or what caused it, of a type: a producer reports a refusal it met before a send as the cause. */
    private static Throwable cause(ThrowingCall call, Class<? extends Throwable> type)
    {
        Throwable failed = cause(call);
        for (Throwable reason = failed; reason != null; reason = reason.getCause()) {
            if (type.isInstance(reason)) {
                return reason;
            }
        }
        return failed;
    }

    /** A call that may fail. */
    @FunctionalInterface
    private interface ThrowingCall
    {
        void run() throws Exception;
    }

    private List<String> consume(String user, String group, String topic, int records)
    {
        Map<String, Object> settings = client(user);
        settings.put("group.id", group);
        settings.put("auto.offset.reset", "earliest");
        settings.put("enable.auto.commit", false);
        List<String> values = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(settings, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (values.size() < records && System.nanoTime() < deadline) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : polled) {
                    values.add(record.value());
                }
            }
            consumer.commitSync();
        }
        return values;
    }

    private List<JsonNode> waitForEvents(Predicate<JsonNode> wanted) throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            List<JsonNode> found = requireNonNull(server, "server").events().stream().filter(wanted).toList();
            if (!found.isEmpty() || System.nanoTime() > deadline) {
                return found;
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
    }

    private static Predicate<JsonNode> event(String user, String resource, String accessType, String outcome)
    {
        return node -> user.equals(node.path("user").asText()) && resource.equals(node.path("resource").asText())
                && accessType.equals(node.path("accessType").asText()) && outcome.equals(node.path("outcome").asText());
    }

    @Test
    @Order(1)
    void producesWhatThePoliciesAllowWithAnIdempotentProducer() throws Exception
    {
        send("alice", true, "orders", 20);
        assertThat(cause(() -> send("alice", false, "payments", 1), TopicAuthorizationException.class))
                .isInstanceOf(TopicAuthorizationException.class);
        assertThat(cause(() -> send("mallory", true, "payments", 1), ClusterAuthorizationException.class))
                .as("no topic to write to, so no idempotent producer").isInstanceOf(ClusterAuthorizationException.class);
        assertThat(cause(() -> send("mallory", false, "orders", 1), TopicAuthorizationException.class)).as("a denial wins")
                .isInstanceOf(TopicAuthorizationException.class);
    }

    @Test
    @Order(2)
    void consumesThroughTheSnapshotsGroupsAndTheConsumerGroupsPolicy()
    {
        assertThat(consume("bob", "billing", "orders", 20)).hasSize(20).startsWith("v0", "v1");
        assertThatThrownBy(() -> consume("bob", "other", "orders", 1)).isInstanceOf(GroupAuthorizationException.class);
        assertThatThrownBy(() -> consume("bob", "billing", "payments", 1)).isInstanceOf(TopicAuthorizationException.class);
    }

    @Test
    @Order(3)
    void listsOnlyTheTopicsAUserMayDescribe() throws Exception
    {
        try (Admin alice = Admin.create(client("alice")); Admin mallory = Admin.create(client("mallory"))) {
            assertThat(alice.listTopics().names().get()).containsExactly("orders");
            assertThat(mallory.listTopics().names().get()).containsExactly("payments");
        }
    }

    @Test
    @Order(4)
    void showsThePoliciesAsReadOnlyAcls() throws Exception
    {
        try (Admin admin = Admin.create(client("admin"))) {
            AclBinding aliceWrites = new AclBinding(new ResourcePattern(ResourceType.TOPIC, "orders", PatternType.LITERAL),
                    new AccessControlEntry("User:alice", "*", AclOperation.WRITE, AclPermissionType.ALLOW));
            assertThat(admin.describeAcls(AclBindingFilter.ANY).values().get()).containsExactlyInAnyOrder(aliceWrites,
                    new AclBinding(new ResourcePattern(ResourceType.TOPIC, "orders", PatternType.LITERAL),
                            new AccessControlEntry("Group:analysts", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                    new AclBinding(new ResourcePattern(ResourceType.GROUP, "billing", PatternType.LITERAL),
                            new AccessControlEntry("User:bob", "*", AclOperation.READ, AclPermissionType.ALLOW)),
                    new AclBinding(new ResourcePattern(ResourceType.TOPIC, "*", PatternType.LITERAL),
                            new AccessControlEntry("User:mallory", "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW)),
                    new AclBinding(new ResourcePattern(ResourceType.TOPIC, "orders", PatternType.LITERAL),
                            new AccessControlEntry("User:mallory", "*", AclOperation.WRITE, AclPermissionType.DENY)));
            Throwable created = cause(() -> admin.createAcls(List.of(new AclBinding(new ResourcePattern(ResourceType.TOPIC, "payments",
                    PatternType.LITERAL), new AccessControlEntry("User:alice", "*", AclOperation.WRITE, AclPermissionType.ALLOW)))).all().get());
            assertThat(created).isInstanceOf(InvalidRequestException.class).hasMessageContaining("GrantForge");
            Throwable deleted = cause(() -> admin.deleteAcls(List.of(aliceWrites.toFilter())).all().get());
            assertThat(deleted).isInstanceOf(InvalidRequestException.class).hasMessageContaining("GrantForge");
        }
    }

    @Test
    @Order(5)
    void foldsRepeatedAccessesIntoOneEventAndReportsRefusals() throws Exception
    {
        List<JsonNode> writes = waitForEvents(event("alice", "orders", "write", "ALLOWED").and(node -> node.path("count").asInt(1) > 1));
        assertThat(writes).as("20 produce requests in a few events, of %s", requireNonNull(server, "server").events()).isNotEmpty();
        assertThat(writes.get(0).path("action").asText()).isEqualTo("PRODUCE");
        assertThat(writes.get(0).path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(writes.get(0).path("lastOccurredAt").asText()).isNotEmpty();
        // Kafka refuses before the write and the read: metadata needs describe, and so does finding the group's coordinator.
        assertThat(waitForEvents(event("alice", "payments", "describe", "DENIED"))).as("of %s", requireNonNull(server, "server").events())
                .isNotEmpty();
        assertThat(waitForEvents(event("bob", "other", "describe", "DENIED"))).as("of %s", requireNonNull(server, "server").events())
                .isNotEmpty();
        assertThat(requireNonNull(server, "server").events()).as("super users are not audited").noneMatch(node -> Set.of("admin", "ANONYMOUS")
                .contains(node.path("user").asText()));
    }

    @Test
    @Order(6)
    void appliesAPolicyChangeWithoutARestart() throws Exception
    {
        requireNonNull(server, "server").publish(policies().allow(List.of("alice"), List.of("write"), "topic", "payments"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            try {
                send("alice", false, "payments", 1);
                break;
            }
            catch (ExecutionException refused) {
                assertThat(refused.getCause()).isInstanceOf(TopicAuthorizationException.class);
                if (System.nanoTime() > deadline) {
                    throw refused;
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
        }
        try (Admin admin = Admin.create(client("admin"))) {
            assertThat(admin.describeAcls(AclBindingFilter.ANY).values().get()).contains(new AclBinding(new ResourcePattern(ResourceType.TOPIC,
                    "payments", PatternType.LITERAL), new AccessControlEntry("User:alice", "*", AclOperation.WRITE, AclPermissionType.ALLOW)));
        }
    }
}
