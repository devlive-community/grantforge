// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Hive plugin against a HiveServer2 that signs clients in with Kerberos, through a KDC in this JVM: the lookup
 * principal signs in with a keytab and with a password, and Hive's driver opens its transport as that principal, on
 * whichever JDK runs the tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class HiveKerberosIT
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String SERVER = "hive/localhost@" + REALM;
    private static final String KEYTAB_USER = "lookup@" + REALM;
    private static final String PASSWORD_USER = "grantforge@" + REALM;
    private static final String SECRET = "kerberos-secret";

    @TempDir
    static Path work;

    private final HiveProvider provider = new HiveProvider();
    private @Nullable SimpleKdcServer kdc;
    private @Nullable GenericContainer<?> hive;
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
        File serverKeytab = work.resolve("hive.keytab").toFile();
        server.createPrincipal(SERVER);
        server.exportPrincipal(SERVER, serverKeytab);
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
        String coreSite = """
                <configuration>
                  <property><name>hadoop.security.authentication</name><value>kerberos</value></property>
                </configuration>
                """;
        String options = String.join(" ", "-Dhive.metastore.warehouse.dir=/tmp/warehouse", "-Dhive.server2.authentication=KERBEROS",
                "-Dhive.server2.authentication.kerberos.principal=" + SERVER,
                "-Dhive.server2.authentication.kerberos.keytab=/tmp/hive.keytab", "-Dhive.server2.enable.doAs=false",
                "-Djava.security.krb5.conf=/tmp/krb5.conf");
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("apache/hive:4.0.1")).withEnv("SERVICE_NAME", "hiveserver2")
                .withEnv("SERVICE_OPTS", options).withExposedPorts(10000)
                .withCopyToContainer(Transferable.of(krb5), "/tmp/krb5.conf")
                .withCopyToContainer(Transferable.of(Files.readAllBytes(serverKeytab.toPath()), 0644), "/tmp/hive.keytab")
                .withCopyToContainer(Transferable.of(coreSite), "/opt/hadoop/etc/hadoop/core-site.xml")
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
        hive = container;
        container.start();
        // HiveServer2 opens its port before its sessions work.
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(4);
        while (provider.testConnection(keytabService()).status() != ConnectionResult.Status.SUCCEEDED && System.nanoTime() < deadline) {
            TimeUnit.SECONDS.sleep(3);
        }
    }

    @AfterAll
    void stop() throws Exception
    {
        KerberosRealms.forget();
        if (hive != null) {
            hive.stop();
        }
        if (kdc != null) {
            kdc.stop();
        }
    }

    private ServiceConfig service(String user, String... values)
    {
        GenericContainer<?> container = requireNonNull(hive, "hive");
        Map<String, String> map = new HashMap<>(Map.of(HiveProvider.URL, "jdbc:hive2://localhost:" + container.getMappedPort(10000)
                + "/default;principal=" + SERVER, HiveProvider.AUTHENTICATION, HiveProvider.KERBEROS, HiveProvider.USER, user,
                HiveProvider.KDC, "localhost:" + kdcPort));
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("secure-warehouse", map);
    }

    private ServiceConfig keytabService()
    {
        return service(KEYTAB_USER, HiveProvider.KEYTAB, requireNonNull(userKeytab, "keytab").getPath());
    }

    @Test
    void looksUpWithAKeytab()
    {
        ConnectionResult result = provider.testConnection(keytabService());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(keytabService(), "database", "def", Map.of(), 10))).containsExactly("default");
    }

    @Test
    void looksUpWithAPassword()
    {
        ServiceConfig config = service(PASSWORD_USER, HiveProvider.PASSWORD, SECRET);
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(config, "database", "", Map.of(), 10))).contains("default");
    }

    @Test
    void namesAWrongPassword()
    {
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(service(PASSWORD_USER, HiveProvider.PASSWORD, "wrong"), "database", "",
                Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        assertThat(List.of(provider.testConnection(service(PASSWORD_USER, HiveProvider.PASSWORD, "wrong")).status()))
                .containsExactly(ConnectionResult.Status.FAILED);
    }

    @Test
    void theServerTakesNothingButKerberos()
    {
        GenericContainer<?> container = requireNonNull(hive, "hive");
        ServiceConfig plain = new ServiceConfig("plain", Map.of(HiveProvider.URL, "jdbc:hive2://localhost:" + container.getMappedPort(10000)
                + "/default", HiveProvider.USER, "lookup", HiveProvider.PASSWORD, "anything", HiveProvider.READ_TIMEOUT, "5000"));
        assertThat(provider.testConnection(plain).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }
}
