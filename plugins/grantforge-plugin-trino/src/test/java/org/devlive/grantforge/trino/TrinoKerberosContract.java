// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Trino plugin against a coordinator of one release that signs users in with Kerberos over HTTPS, through a KDC in
 * this JVM: the lookup principal signs in with a keytab and with a password, and the driver authenticates with the
 * credential of that sign-in rather than with anything of the JVM's own.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class TrinoKerberosContract
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String COORDINATOR = "trino/localhost@" + REALM;
    private static final String KEYTAB_USER = "lookup@" + REALM;
    private static final String PASSWORD_USER = "grantforge@" + REALM;
    private static final String SECRET = "kerberos-secret";

    private final TrinoProvider provider = new TrinoProvider();
    private @Nullable SimpleKdcServer kdc;
    private @Nullable TrinoServer server;
    private Path work = Path.of(".");
    private int kdcPort;

    /**
     * The release under test.
     *
     * @return such as {@code 476}
     */
    abstract String version();

    @BeforeAll
    void start(@TempDir Path directory) throws Exception
    {
        work = directory;
        try (ServerSocket socket = new ServerSocket(0)) {
            kdcPort = socket.getLocalPort();
        }
        SimpleKdcServer started = new SimpleKdcServer();
        started.setWorkDir(work.toFile());
        started.setKdcRealm(REALM);
        started.setKdcHost("localhost");
        started.setAllowUdp(false);
        started.setKdcTcpPort(kdcPort);
        started.init();
        started.start();
        kdc = started;
        File coordinatorKeytab = work.resolve("trino.keytab").toFile();
        started.createPrincipal(COORDINATOR);
        started.exportPrincipal(COORDINATOR, coordinatorKeytab);
        started.createPrincipal(KEYTAB_USER);
        started.exportPrincipal(KEYTAB_USER, work.resolve("lookup.keytab").toFile());
        started.createPrincipal(PASSWORD_USER, SECRET);
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
        TestCertificates.create(work, "coordinator", "trust.p12", "PKCS12");
        TrinoServer coordinator = new TrinoServer(version()).set("http-server.https.enabled=true").set("http-server.https.port=" + TrinoServer.HTTPS)
                .set("http-server.https.keystore.path=/etc/trino/coordinator.p12").set("http-server.https.keystore.key=" + TestCertificates.KEY_PASSWORD)
                .set("internal-communication.shared-secret=" + Base64.getEncoder().encodeToString(new byte[48]))
                .set("http-server.authentication.type=KERBEROS").set("http-server.authentication.krb5.service-name=trino")
                .set("http-server.authentication.krb5.principal-hostname=localhost")
                .set("http-server.authentication.krb5.keytab=/etc/trino/trino.keytab").set("http.authentication.krb5.config=/etc/trino/krb5.conf")
                .set("http-server.authentication.krb5.user-mapping.pattern=(.*)@" + REALM.replace(".", "\\\\."))
                .file("coordinator.p12", Files.readAllBytes(work.resolve("coordinator.p12")))
                .file("trino.keytab", Files.readAllBytes(coordinatorKeytab.toPath()))
                .file("krb5.conf", krb5.getBytes(StandardCharsets.US_ASCII));
        server = coordinator;
        coordinator.start();
    }

    @AfterAll
    void stop() throws Exception
    {
        if (server != null) {
            server.stop();
        }
        KerberosRealms.forget();
        if (kdc != null) {
            kdc.stop();
        }
    }

    private ServiceConfig service(String user, String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(TrinoSettings.URL, requireNonNull(server, "server").secureUrl(), TrinoSettings.SSL, "true",
                TrinoSettings.TRUSTSTORE, work.resolve("trust.p12").toString(), TrinoSettings.TRUSTSTORE_PASSWORD, TestCertificates.TRUST_PASSWORD,
                TrinoSettings.TRUSTSTORE_TYPE, "PKCS12", TrinoSettings.AUTHENTICATION, TrinoSettings.KERBEROS, TrinoSettings.USER, user,
                TrinoSettings.KDC, "localhost:" + kdcPort, TrinoSettings.CANONICAL_HOST, "false", TrinoSettings.TIMEOUT, "15000"));
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("kerberized", map);
    }

    @Test
    void looksUpWithAKeytab()
    {
        ServiceConfig config = service(KEYTAB_USER, TrinoSettings.KEYTAB, work.resolve("lookup.keytab").toString());
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(config, "schema", "ti", Map.of("catalog", List.of("tpch")), 10))).containsExactly("tiny");
    }

    @Test
    void looksUpWithAPassword()
    {
        ServiceConfig config = service(PASSWORD_USER, TrinoSettings.PASSWORD, SECRET);
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.lookup(new LookupRequest(config, "catalog", "tp", Map.of(), 10))).containsExactly("tpcds", "tpch");
    }

    @Test
    void namesAWrongPassword()
    {
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(service(PASSWORD_USER, TrinoSettings.PASSWORD, "wrong"), "catalog", "", Map.of(),
                10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void namesAServicePrincipalTheKdcDoesNotKnow()
    {
        ServiceConfig config = service(PASSWORD_USER, TrinoSettings.PASSWORD, SECRET, TrinoSettings.SERVICE_NAME, "presto");
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(config, "catalog", "", Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).as(String.valueOf(failed.getMessage())).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }
}
