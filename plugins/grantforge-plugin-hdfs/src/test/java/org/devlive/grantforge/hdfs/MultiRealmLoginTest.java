// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.hadoop.security.UserGroupInformation;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One GrantForge, two realms, each with its own KDC, which only the services name: the server's krb5.conf knows
 * neither, and has no default realm. A local file system stands in for the clusters.
 */
class MultiRealmLoginTest
{
    private static final String ALPHA = "ALPHA.EXAMPLE";
    private static final String BETA = "BETA.EXAMPLE";
    private static final String SECRET = "alpha-secret";

    @TempDir
    static Path work;

    private static SimpleKdcServer alphaKdc;
    private static SeparateKdc betaKdc;
    private static int alphaPort;
    private static int betaPort;
    private static Path betaKeytab;

    private final HdfsProvider provider = new HdfsProvider();

    @BeforeAll
    static void startKdcs() throws Exception
    {
        alphaPort = freePort();
        alphaKdc = new SimpleKdcServer();
        alphaKdc.setWorkDir(Files.createDirectories(work.resolve(ALPHA)).toFile());
        alphaKdc.setKdcRealm(ALPHA);
        alphaKdc.setKdcHost("localhost");
        alphaKdc.setAllowUdp(false);
        alphaKdc.setKdcTcpPort(alphaPort);
        alphaKdc.init();
        alphaKdc.start();
        alphaKdc.createPrincipal("grantforge@" + ALPHA, SECRET);
        betaPort = freePort();
        betaKeytab = work.resolve("beta.keytab");
        betaKdc = SeparateKdc.start(BETA, betaPort, Files.createDirectories(work.resolve(BETA)), betaKeytab, "lookup@" + BETA);
        // The server's own file: settings for every realm, but none of these realms, and no default realm.
        Path server = Files.writeString(work.resolve("server-krb5.conf"), """
                [libdefaults]
                  udp_preference_limit = 1
                  dns_lookup_kdc = false
                  dns_lookup_realm = false
                """);
        // This JVM is the test class's own (surefire does not reuse forks here), so the setting cannot leak.
        System.setProperty(KerberosRealms.CONFIGURATION, server.toString());
    }

    private static int freePort() throws IOException
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @AfterAll
    static void stopKdcs() throws Exception
    {
        alphaKdc.stop();
        betaKdc.close();
        KerberosRealms.forget();
    }

    private static ServiceConfig service(String name, String user, int kdcPort, String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of("fs.default.name", "file:///", "username", user,
                "hadoop.security.authentication", "kerberos", "kerberos.kdc", "localhost:" + kdcPort));
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig(name, values);
    }

    private static ServiceConfig alpha()
    {
        return service("alpha-lake", "grantforge@" + ALPHA, alphaPort, "password", SECRET);
    }

    private static ServiceConfig beta()
    {
        return service("beta-lake", "lookup@" + BETA, betaPort, "keytab", betaKeytab.toString());
    }

    /** Who the file system action runs as, by full and short name. */
    private static String signedIn(ServiceConfig config) throws IOException
    {
        return new HadoopClient(config).run(files -> {
            UserGroupInformation user = UserGroupInformation.getCurrentUser();
            return user.getUserName() + " " + user.getShortUserName();
        });
    }

    @Test
    void signsEachServiceInAtItsOwnRealmsKdcWithoutARestart() throws Exception
    {
        // A KDC that is not there fails the sign-in; correcting it takes effect on the next call.
        int nobody = freePort();
        ConnectionResult unreachable = provider.testConnection(service("alpha-lake", "grantforge@" + ALPHA, nobody, "password", SECRET));
        assertThat(unreachable.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(provider.testConnection(alpha())).isEqualTo(ConnectionResult.succeeded());
        assertThat(provider.testConnection(beta())).isEqualTo(ConnectionResult.succeeded());
        assertThat(Files.readString(Path.of(System.getProperty(KerberosRealms.CONFIGURATION))))
                .contains("include " + work.resolve("server-krb5.conf").toAbsolutePath())
                .contains("default_realm = ").contains(ALPHA + " = {").contains(BETA + " = {");

        // Side by side, each service keeps its own sign-in, and Hadoop gives each principal its short name.
        HadoopClient.forgetLogins();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> calls = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                ServiceConfig config = index % 2 == 0 ? alpha() : beta();
                calls.add(pool.submit(() -> signedIn(config)));
            }
            for (int index = 0; index < calls.size(); index++) {
                assertThat(calls.get(index).get()).isEqualTo(index % 2 == 0 ? "grantforge@" + ALPHA + " grantforge" : "lookup@" + BETA + " lookup");
            }
        }
        finally {
            pool.shutdownNow();
        }
    }

    @Test
    void refusesAPrincipalOfARealmItsKdcDoesNotServe()
    {
        ConnectionResult wrong = provider.testConnection(service("gamma-lake", "grantforge@GAMMA.EXAMPLE", betaPort,
                "password", SECRET));
        assertThat(wrong.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(wrong.message()).contains("Kerberos refused grantforge@GAMMA.EXAMPLE");
    }
}
