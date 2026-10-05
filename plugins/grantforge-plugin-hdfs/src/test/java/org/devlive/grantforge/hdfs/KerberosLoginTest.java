// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Signs the lookup user in against a real KDC, with a keytab and with a password, as a Kerberos cluster needs. A local
 * file system stands in for the cluster, which Hadoop lists the same way once the user is signed in.
 */
class KerberosLoginTest
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String PASSWORD_PRINCIPAL = "grantforge@" + REALM;
    private static final String KEYTAB_PRINCIPAL = "lookup@" + REALM;
    private static final String SECRET = "kerberos-secret";

    @TempDir
    static Path work;

    private static SimpleKdcServer kdc;
    private static File keytab;

    private final HdfsProvider provider = new HdfsProvider();

    @BeforeAll
    static void startKdc() throws Exception
    {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        kdc = new SimpleKdcServer();
        kdc.setWorkDir(work.toFile());
        kdc.setKdcRealm(REALM);
        kdc.setKdcHost("localhost");
        kdc.setAllowUdp(false);
        kdc.setKdcTcpPort(port);
        kdc.init();
        kdc.start();
        // The export takes every principal there is, so the password principal comes after it.
        keytab = work.resolve("lookup.keytab").toFile();
        kdc.createAndExportPrincipals(keytab, KEYTAB_PRINCIPAL);
        kdc.createPrincipal(PASSWORD_PRINCIPAL, SECRET);
        // This JVM is the test class's own (surefire does not reuse forks here), so the setting cannot leak.
        System.setProperty("java.security.krb5.conf", work.resolve("krb5.conf").toString());
    }

    @AfterAll
    static void stopKdc() throws Exception
    {
        kdc.stop();
    }

    private static ServiceConfig kerberos(String user, String... more)
    {
        Map<String, String> values = new java.util.HashMap<>(Map.of("fs.default.name", "file:///", "username", user,
                "hadoop.security.authentication", "kerberos"));
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig("secure-lake", values);
    }

    @Test
    void signsInWithAKeytab() throws IOException
    {
        Files.createDirectories(work.resolve("data/sales"));
        ServiceConfig config = kerberos(KEYTAB_PRINCIPAL, "keytab", keytab.getPath());

        assertThat(provider.testConnection(config)).isEqualTo(ConnectionResult.succeeded());
        String data = work.resolve("data").toString();
        assertThat(provider.lookup(new LookupRequest(config, "path", data + "/", Map.of(), 10))).containsExactly(data + "/sales");
    }

    @Test
    void signsInWithAPassword()
    {
        assertThat(provider.testConnection(kerberos(PASSWORD_PRINCIPAL, "password", SECRET))).isEqualTo(ConnectionResult.succeeded());
    }

    @Test
    void reportsRefusedCredentials()
    {
        ConnectionResult wrong = provider.testConnection(kerberos(PASSWORD_PRINCIPAL, "password", "not the secret"));
        assertThat(wrong.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(wrong.message()).contains("Kerberos refused " + PASSWORD_PRINCIPAL);
        ConnectionResult otherKeytab = provider.testConnection(kerberos(PASSWORD_PRINCIPAL, "keytab", keytab.getPath()));
        assertThat(otherKeytab.status()).isEqualTo(ConnectionResult.Status.FAILED);
    }
}
