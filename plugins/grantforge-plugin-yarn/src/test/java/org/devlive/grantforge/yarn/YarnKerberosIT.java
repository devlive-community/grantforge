// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

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

import java.io.File;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The YARN plugin against a ResourceManager that signs clients in with Kerberos, through a KDC in this JVM: the lookup
 * principal signs in with a keytab and with a password, and a simple sign-in is refused.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class YarnKerberosIT
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String SERVER = "yarn/localhost@" + REALM;
    private static final String KEYTAB_USER = "lookup@" + REALM;
    private static final String PASSWORD_USER = "grantforge@" + REALM;
    private static final String SECRET = "kerberos-secret";

    @TempDir
    static Path work;

    private final YarnProvider provider = new YarnProvider();
    private @Nullable SimpleKdcServer kdc;
    private @Nullable YarnCluster cluster;
    private int kdcPort;

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
        File serverKeytab = work.resolve("yarn.keytab").toFile();
        server.createPrincipal(SERVER);
        server.exportPrincipal(SERVER, serverKeytab);
        server.createPrincipal(KEYTAB_USER);
        server.exportPrincipal(KEYTAB_USER, work.resolve("lookup.keytab").toFile());
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
        YarnCluster started = new YarnCluster("3.4.3").core("hadoop.security.authentication", "kerberos")
                .yarn("yarn.resourcemanager.principal", SERVER).yarn("yarn.resourcemanager.keytab", "/opt/hadoop/etc/hadoop/yarn.keytab")
                .file("/opt/hadoop/etc/hadoop/yarn.keytab", Files.readAllBytes(serverKeytab.toPath()))
                .file("/opt/hadoop/etc/hadoop/krb5.conf", krb5.getBytes(StandardCharsets.US_ASCII));
        started.container().withEnv("HADOOP_OPTS", "-Djava.security.krb5.conf=/opt/hadoop/etc/hadoop/krb5.conf");
        cluster = started;
        started.start();
    }

    @AfterAll
    void stop() throws Exception
    {
        if (cluster != null) {
            cluster.stop();
        }
        YarnClusterClient.forgetLogins();
        KerberosRealms.forget();
        if (kdc != null) {
            kdc.stop();
        }
    }

    private ServiceConfig service(String user, String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(YarnSettings.RESOURCE_MANAGER, requireNonNull(cluster, "cluster").address(),
                YarnSettings.AUTHENTICATION, "kerberos", YarnSettings.USER, user, YarnSettings.KDC, "localhost:" + kdcPort, YarnSettings.TIMEOUT,
                "15000"));
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("secure-compute", map);
    }

    @Test
    void looksUpWithAKeytab()
    {
        ServiceConfig config = service(KEYTAB_USER, YarnSettings.KEYTAB, work.resolve("lookup.keytab").toString());
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(config, "queue", "root.sales", Map.of(), 10))).contains("root.sales.etl");
    }

    @Test
    void looksUpWithAPassword()
    {
        ServiceConfig config = service(PASSWORD_USER, YarnSettings.PASSWORD, SECRET);
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(config, "queue", "", Map.of(), 10))).contains("root.hr");
    }

    @Test
    void namesAWrongPassword()
    {
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(service(PASSWORD_USER, YarnSettings.PASSWORD, "wrong"), "queue", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class,
                        failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void isRefusedASimpleSignIn()
    {
        ServiceConfig simple = new ServiceConfig("insecure", Map.of(YarnSettings.RESOURCE_MANAGER, requireNonNull(cluster, "cluster").address(),
                YarnSettings.USER, "grantforge", YarnSettings.TIMEOUT, "5000"));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(simple, "queue", "", Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).as(String.valueOf(failed.getMessage())).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }
}
