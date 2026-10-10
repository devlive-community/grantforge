// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Container;

import static org.devlive.grantforge.hdfs.it.HadoopContainers.await;
import static org.devlive.grantforge.hdfs.it.HadoopContainers.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The agent under automatic failover: ZooKeeper elects the active NameNode, and a quorum of three JournalNodes carries
 * the edits. Each NameNode's agent keeps its own snapshot and reports its own decisions, and the role moving between
 * them never moves the policies back to an older version. Requires Docker.
 */
@Timeout(1200)
class HdfsAgentAutomaticHaIT
{
    @Test
    void keepsEnforcingTheNewestPoliciesWhileZooKeeperMovesTheActiveNameNode() throws Exception
    {
        try (SignedGrantForge grantforge = new SignedGrantForge("hadoop");
             HadoopContainers cluster = HadoopContainers.automaticHa(grantforge, "automatic-ha")) {
            cluster.awaitSnapshot(1);
            success(cluster.admin("dfs", "-mkdir", "/data"));
            success(cluster.admin("dfs", "-chmod", "777", "/data"));
            success(cluster.put("hadoop", "/data/public", "public"));
            success(cluster.put("hadoop", "/data/secret", "secret"));
            assertEquals("public", read(cluster, "/data/public"));
            denied(cluster.alice("-cat", "/data/secret"));
            await(() -> grantforge.deniedOn("tc-nn1", "/data/secret", 1));

            // Losing the active NameNode's host: its ZKFC's session ends, and the other ZKFC takes the role over.
            cluster.kill("nn1");
            cluster.awaitActive("nn2");
            assertEquals("public", read(cluster, "/data/public"));
            denied(cluster.alice("-cat", "/data/secret"));
            await(() -> grantforge.deniedOn("tc-nn2", "/data/secret", 1));
            success(cluster.put("alice", "/data/after-failover", "second namenode"));

            // The policies change while nn1 is away; on its return it takes them up as the standby.
            long revoked = grantforge.publishPublicRead(false);
            await(() -> grantforge.applied("tc-nn2", revoked));
            denied(cluster.alice("-cat", "/data/public"));
            cluster.start("nn1");
            cluster.awaitState("nn1", "standby");
            await(() -> grantforge.applied("tc-nn1", revoked));

            // Handing the role back keeps the newer policies, and the edits made on nn2.
            cluster.failover("nn2", "nn1");
            denied(cluster.alice("-cat", "/data/public"));
            await(() -> grantforge.deniedOn("tc-nn1", "/data/public", revoked));
            assertEquals("second namenode", read(cluster, "/data/after-failover"));

            // Without the policy server, nn2 takes over on the snapshot it holds, and its denials wait to be reported.
            grantforge.available(false);
            cluster.kill("nn1");
            cluster.awaitActive("nn2");
            denied(cluster.alice("-cat", "/data/secret"));
            grantforge.available(true);
            await(() -> grantforge.deniedOn("tc-nn2", "/data/secret", revoked));
            cluster.start("nn1");
            cluster.awaitState("nn1", "standby");

            // Two of three JournalNodes are a quorum: edits are still written.
            cluster.kill("journal3");
            success(cluster.put("hadoop", "/data/two-journals", "quorum"));
            assertEquals("quorum", read(cluster, "/data/two-journals"));
            // One is not: the active NameNode cannot write its edits and stops, rather than going on without them.
            cluster.kill("journal2");
            Container.ExecResult lost = cluster.put("hadoop", "/data/one-journal", "lost");
            assertNotEquals(0, lost.getExitCode(), "a write without a JournalNode quorum must fail: " + lost.getStdout());
            await(() -> cluster.logs("nn2").contains("failed for required journal"));
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
