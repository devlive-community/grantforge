// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

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
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.images.builder.Transferable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Kafka plugin against a broker that serves TLS with a certificate for {@code localhost}, made with the JDK's
 * keytool: the plugin connects with a truststore that holds the certificate, and refuses a broker it cannot verify.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 6, unit = TimeUnit.MINUTES)
class KafkaTlsIT
{
    @TempDir
    static Path work;

    private final KafkaProvider provider = new KafkaProvider();
    private @Nullable KafkaBroker broker;

    private static void keytool(String... arguments) throws IOException, InterruptedException
    {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IOException("keytool failed: " + output);
        }
    }

    private static void certificate(String name, String store, String password) throws IOException, InterruptedException
    {
        keytool("-genkeypair", "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost", "-ext",
                "SAN=dns:localhost", "-storetype", "PKCS12", "-keystore", store, "-storepass", password, "-keypass", password);
        keytool("-exportcert", "-alias", name, "-keystore", store, "-storepass", password, "-rfc", "-file", name + ".pem");
    }

    private static void trust(String name, String store) throws IOException, InterruptedException
    {
        keytool("-importcert", "-noprompt", "-alias", name, "-file", name + ".pem", "-storetype", "PKCS12", "-keystore", store, "-storepass",
                "trust-secret");
    }

    @BeforeAll
    void start() throws Exception
    {
        certificate("broker", "broker.p12", "broker-secret");
        trust("broker", "trust.p12");
        certificate("stranger", "stranger.p12", "stranger-secret");
        trust("stranger", "wrong.p12");
        KafkaBroker started = new KafkaBroker("apache/kafka:4.3.1", "SSL")
                .set("ssl.keystore.location", "/tmp/broker.p12").set("ssl.keystore.password", "broker-secret")
                .set("ssl.key.password", "broker-secret").set("ssl.keystore.type", "PKCS12")
                .set("ssl.truststore.location", "/tmp/trust.p12").set("ssl.truststore.password", "trust-secret")
                .set("ssl.truststore.type", "PKCS12");
        started.container().withCopyToContainer(Transferable.of(Files.readAllBytes(work.resolve("broker.p12")), 0644), "/tmp/broker.p12")
                .withCopyToContainer(Transferable.of(Files.readAllBytes(work.resolve("trust.p12")), 0644), "/tmp/trust.p12");
        broker = started;
        started.start();
    }

    @AfterAll
    void stop()
    {
        if (broker != null) {
            broker.stop();
        }
    }

    private ServiceConfig service(String truststore)
    {
        return new ServiceConfig("tls-events", Map.of(KafkaSettings.SERVERS, requireNonNull(broker, "broker").servers(), KafkaSettings.PROTOCOL,
                "SSL", KafkaSettings.TRUSTSTORE, work.resolve(truststore).toString(), KafkaSettings.TRUSTSTORE_PASSWORD, "trust-secret",
                KafkaSettings.TRUSTSTORE_TYPE, "PKCS12", KafkaSettings.TIMEOUT, "10000"));
    }

    @Test
    void connectsToABrokerItCanVerify()
    {
        ConnectionResult result = provider.testConnection(service("trust.p12"));
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(service("trust.p12"), "cluster", "", Map.of(), 10))).containsExactly("kafka-cluster");
        assertThat(provider.lookup(new LookupRequest(service("trust.p12"), "topic", "", Map.of(), 10))).isEmpty();
    }

    @Test
    void refusesABrokerItCannotVerify()
    {
        assertThat(provider.testConnection(service("wrong.p12")).status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(service("wrong.p12"), "topic", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isIn(
                        LookupException.Reason.UNREACHABLE, LookupException.Reason.AUTHENTICATION_FAILED));
    }
}
