// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

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
 * The HBase plugin against a cluster that signs clients in with Kerberos, through a KDC in this JVM: the lookup principal
 * signs in with a keytab and with a password, a wrong password is named as such, and a simple sign-in is refused.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class HBaseKerberosIT
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String SERVER = "hbase/localhost@" + REALM;
    private static final String KEYTAB_USER = "lookup@" + REALM;
    private static final String PASSWORD_USER = "grantforge@" + REALM;
    private static final String SECRET = "kerberos-secret";

    @TempDir
    static Path work;

    private final HBaseProvider provider = new HBaseProvider();
    private @Nullable SimpleKdcServer kdc;
    private @Nullable HBaseCluster cluster;
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
        File serverKeytab = work.resolve("hbase.keytab").toFile();
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
        HBaseCluster started = new HBaseCluster("2.6.7")
                .set("hbase.security.authentication", "kerberos")
                .set("hadoop.security.authentication", "kerberos")
                .set("hbase.master.kerberos.principal", SERVER)
                .set("hbase.master.keytab.file", "/opt/hbase/conf/hbase.keytab")
                .set("hbase.regionserver.kerberos.principal", SERVER)
                .set("hbase.regionserver.keytab.file", "/opt/hbase/conf/hbase.keytab");
        started.container().withEnv("HBASE_OPTS", "-Djava.security.krb5.conf=/opt/hbase/conf/krb5.conf")
                .withCopyToContainer(Transferable.of(krb5), "/opt/hbase/conf/krb5.conf")
                .withCopyToContainer(Transferable.of(Files.readAllBytes(serverKeytab.toPath()), 0644), "/opt/hbase/conf/hbase.keytab");
        cluster = started;
        started.start();
    }

    @AfterAll
    void stop() throws Exception
    {
        if (cluster != null) {
            cluster.stop();
        }
        HBaseClient.forgetLogins();
        KerberosRealms.forget();
        if (kdc != null) {
            kdc.stop();
        }
    }

    private ServiceConfig service(String user, String... values)
    {
        HBaseCluster running = requireNonNull(cluster, "cluster");
        Map<String, String> map = new HashMap<>(Map.of(HBaseSettings.QUORUM, running.quorum(), HBaseSettings.ZOOKEEPER_PORT,
                Integer.toString(running.zookeeperPort()), HBaseSettings.AUTHENTICATION, "kerberos", HBaseSettings.USER, user, HBaseSettings.KDC,
                "localhost:" + kdcPort, HBaseSettings.TIMEOUT, "15000"));
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("secure-tables", map);
    }

    private ServiceConfig keytabService()
    {
        return service(KEYTAB_USER, HBaseSettings.KEYTAB, requireNonNull(userKeytab, "keytab").getPath());
    }

    @Test
    void looksUpWithAKeytab()
    {
        ConnectionResult result = provider.testConnection(keytabService());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(keytabService(), "namespace", "", Map.of(), 10))).contains("default", "hbase");
    }

    @Test
    void looksUpWithAPassword()
    {
        ServiceConfig config = service(PASSWORD_USER, HBaseSettings.PASSWORD, SECRET);
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(config, "table", "", Map.of("namespace", List.of("hbase")), 10))).contains("meta");
    }

    @Test
    void namesAWrongPassword()
    {
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(service(PASSWORD_USER, HBaseSettings.PASSWORD, "wrong"), "namespace", "",
                Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void isRefusedASimpleSignIn()
    {
        HBaseCluster running = requireNonNull(cluster, "cluster");
        ServiceConfig simple = new ServiceConfig("insecure", Map.of(HBaseSettings.QUORUM, running.quorum(), HBaseSettings.ZOOKEEPER_PORT,
                Integer.toString(running.zookeeperPort()), HBaseSettings.USER, "grantforge", HBaseSettings.TIMEOUT, "5000"));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(simple, "namespace", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> {
                    assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
                    assertThat(failed).hasMessageContaining("hbase.security.authentication");
                });
    }
}
