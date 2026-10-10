// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Container;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.devlive.grantforge.hdfs.it.HadoopContainers.await;
import static org.devlive.grantforge.hdfs.it.HadoopContainers.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The agent in a Kerberos cluster: users sign in from keytabs, the NameNode maps their principals to short names, and
 * GrantForge's policies apply to those names next to the native permissions, with denials audited under them. The
 * DataNode transfers data only after SASL, over HTTPS only. Requires Docker; the KDC runs in this JVM.
 */
@Timeout(1200)
class HdfsAgentSecureClusterIT
{
    private static final String REALM = "EXAMPLE.COM";

    @TempDir
    Path work;

    @Test
    void enforcesPoliciesForKerberosUsersByTheirShortNamesAndNeverFallsBackToSimple() throws Exception
    {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        SimpleKdcServer kdc = new SimpleKdcServer();
        kdc.setWorkDir(work.toFile());
        kdc.setKdcRealm(REALM);
        kdc.setKdcHost("localhost");
        kdc.setAllowUdp(false);
        kdc.setKdcTcpPort(port);
        kdc.init();
        kdc.start();
        try {
            Path security = Files.createDirectories(work.resolve("security"));
            keytab(kdc, security, "nn-service.keytab", "nn/namenode");
            keytab(kdc, security, "dn-service.keytab", "dn/datanode");
            keytab(kdc, security, "http-service.keytab", "HTTP/namenode", "HTTP/datanode");
            // nn is the superuser: the NameNode signs in as nn/namenode, whose short name it is.
            for (String user : List.of("nn", "alice", "mallory")) {
                keytab(kdc, security, "user-" + user + ".keytab", user);
            }
            stores(security);

            try (SignedGrantForge grantforge = new SignedGrantForge("nn");
                 HadoopContainers cluster = HadoopContainers.secure(grantforge, "kerberos", new KerberosSetup(REALM, port, security))) {
                cluster.awaitSnapshot(1);
                success(cluster.admin("dfs", "-mkdir", "-p", "/data"));
                success(cluster.admin("dfs", "-chmod", "777", "/data"));
                success(cluster.put("hadoop", "/data/public", "public"));
                success(cluster.put("hadoop", "/data/secret", "secret"));

                // alice@EXAMPLE.COM is alice to the NameNode, so the policies written for alice apply, data included.
                assertEquals("public", read(cluster, "alice", "/data/public"));
                success(cluster.put("alice", "/data/created", "by alice"));
                assertEquals("by alice", read(cluster, "alice", "/data/created"));
                denied(cluster.alice("-cat", "/data/secret"), "GrantForge denied read for user alice");
                // mallory has no policy: strict by default, nothing is determined, so already the way to the file is refused.
                denied(cluster.user("mallory", "dfs", "-cat", "/data/public"), "for user mallory");
                denied(cluster.user("mallory", "dfs", "-cat", "/data/public"), "GrantForge denied");
                await(() -> grantforge.denied("/data/secret", "GRANTFORGE", 1));

                // A client without a ticket gets nothing: the cluster does not fall back to simple authentication.
                Container.ExecResult anonymous = cluster.withoutTicket("dfs", "-cat", "/data/public");
                assertNotEquals(0, anonymous.getExitCode(), "a client without a Kerberos ticket must be refused");
                String said = anonymous.getStdout() + anonymous.getStderr();
                assertTrue(said.contains("Client cannot authenticate via") || said.contains("SIMPLE authentication is not enabled"), said);

                // The restarted NameNode signs in from its keytab again and keeps enforcing the same policies.
                cluster.restartNameNode();
                assertEquals("public", read(cluster, "alice", "/data/public"));
                denied(cluster.alice("-cat", "/data/secret"), "GrantForge denied read for user alice");
            }
        }
        finally {
            kdc.stop();
        }
    }

    private static String read(HadoopContainers cluster, String user, String path) throws Exception
    {
        Container.ExecResult result = cluster.user(user, "dfs", "-cat", path);
        success(result);
        return result.getStdout().strip();
    }

    private static void denied(Container.ExecResult result, String reason)
    {
        assertNotEquals(0, result.getExitCode(), "the native HDFS operation unexpectedly succeeded");
        assertTrue((result.getStdout() + result.getStderr()).contains(reason), result.getStdout() + result.getStderr());
    }

    /** A keytab with exactly these principals, as each daemon and user of a cluster gets its own. */
    private static void keytab(SimpleKdcServer kdc, Path security, String name, String... principals) throws Exception
    {
        File file = security.resolve(name).toFile();
        for (String principal : principals) {
            kdc.createPrincipal(principal + "@" + REALM);
            kdc.exportPrincipal(principal + "@" + REALM, file);
        }
    }

    /** A self-signed certificate for both daemons, with the JDK's keytool, and a truststore holding it. */
    private static void stores(Path security) throws IOException, InterruptedException
    {
        Path keys = security.resolve("keystore.p12");
        Path certificate = security.resolve("cluster.crt");
        Path trust = security.resolve("truststore.jks");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        String password = KerberosSetup.STORE_PASSWORD;
        keytool(List.of(keytool, "-genkeypair", "-alias", "cluster", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=namenode", "-ext", "san=dns:namenode,dns:datanode,dns:localhost", "-keystore", keys.toString(),
                "-storetype", "PKCS12", "-storepass", password, "-keypass", password));
        keytool(List.of(keytool, "-exportcert", "-alias", "cluster", "-keystore", keys.toString(), "-storepass", password,
                "-file", certificate.toString()));
        keytool(List.of(keytool, "-importcert", "-noprompt", "-alias", "cluster", "-file", certificate.toString(), "-keystore",
                trust.toString(), "-storetype", "JKS", "-storepass", password));
        // Only the stores go to the daemons.
        Files.delete(certificate);
    }

    private static void keytool(List<String> command) throws IOException, InterruptedException
    {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IOException("keytool failed: " + output);
        }
    }
}
