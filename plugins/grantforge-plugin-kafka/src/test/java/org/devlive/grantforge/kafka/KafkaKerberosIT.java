// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.KerberosRealms;
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
import org.testcontainers.Testcontainers;
import org.testcontainers.images.builder.Transferable;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Kafka plugin against a broker whose listener signs clients in with Kerberos, through a KDC in this JVM: the lookup
 * principal signs in with a keytab and with a password, Kafka's own login taking the password from the plugin's handler.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 8, unit = TimeUnit.MINUTES)
class KafkaKerberosIT
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String BROKER = "kafka/localhost@" + REALM;
    private static final String KEYTAB_USER = "lookup@" + REALM;
    private static final String PASSWORD_USER = "grantforge@" + REALM;
    private static final String SECRET = "kerberos-secret";

    @TempDir
    static Path work;

    private final KafkaProvider provider = new KafkaProvider();
    private @Nullable SimpleKdcServer kdc;
    private @Nullable KafkaBroker broker;
    private int kdcPort;
    private @Nullable File userKeytab;

    @BeforeAll
    void start() throws Exception
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            kdcPort = socket.getLocalPort();
        }
        SimpleKdcServer server = new SimpleKdcServer();
        server.setWorkDir(work.toFile());
        server.setKdcRealm(REALM);
        server.setKdcHost("localhost");
        server.setAllowUdp(false);
        server.setKdcTcpPort(kdcPort);
        server.init();
        server.start();
        kdc = server;
        File brokerKeytab = work.resolve("kafka.keytab").toFile();
        server.createPrincipal(BROKER);
        server.exportPrincipal(BROKER, brokerKeytab);
        File keytab = work.resolve("lookup.keytab").toFile();
        server.createPrincipal(KEYTAB_USER);
        server.exportPrincipal(KEYTAB_USER, keytab);
        userKeytab = keytab;
        server.createPrincipal(PASSWORD_USER, SECRET);
        Testcontainers.exposeHostPorts(kdcPort);
        String krb5 = """
                [libdefaults]
                  default_realm = %s
                  udp_preference_limit = 1
                  rdns = false
                  dns_canonicalize_hostname = false
                  dns_lookup_kdc = false
                  dns_lookup_realm = false
                [realms]
                  %s = {
                    kdc = host.testcontainers.internal:%d
                  }
                """.formatted(REALM, REALM, kdcPort);
        KafkaBroker started = new KafkaBroker("apache/kafka:4.3.1", "SASL_PLAINTEXT")
                .set("sasl.enabled.mechanisms", "GSSAPI")
                .set("sasl.mechanism.inter.broker.protocol", "GSSAPI")
                .set("sasl.kerberos.service.name", "kafka")
                .set("listener.name.external.gssapi.sasl.jaas.config", "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true"
                        + " storeKey=true keyTab=\"/tmp/kafka.keytab\" principal=\"" + BROKER + "\";");
        started.container().withEnv("KAFKA_OPTS", "-Djava.security.krb5.conf=/tmp/krb5.conf")
                .withCopyToContainer(Transferable.of(krb5), "/tmp/krb5.conf")
                .withCopyToContainer(Transferable.of(Files.readAllBytes(brokerKeytab.toPath()), 0644), "/tmp/kafka.keytab");
        broker = started;
        started.start();
        try (Admin admin = Admin.create(adminSettings())) {
            admin.createTopics(List.of(new NewTopic("secure-orders", 1, (short) 1))).all().get();
        }
    }

    @AfterAll
    void stop() throws Exception
    {
        if (broker != null) {
            broker.stop();
        }
        KerberosRealms.forget();
        if (kdc != null) {
            kdc.stop();
        }
    }

    private Map<String, Object> adminSettings() throws Exception
    {
        KafkaSettings settings = KafkaSettings.of(keytabService());
        return settings.client(KerberosPasswordHandler.class, "kerberos-setup");
    }

    private ServiceConfig service(String user, String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(KafkaSettings.SERVERS, requireNonNull(broker, "broker").servers(), KafkaSettings.PROTOCOL,
                "SASL_PLAINTEXT", KafkaSettings.MECHANISM, "GSSAPI", KafkaSettings.USER, user, KafkaSettings.KDC, "localhost:" + kdcPort,
                KafkaSettings.TIMEOUT, "15000"));
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("secure-events", map);
    }

    private ServiceConfig keytabService()
    {
        return service(KEYTAB_USER, KafkaSettings.KEYTAB, requireNonNull(userKeytab, "keytab").getPath());
    }

    @Test
    void looksUpWithAKeytab()
    {
        ConnectionResult result = provider.testConnection(keytabService());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(keytabService(), "topic", "secure", Map.of(), 10))).containsExactly("secure-orders");
    }

    @Test
    void looksUpWithAPassword()
    {
        ServiceConfig config = service(PASSWORD_USER, KafkaSettings.PASSWORD, SECRET);
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(config, "topic", "", Map.of(), 10))).contains("secure-orders");
    }

    @Test
    void namesAWrongPassword()
    {
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(service(PASSWORD_USER, KafkaSettings.PASSWORD, "wrong"), "topic", "",
                Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }
}
