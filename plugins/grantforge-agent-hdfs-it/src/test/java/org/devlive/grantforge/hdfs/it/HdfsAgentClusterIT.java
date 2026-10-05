// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.MiniDFSNNTopology;
import org.apache.hadoop.hdfs.server.namenode.ha.HATestUtil;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real RPC and DataNode operations with the release jar loaded through Hadoop's NameNode extension point. */
@Timeout(120)
class HdfsAgentClusterIT
{
    private static final String PROVIDER = "org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider";

    @TempDir
    java.nio.file.Path temporary;

    @Test
    void enforcesSignedPoliciesAlongsideNativeChecksAndKeepsThemAfterAnOfflineRestart() throws Exception
    {
        assertReleaseJar();
        String owner = UserGroupInformation.getCurrentUser().getShortUserName();
        try (SignedGrantForge grantforge = new SignedGrantForge(owner)) {
            Configuration configuration = configuration(grantforge, false);
            try (MiniDFSCluster cluster = new MiniDFSCluster.Builder(configuration).numDataNodes(1).build()) {
                cluster.waitActive();
                await(() -> Files.isRegularFile(temporary.resolve("cache/snapshot.properties")) && grantforge.downloads() > 0);
                try (DistributedFileSystem administrator = cluster.getFileSystem()) {
                    Path data = new Path("/data");
                    assertTrue(administrator.mkdirs(data));
                    administrator.setPermission(data, new FsPermission((short) 0777));
                    create(administrator, "/data/public", "public");
                    create(administrator, "/data/secret", "secret");
                    create(administrator, "/data/protected", "protected");
                    create(administrator, "/data/native-denied", "native");
                    administrator.setPermission(new Path("/data/native-denied"), new FsPermission((short) 0000));
                    assertTrue(administrator.mkdirs(new Path("/data/private")));
                    administrator.setPermission(new Path("/data/private"), new FsPermission((short) 0777));
                    create(administrator, "/data/private/child", "child");
                    administrator.allowSnapshot(data);
                    administrator.createSnapshot(data, "s1");

                    try (DistributedFileSystem alice = alice(cluster)) {
                        assertEquals("public", read(alice, "/data/public"));
                        create(alice, "/data/created", "first");
                        try (FSDataOutputStream output = alice.append(new Path("/data/created"))) {
                            output.write(" second".getBytes(StandardCharsets.UTF_8));
                        }
                        assertEquals("first second", read(alice, "/data/created"));
                        assertTrue(alice.rename(new Path("/data/created"), new Path("/data/renamed")));
                        assertEquals("first second", read(alice, "/data/renamed"));
                        assertTrue(alice.delete(new Path("/data/renamed"), false));
                        assertFalse(administrator.exists(new Path("/data/renamed")));

                        denied(() -> read(alice, "/data/secret"), "GrantForge");
                        denied(() -> read(alice, "/data/native-denied"), "Permission denied");
                        denied(() -> create(alice, "/data/blocked", "blocked"), "GrantForge");
                        assertFalse(administrator.exists(new Path("/data/blocked")));
                        denied(() -> alice.rename(new Path("/data/public"), new Path("/data/blocked")), "GrantForge");
                        assertTrue(administrator.exists(new Path("/data/public")));
                        denied(() -> alice.delete(new Path("/data/protected"), false), "GrantForge");
                        denied(() -> alice.delete(new Path("/data/private"), true), "GrantForge");
                        assertTrue(administrator.exists(new Path("/data/private/child")));
                        assertEquals("public", read(alice, "/data/.snapshot/s1/public"));
                        denied(() -> read(alice, "/data/.snapshot/s1/secret"), "GrantForge");
                    }
                }
                await(() -> grantforge.denied("/data/native-denied", "NATIVE") && grantforge.denied("/data/secret", "GRANTFORGE"));

                grantforge.available(false);
                int downloaded = grantforge.downloads();
                cluster.restartNameNode();
                cluster.waitActive();
                try (DistributedFileSystem alice = alice(cluster)) {
                    assertEquals("public", read(alice, "/data/public"));
                    denied(() -> read(alice, "/data/.snapshot/s1/secret"), "GrantForge");
                }
                assertEquals(downloaded, grantforge.downloads(), "the restart must use the previously signed local snapshot");
            }
        }
    }

    @Test
    void deniesNativeReadableDataWithoutASnapshotUnlessFallbackWasExplicitlyConfigured() throws Exception
    {
        String owner = UserGroupInformation.getCurrentUser().getShortUserName();
        try (SignedGrantForge grantforge = new SignedGrantForge(owner)) {
            grantforge.available(false);
            Configuration configuration = configuration(grantforge, false);
            try (MiniDFSCluster cluster = new MiniDFSCluster.Builder(configuration).numDataNodes(1).build()) {
                cluster.waitActive();
                try (DistributedFileSystem alice = alice(cluster)) {
                    denied(() -> alice.listStatus(new Path("/")), "GrantForge");
                }
                assertFalse(Files.exists(temporary.resolve("cache/snapshot.properties")));
                cluster.getConfiguration(0).setBoolean("grantforge.hdfs.native.fallback", true);
                cluster.restartNameNode();
                cluster.waitActive();
                try (DistributedFileSystem alice = alice(cluster)) {
                    assertEquals(0, alice.listStatus(new Path("/")).length);
                }
                assertEquals(0, grantforge.downloads());
            }
        }
    }

    @Test
    void retainsPathPoliciesAcrossManualHaFailoverWithIndependentNameNodeCaches() throws Exception
    {
        assertReleaseJar();
        String owner = UserGroupInformation.getCurrentUser().getShortUserName();
        try (SignedGrantForge grantforge = new SignedGrantForge(owner)) {
            Configuration configuration = configuration(grantforge, false);
            configuration.set("grantforge.hdfs.instance", "integration-${dfs.ha.namenode.id}");
            configuration.set("grantforge.hdfs.cache.dir", temporary.resolve("cache-${dfs.ha.namenode.id}").toString());
            configuration.setInt("dfs.ha.tail-edits.period", 1);
            configuration.setInt("dfs.client.failover.sleep.base.millis", 100);
            configuration.setInt("dfs.client.failover.sleep.max.millis", 500);
            try (MiniDFSCluster cluster = new MiniDFSCluster.Builder(configuration).nnTopology(MiniDFSNNTopology.simpleHATopology())
                    .numDataNodes(1).build()) {
                cluster.waitActive();
                await(() -> Files.isRegularFile(temporary.resolve("cache-nn1/snapshot.properties"))
                        && Files.isRegularFile(temporary.resolve("cache-nn2/snapshot.properties"))
                        && grantforge.hasInstance("integration-nn1") && grantforge.hasInstance("integration-nn2"));
                cluster.transitionToActive(0);
                try (DistributedFileSystem administrator = cluster.getFileSystem(0)) {
                    assertTrue(administrator.mkdirs(new Path("/data")));
                    administrator.setPermission(new Path("/data"), new FsPermission((short) 0777));
                    create(administrator, "/data/public", "public");
                    create(administrator, "/data/secret", "secret");
                }
                HATestUtil.waitForStandbyToCatchUp(cluster.getNameNode(0), cluster.getNameNode(1));
                UserGroupInformation user = UserGroupInformation.createUserForTesting("alice", new String[] {"analysts"});
                try (DistributedFileSystem alice = user.doAs((PrivilegedExceptionAction<DistributedFileSystem>) () ->
                        HATestUtil.configureFailoverFs(cluster, new Configuration(cluster.getConfiguration(0))))) {
                    assertEquals("public", read(alice, "/data/public"));
                    denied(() -> read(alice, "/data/secret"), "GrantForge");
                    cluster.transitionToStandby(0);
                    cluster.transitionToActive(1);
                    assertEquals("public", read(alice, "/data/public"));
                    denied(() -> read(alice, "/data/secret"), "GrantForge");
                    create(alice, "/data/after-failover", "second namenode");
                    assertEquals("second namenode", read(alice, "/data/after-failover"));
                }
                assertTrue(grantforge.downloads() >= 2, "both NameNode agents must independently verify their signed snapshot");
            }
        }
    }

    private Configuration configuration(SignedGrantForge grantforge, boolean fallback) throws IOException
    {
        java.nio.file.Path token = Files.writeString(temporary.resolve("token"), SignedGrantForge.TOKEN);
        java.nio.file.Path key = Files.writeString(temporary.resolve("signing-key"), grantforge.publicKey());
        Configuration configuration = new HdfsConfiguration();
        configuration.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, temporary.resolve("hdfs").toString());
        configuration.set("dfs.namenode.inode.attributes.provider.class", PROVIDER);
        configuration.setBoolean("dfs.permissions.enabled", true);
        configuration.setBoolean("dfs.namenode.acls.enabled", true);
        configuration.setInt("dfs.replication", 1);
        configuration.setInt("dfs.namenode.handler.count", 2);
        configuration.setInt("dfs.datanode.handler.count", 2);
        configuration.setLong("dfs.blocksize", 1024 * 1024);
        configuration.set("grantforge.hdfs.server.url", grantforge.uri().toString());
        configuration.set("grantforge.hdfs.token.file", token.toString());
        configuration.set("grantforge.hdfs.signing.key.file", key.toString());
        configuration.set("grantforge.hdfs.instance", "integration-namenode");
        configuration.set("grantforge.hdfs.cache.dir", temporary.resolve("cache").toString());
        configuration.setLong("grantforge.hdfs.connect.timeout.ms", 1000);
        configuration.setLong("grantforge.hdfs.read.timeout.ms", 1000);
        configuration.setLong("grantforge.hdfs.refresh.interval.ms", 1000);
        configuration.setBoolean("grantforge.hdfs.native.fallback", fallback);
        return configuration;
    }

    private static DistributedFileSystem alice(MiniDFSCluster cluster) throws IOException, InterruptedException
    {
        UserGroupInformation alice = UserGroupInformation.createUserForTesting("alice", new String[] {"analysts"});
        return alice.doAs((PrivilegedExceptionAction<DistributedFileSystem>) () ->
                (DistributedFileSystem) FileSystem.newInstance(cluster.getURI(), cluster.getConfiguration(0)));
    }

    private static void assertReleaseJar() throws Exception
    {
        Class<?> provider = Class.forName(PROVIDER);
        String artifact = java.nio.file.Path.of(requireNonNull(provider.getProtectionDomain().getCodeSource()).getLocation().toURI())
                .getFileName().toString();
        assertTrue(artifact.startsWith("grantforge-agent-hdfs-") && artifact.endsWith(".jar"), "the NameNode must load the packaged agent jar");
        Class.forName("org.devlive.grantforge.hdfs.agent.internal.jackson.databind.ObjectMapper");
        Class.forName("org.devlive.grantforge.hdfs.agent.internal.bouncycastle.crypto.signers.Ed25519Signer");
    }

    private static void create(DistributedFileSystem files, String path, String text) throws IOException
    {
        try (FSDataOutputStream output = files.create(new Path(path), false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(DistributedFileSystem files, String path) throws IOException
    {
        try (org.apache.hadoop.fs.FSDataInputStream input = files.open(new Path(path))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void denied(HdfsAction operation, String reason)
    {
        IOException denied = assertThrows(IOException.class, operation::run);
        assertTrue(String.valueOf(denied.getMessage()).contains(reason), String.valueOf(denied));
    }

    private static void await(BooleanSupplier ready) throws InterruptedException
    {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(ready.getAsBoolean(), "the real NameNode agent did not complete its HTTP exchange");
    }

    @FunctionalInterface
    private interface HdfsAction
    {
        void run() throws IOException;
    }
}
