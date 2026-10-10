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
 * Automatic failover in a Kerberos cluster: the NameNodes, JournalNodes and DataNode sign in from their keytabs, the
 * ZKFCs authenticate to ZooKeeper with SASL, and the election's znodes belong to the NameNodes' principal alone. The
 * agents keep enforcing their policies for Kerberos users across the switch. Requires Docker.
 */
@Timeout(1200)
class HdfsAgentSecureAutomaticHaIT
{
    @TempDir
    Path work;

    @Test
    void movesTheActiveRoleThroughSaslAuthenticatedZooKeeperAndKeepsEnforcing() throws Exception
    {
        List<String> hosts = List.of("nn1", "nn2", "journal1", "journal2", "journal3", "datanode");
        try (ClusterKdc kdc = new ClusterKdc(work, hosts);
             SignedGrantForge grantforge = new SignedGrantForge("nn");
             HadoopContainers cluster = HadoopContainers.secureAutomaticHa(grantforge, "kerberos-automatic-ha", kdc.setup())) {
            cluster.awaitSnapshot(1);
            success(cluster.admin("dfs", "-mkdir", "-p", "/data"));
            success(cluster.admin("dfs", "-chmod", "777", "/data"));
            success(cluster.put("hadoop", "/data/public", "public"));
            success(cluster.put("hadoop", "/data/secret", "secret"));
            assertEquals("public", read(cluster, "/data/public"));
            denied(cluster.alice("-cat", "/data/secret"));
            await(() -> grantforge.deniedOn("tc-nn1", "/data/secret", 1));

            // Only the NameNodes' principal may touch the election: an unauthenticated ZooKeeper client cannot even read it.
            Container.ExecResult election = cluster.zookeeper("ls", "/hadoop-ha/grantforge-ha");
            String said = election.getStdout() + election.getStderr();
            assertTrue(said.contains("Insufficient permission") || said.contains("NoAuth"), said);

            // Losing the active NameNode: its ZKFC's session ends, and the other ZKFC takes the role over.
            cluster.kill("nn1");
            cluster.awaitActive("nn2");
            assertEquals("public", read(cluster, "/data/public"));
            denied(cluster.alice("-cat", "/data/secret"));
            await(() -> grantforge.deniedOn("tc-nn2", "/data/secret", 1));
            success(cluster.put("alice", "/data/after-failover", "second namenode"));

            // Still no way in without a ticket, whichever NameNode is active.
            Container.ExecResult anonymous = cluster.withoutTicket("dfs", "-cat", "/data/public");
            assertNotEquals(0, anonymous.getExitCode(), "a client without a Kerberos ticket must be refused");

            // nn1 returns as the standby, signing in from its keytab again, and takes the role back.
            cluster.start("nn1");
            cluster.awaitState("nn1", "standby");
            cluster.failover("nn2", "nn1");
            assertEquals("second namenode", read(cluster, "/data/after-failover"));
            denied(cluster.alice("-cat", "/data/secret"));
        }
    }

    private static String read(HadoopContainers cluster, String path) throws Exception
    {
        Container.ExecResult result = cluster.alice("-cat", path);
        success(result);
        return result.getStdout().strip();
    }

    private static void denied(Container.ExecResult result)
    {
        assertNotEquals(0, result.getExitCode(), "the native HDFS operation unexpectedly succeeded");
        assertTrue((result.getStdout() + result.getStderr()).contains("GrantForge denied"), result.getStdout() + result.getStderr());
    }
}
