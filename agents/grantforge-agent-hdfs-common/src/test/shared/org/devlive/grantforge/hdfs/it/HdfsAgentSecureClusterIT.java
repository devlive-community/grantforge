// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Container;

import java.nio.file.Path;
import java.util.List;

import static org.devlive.grantforge.hdfs.it.HadoopContainers.await;
import static org.devlive.grantforge.hdfs.it.HadoopContainers.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The agent in a Kerberos cluster: users sign in from keytabs, the NameNode maps their principals to short names, and
 * GrantForge's policies apply to those names next to the native permissions, with denials audited under them. The
 * DataNode transfers data only after SASL, over HTTPS only. Requires Docker; the KDC runs in this JVM, whose Kerberos
 * client also gets the users' tickets: the image's MIT kinit fails the KDC's pre-authentication.
 */
@Timeout(1200)
class HdfsAgentSecureClusterIT
{
    @TempDir
    Path work;

    @Test
    void enforcesPoliciesForKerberosUsersByTheirShortNamesAndNeverFallsBackToSimple() throws Exception
    {
        try (ClusterKdc kdc = new ClusterKdc(work, List.of("namenode", "datanode"));
             SignedGrantForge grantforge = new SignedGrantForge("nn");
             HadoopContainers cluster = HadoopContainers.secure(grantforge, "kerberos", kdc.setup())) {
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
}
