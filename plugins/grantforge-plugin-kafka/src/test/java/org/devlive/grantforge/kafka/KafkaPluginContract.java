// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.clients.admin.UserScramCredentialUpsertion;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.apache.kafka.common.serialization.StringSerializer;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Kafka plugin against a real broker of one Kafka release, with a SASL listener that takes PLAIN and SCRAM and
 * Kafka's own authorizer: the connection test, lookups of topics, groups, transactional IDs and SCRAM users by a user
 * the broker's ACLs let describe them, a refused sign-in, and a user without permissions.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 8, unit = TimeUnit.MINUTES)
abstract class KafkaPluginContract
{
    private static final String ADMIN_JAAS = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"admin\""
            + " password=\"admin-secret\"";

    private final KafkaProvider provider = new KafkaProvider();
    private @Nullable KafkaBroker broker;

    /**
     * The image under test.
     *
     * @return such as {@code apache/kafka:4.3.1}
     */
    abstract String image();

    @BeforeAll
    void startBroker() throws Exception
    {
        KafkaBroker started = new KafkaBroker(image(), "SASL_PLAINTEXT")
                .set("sasl.enabled.mechanisms", "PLAIN,SCRAM-SHA-256")
                .set("sasl.mechanism.inter.broker.protocol", "PLAIN")
                .set("listener.name.external.plain.sasl.jaas.config", ADMIN_JAAS + " user_admin=\"admin-secret\""
                        + " user_stranger=\"stranger-secret\";")
                .set("listener.name.external.scram-sha-256.sasl.jaas.config", "org.apache.kafka.common.security.scram.ScramLoginModule required;")
                .set("authorizer.class.name", "org.apache.kafka.metadata.authorizer.StandardAuthorizer")
                .set("super.users", "User:admin;User:ANONYMOUS");
        broker = started;
        started.start();
        try (Admin admin = Admin.create(adminSettings())) {
            admin.createTopics(List.of(new NewTopic("orders", 1, (short) 1), new NewTopic("orders-eu", 1, (short) 1),
                    new NewTopic("payments", 1, (short) 1))).all().get();
            admin.alterUserScramCredentials(List.of(new UserScramCredentialUpsertion("lookup", new ScramCredentialInfo(ScramMechanism.SCRAM_SHA_256,
                    4096), "lookup-secret"))).all().get();
            AccessControlEntry describe = new AccessControlEntry("User:lookup", "*", AclOperation.DESCRIBE, AclPermissionType.ALLOW);
            admin.createAcls(List.of(new AclBinding(new ResourcePattern(ResourceType.TOPIC, "*", PatternType.LITERAL), describe),
                    new AclBinding(new ResourcePattern(ResourceType.GROUP, "*", PatternType.LITERAL), describe),
                    new AclBinding(new ResourcePattern(ResourceType.TRANSACTIONAL_ID, "*", PatternType.LITERAL), describe),
                    new AclBinding(new ResourcePattern(ResourceType.CLUSTER, "kafka-cluster", PatternType.LITERAL), describe))).all().get();
            admin.alterConsumerGroupOffsets("billing", Map.of(new TopicPartition("orders", 0), new OffsetAndMetadata(0))).all().get();
        }
        Properties producer = new Properties();
        producer.putAll(adminSettings());
        producer.put("transactional.id", "payments-tx");
        try (KafkaProducer<String, String> transactions = new KafkaProducer<>(producer, new StringSerializer(), new StringSerializer())) {
            transactions.initTransactions();
            transactions.beginTransaction();
            transactions.send(new ProducerRecord<>("payments", "p1", "10.00")).get();
            transactions.commitTransaction();
        }
    }

    @AfterAll
    void stopBroker()
    {
        if (broker != null) {
            broker.stop();
        }
    }

    private Map<String, Object> adminSettings()
    {
        Map<String, Object> settings = new HashMap<>();
        settings.put("bootstrap.servers", requireNonNull(broker, "broker").servers());
        settings.put("security.protocol", "SASL_PLAINTEXT");
        settings.put("sasl.mechanism", "PLAIN");
        settings.put("sasl.jaas.config", ADMIN_JAAS + ";");
        return settings;
    }

    private ServiceConfig service(String mechanism, String user, String password)
    {
        return new ServiceConfig("events", Map.of(KafkaSettings.SERVERS, requireNonNull(broker, "broker").servers(), KafkaSettings.PROTOCOL,
                "SASL_PLAINTEXT", KafkaSettings.MECHANISM, mechanism, KafkaSettings.USER, user, KafkaSettings.PASSWORD, password,
                KafkaSettings.TIMEOUT, "15000"));
    }

    private ServiceConfig lookupUser()
    {
        return service("SCRAM-SHA-256", "lookup", "lookup-secret");
    }

    private List<String> lookup(ServiceConfig config, String resource, String typed)
    {
        return provider.lookup(new LookupRequest(config, resource, typed, Map.of(), 50));
    }

    @Test
    void connectsAndNamesTheCluster()
    {
        ConnectionResult result = provider.testConnection(lookupUser());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).startsWith("cluster ").endsWith(", 1 broker");
    }

    @Test
    void looksUpWhatTheUserMayDescribe()
    {
        assertThat(lookup(lookupUser(), "topic", "ord")).containsExactly("orders", "orders-eu");
        assertThat(lookup(lookupUser(), "topic", "")).contains("orders", "orders-eu", "payments").doesNotContain("__consumer_offsets");
        assertThat(lookup(lookupUser(), "topic", "__")).contains("__consumer_offsets");
        assertThat(lookup(lookupUser(), "group", "bill")).containsExactly("billing");
        assertThat(lookup(lookupUser(), "transactional_id", "pay")).containsExactly("payments-tx");
        assertThat(lookup(lookupUser(), "user", "")).containsExactly("lookup");
        assertThat(lookup(lookupUser(), "cluster", "")).containsExactly("kafka-cluster");
        assertThat(lookup(service("PLAIN", "admin", "admin-secret"), "topic", "pay")).containsExactly("payments");
    }

    @Test
    void namesARefusedSignIn()
    {
        assertThatThrownBy(() -> lookup(service("SCRAM-SHA-256", "lookup", "wrong"), "topic", "")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        assertThat(provider.testConnection(service("PLAIN", "admin", "wrong")).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void aUserWithoutPermissionsSeesNothingAndIsRefusedWhatKafkaChecks()
    {
        ServiceConfig stranger = service("PLAIN", "stranger", "stranger-secret");
        assertThat(lookup(stranger, "topic", "")).as("Kafka leaves out the topics a user may not describe").isEmpty();
        assertThatThrownBy(() -> lookup(stranger, "user", "")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
    }
}
