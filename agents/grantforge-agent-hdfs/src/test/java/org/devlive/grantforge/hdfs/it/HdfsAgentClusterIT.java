// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Container;

import java.util.Map;

import static org.devlive.grantforge.hdfs.it.HadoopContainers.await;
import static org.devlive.grantforge.hdfs.it.HadoopContainers.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real NameNode/DataNode processes on a Testcontainers network; requires Docker and tests the actual released agent jar. */
@Timeout(600)
class HdfsAgentClusterIT
{
    private static final String METRICS_BEAN = "Hadoop:service=NameNode,name=GrantForgeHdfsAgent";

    @Test
    void enforcesSignedPoliciesAlongsideNativeChecksAndKeepsThemAfterAnOfflineRestart() throws Exception
    {
        try (SignedGrantForge grantforge = new SignedGrantForge("hadoop");
             HadoopContainers cluster = new HadoopContainers(grantforge, false, false, "io-refresh-cache")) {
            cluster.awaitSnapshot(1);
            success(cluster.admin("dfs", "-mkdir", "-p", "/data/private"));
            success(cluster.admin("dfs", "-chmod", "777", "/data", "/data/private"));
            success(cluster.put("hadoop", "/data/public", "public"));
            success(cluster.put("hadoop", "/data/secret", "secret"));
            success(cluster.put("hadoop", "/data/protected", "protected"));
            success(cluster.put("hadoop", "/data/native-denied", "native"));
            success(cluster.put("hadoop", "/data/private/child", "child"));
            success(cluster.admin("dfs", "-chmod", "000", "/data/native-denied"));
            success(cluster.admin("dfsadmin", "-allowSnapshot", "/data"));
            success(cluster.admin("dfs", "-createSnapshot", "/data", "s1"));

            assertEquals("public", read(cluster, "/data/public"));
            success(cluster.put("alice", "/data/created", "first"));
            cluster.append("/data/created", " second");
            assertEquals("first second", read(cluster, "/data/created"));
            success(cluster.alice("-mv", "/data/created", "/data/renamed"));
            assertEquals("first second", read(cluster, "/data/renamed"));
            success(cluster.alice("-rm", "-skipTrash", "/data/renamed"));
            assertNotEquals(0, cluster.admin("dfs", "-test", "-e", "/data/renamed").getExitCode());
            denied(cluster.alice("-cat", "/data/secret"), "GrantForge");
            denied(cluster.alice("-cat", "/data/native-denied"), "Permission denied");
            denied(cluster.put("alice", "/data/blocked", "blocked"), "GrantForge");
            assertNotEquals(0, cluster.admin("dfs", "-test", "-e", "/data/blocked").getExitCode());
            denied(cluster.alice("-mv", "/data/public", "/data/blocked"), "GrantForge");
            success(cluster.admin("dfs", "-test", "-e", "/data/public"));
            denied(cluster.alice("-rm", "-skipTrash", "/data/protected"), "GrantForge");
            denied(cluster.alice("-rm", "-r", "-skipTrash", "/data/private"), "GrantForge");
            success(cluster.admin("dfs", "-test", "-e", "/data/private/child"));
            assertEquals("public", read(cluster, "/data/.snapshot/s1/public"));
            denied(cluster.alice("-cat", "/data/.snapshot/s1/secret"), "GrantForge");
            await(() -> grantforge.denied("/data/native-denied", "NATIVE") && grantforge.denied("/data/secret", "GRANTFORGE", 1));

            long deniedVersion = grantforge.publishPublicRead(false);
            cluster.awaitSnapshot(deniedVersion);
            denied(cluster.alice("-cat", "/data/public"), "GrantForge");
            await(() -> grantforge.denied("/data/public", "GRANTFORGE", deniedVersion));
            long restoredVersion = grantforge.publishPublicRead(true);
            cluster.awaitSnapshot(restoredVersion);
            assertEquals("public", read(cluster, "/data/public"));
            await(() -> grantforge.allowed("/data/public", "GRANTFORGE", restoredVersion));

            grantforge.available(false);
            int downloaded = grantforge.downloads();
            cluster.restartNameNode();
            assertEquals("public", read(cluster, "/data/public"));
            denied(cluster.alice("-cat", "/data/.snapshot/s1/secret"), "GrantForge");
            assertEquals(downloaded, grantforge.downloads(), "the restarted container must use its previously signed local snapshot");

            // The agent reports its decisions through Hadoop's metrics system, on the same JMX beans as the NameNode.
            // The sink publishes periodically, so wait for the restarted NameNode's first published callbacks.
            await(() -> number(cluster.jmx(METRICS_BEAN), "Callbacks") >= 1);
            Map<String, Object> metrics = cluster.jmx(METRICS_BEAN);
            assertTrue(number(metrics, "DecisionsAllowed") >= 1, "the agent must count allowed policy decisions");
            assertTrue(number(metrics, "DecisionsDenied") >= 1, "the agent must count denied policy decisions");
            // The policy server is deliberately down at this point: the restarted NameNode serves its signed cache.
            assertEquals(0L, number(metrics, "ServerReachable"), "the offline policy server must show as unreachable");
            assertTrue(number(metrics, "SnapshotVersion") >= restoredVersion, "the metrics carry the applied policy version");
        }
    }

    private static long number(Map<String, Object> bean, String attribute)
    {
        Object value = bean.get(attribute);
        assertTrue(value instanceof Number, attribute + " must be reported as a number: " + value);
        return ((Number) value).longValue();
    }

    @Test
    void deniesNativeReadableDataWithoutASnapshotUnlessFallbackWasExplicitlyConfigured() throws Exception
    {
        try (SignedGrantForge grantforge = new SignedGrantForge("hadoop")) {
            grantforge.available(false);
            try (HadoopContainers cluster = new HadoopContainers(grantforge, false, false, "strict-fallback")) {
                denied(cluster.alice("-ls", "/"), "GrantForge");
                cluster.nativeFallback(true);
                success(cluster.alice("-ls", "/"));
                assertEquals(0, grantforge.downloads());
            }
        }
    }

    @Test
    void retainsPathPoliciesAcrossManualHaFailoverWithIndependentNameNodeCaches() throws Exception
    {
        // One JournalNode proves functional switching; quorum fault tolerance and ZK automatic failover are separate concerns.
        try (SignedGrantForge grantforge = new SignedGrantForge("hadoop");
             HadoopContainers cluster = new HadoopContainers(grantforge, false, true, "manual-ha")) {
            cluster.awaitSnapshot(1);
            success(cluster.admin("dfs", "-mkdir", "/data"));
            success(cluster.admin("dfs", "-chmod", "777", "/data"));
            success(cluster.put("hadoop", "/data/public", "public"));
            success(cluster.put("hadoop", "/data/secret", "secret"));
            assertEquals("public", read(cluster, "/data/public"));
            denied(cluster.alice("-cat", "/data/secret"), "GrantForge");
            cluster.failover();
            assertEquals("public", read(cluster, "/data/public"));
            denied(cluster.alice("-cat", "/data/secret"), "GrantForge");
            success(cluster.put("alice", "/data/after-failover", "second namenode"));
            assertEquals("second namenode", read(cluster, "/data/after-failover"));
            assertTrue(grantforge.downloads() >= 2, "both NameNode agents must independently verify their signed snapshot");
        }
    }

    private static String read(HadoopContainers cluster, String path) throws Exception
    {
        Container.ExecResult result = cluster.alice("-cat", path);
        success(result);
        return result.getStdout().strip();
    }

    private static void denied(Container.ExecResult result, String reason)
    {
        assertNotEquals(0, result.getExitCode(), "the native HDFS operation unexpectedly succeeded");
        assertTrue((result.getStdout() + result.getStderr()).contains(reason), result.getStdout() + result.getStderr());
    }
}
