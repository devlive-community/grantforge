// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.client.ZKClientConfig;
import org.devlive.grantforge.plugin.api.BrowseEntry;
import org.devlive.grantforge.plugin.api.BrowsePage;
import org.devlive.grantforge.plugin.api.BrowseRequest;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/** Exercises the actual plugin against TLS servers, a quorum and a server which stops answering an established session. */
@Timeout(300)
class ZooKeeperPluginIT
{
    private static final DockerImageName IMAGE = DockerImageName.parse("zookeeper:3.9.5");
    private static final Path LOGS = Path.of("target/zookeeper-testcontainers");

    @TempDir
    static Path files;

    private static ZooKeeperTlsCertificates certificates;
    private final ZooKeeperProvider provider = new ZooKeeperProvider();

    @BeforeAll
    static void certificates() throws Exception
    {
        try {
            certificates = new ZooKeeperTlsCertificates(files.resolve("certificates"));
        }
        finally {
            Files.createDirectories(LOGS);
            Path log = files.resolve("certificates/keytool.log");
            if (Files.exists(log)) {
                Files.copy(log, LOGS.resolve("keytool.log"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Test
    void tlsDigestAclChrootAndPagingUseTheRealPlugin() throws Exception
    {
        try (GenericContainer<?> server = tlsServer("tls", certificates.serverStore(), false)) {
            try {
                server.start();
                ServiceConfig alice = digest(tlsConfig(server, false));
                seed(alice);
                ServiceConfig rooted = with(alice, ZooKeeperProvider.CONNECT, endpoint(server, 2281) + "/tenant",
                        ZooKeeperProvider.LOOKUP_ROOT, "/catalog");
                assertThat(provider.validateConfig(rooted)).isEmpty();
                assertThat(provider.testConnection(rooted).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
                assertThat(lookup(rooted, "")).containsExactly("/catalog/c0", "/catalog/c1", "/catalog/c2", "/catalog/c3", "/catalog/c4");
                assertThat(browseAll(rooted)).containsExactly("/catalog/c0", "/catalog/c1", "/catalog/c2", "/catalog/c3", "/catalog/c4");
                BrowseEntry child = provider.browse(new BrowseRequest(rooted, "path", "", null, 2)).entries().get(0);
                assertThat(child.permission()).startsWith("digest:alice:").endsWith(":cdrwa");
                assertThat(child.size()).isEqualTo(1L);
                assertThat(child.modifiedAt()).isNotNull();
                assertReason(with(rooted, ZooKeeperProvider.PASSWORD, "wrong"), LookupException.Reason.ACCESS_DENIED);
                assertReason(with(rooted, ZooKeeperProvider.AUTHENTICATION, "none"), LookupException.Reason.ACCESS_DENIED);
                assertReason(with(rooted, ZooKeeperProvider.TRUSTSTORE, certificates.wrongTrustStore().toString()),
                        LookupException.Reason.UNREACHABLE);
            }
            finally {
                saveLogs(server, "tls");
            }
        }
        try (GenericContainer<?> wrongHost = tlsServer("wrong-host", certificates.wrongHostStore(), false)) {
            try {
                wrongHost.start();
                ServiceConfig service = tlsConfig(wrongHost, false);
                // The same CA, store passwords and live server work when only hostname checking is disabled in this control client.
                provesTrustedServerWithDifferentHostname(service);
                assertReason(service, LookupException.Reason.UNREACHABLE);
            }
            finally {
                saveLogs(wrongHost, "wrong-host");
            }
        }
    }

    @Test
    void mutualTlsRequiresAClientCertificateAndKeepsWhitespacePasswords() throws Exception
    {
        try (GenericContainer<?> server = tlsServer("mutual-tls", certificates.serverStore(), true)) {
            try {
                server.start();
                ServiceConfig service = tlsConfig(server, true);
                assertThat(service.get(ZooKeeperProvider.KEYSTORE_PASSWORD)).isEqualTo("  tls-password  ");
                assertThat(service.get(ZooKeeperProvider.TRUSTSTORE_PASSWORD)).isEqualTo("  tls-password  ");
                assertThat(provider.testConnection(service).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
                assertThat(lookup(service, "")).contains("/zookeeper");
                assertReason(tlsConfig(server, false), LookupException.Reason.UNREACHABLE);
            }
            finally {
                saveLogs(server, "mutual-tls");
            }
        }
    }

    @Test
    void electsAnotherLeaderAndFailsWithoutAMajority() throws Exception
    {
        String peers = "server.1=zk-1:2888:3888;2181\nserver.2=zk-2:2888:3888;2181\nserver.3=zk-3:2888:3888;2181\n";
        try (Network network = Network.newNetwork();
                GenericContainer<?> first = ensembleMember(network, 1, peers);
                GenericContainer<?> second = ensembleMember(network, 2, peers);
                GenericContainer<?> third = ensembleMember(network, 3, peers)) {
            List<GenericContainer<?>> nodes = List.of(first, second, third);
            try {
                Startables.deepStart(nodes).get(120, TimeUnit.SECONDS);
                GenericContainer<?> leader = leader(nodes);
                ServiceConfig service = config(nodes.stream().map(node -> endpoint(node, 2181)).collect(Collectors.joining(",")));
                new ZooKeeperClient(service).run(client -> client.create("/available", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));
                assertThat(lookup(service, "")).contains("/available");
                stop(leader);
                List<GenericContainer<?>> remaining = nodes.stream().filter(node -> node != leader).toList();
                GenericContainer<?> elected = leader(remaining);
                assertThat(elected.getContainerId()).isNotEqualTo(leader.getContainerId());
                assertThat(lookup(service, "")).contains("/available");
                assertThat(provider.browse(new BrowseRequest(service, "path", "/", null, 10)).entries())
                        .extracting(BrowseEntry::value).contains("/available");
                stop(elected);
                GenericContainer<?> survivor = remaining.stream().filter(node -> node != elected).findFirst().orElseThrow();
                awaitNoQuorum(survivor);
                long start = System.nanoTime();
                assertReason(service, LookupException.Reason.UNREACHABLE);
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(15));
            }
            finally {
                for (int index = 0; index < nodes.size(); index++) {
                    saveLogs(nodes.get(index), "ensemble-" + (index + 1));
                }
            }
        }
    }

    @Test
    void boundsARequestAfterTheConnectedServerStopsAnswering() throws Exception
    {
        try (GenericContainer<?> server = server("paused", baseConfig(), 2181)) {
            try {
                server.start();
                ServiceConfig service = config(endpoint(server, 2181));
                new ZooKeeperClient(service).run(client -> {
                    assertThat(client.exists("/", false)).isNotNull();
                    server.getDockerClient().pauseContainerCmd(server.getContainerId()).exec();
                    try {
                        long start = System.nanoTime();
                        assertThatThrownBy(() -> client.getChildren("/", false))
                                .isInstanceOfSatisfying(KeeperException.RequestTimeoutException.class,
                                        timeout -> assertThat(ZooKeeperProvider.failure(timeout).getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
                        // The configured deadline is one second; allow scheduler overhead, far below the 15-second session timeout.
                        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
                    }
                    finally {
                        server.getDockerClient().unpauseContainerCmd(server.getContainerId()).exec();
                    }
                    return true;
                });
                assertThat(provider.testConnection(service).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            }
            finally {
                saveLogs(server, "paused");
            }
        }
    }

    private void seed(ServiceConfig service) throws Exception
    {
        new ZooKeeperClient(service).run(client -> {
            client.create("/tenant", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            client.create("/tenant/catalog", new byte[0], ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
            for (int index = 0; index < 5; index++) {
                client.create("/tenant/catalog/c" + index, new byte[] {(byte) index}, ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.PERSISTENT);
            }
            return true;
        });
    }

    private List<String> browseAll(ServiceConfig service)
    {
        List<String> entries = new ArrayList<>();
        @Nullable String cursor = null;
        do {
            assertThat(entries).hasSizeLessThan(10);
            BrowsePage page = provider.browse(new BrowseRequest(service, "path", "", cursor, 2));
            assertThat(page.root()).isEqualTo("/catalog");
            assertThat(page.entries()).hasSizeLessThanOrEqualTo(2);
            entries.addAll(page.entries().stream().map(BrowseEntry::value).toList());
            cursor = page.nextCursor();
        }
        while (cursor != null);
        assertThat(entries).doesNotHaveDuplicates();
        return entries;
    }

    private List<String> lookup(ServiceConfig service, String typed)
    {
        return provider.lookup(new LookupRequest(service, "path", typed, Map.of(), 50));
    }

    private void assertReason(ServiceConfig service, LookupException.Reason reason)
    {
        assertThatThrownBy(() -> lookup(service, "")).isInstanceOfSatisfying(LookupException.class,
                failure -> assertThat(failure.getReason()).isEqualTo(reason));
    }

    private static void provesTrustedServerWithDifferentHostname(ServiceConfig service) throws Exception
    {
        ZKClientConfig settings = new ZooKeeperClient(service).settings();
        settings.setProperty("zookeeper.ssl.hostnameVerification", "false");
        CountDownLatch ready = new CountDownLatch(1);
        try (ZooKeeper client = new ZooKeeper(service.require(ZooKeeperProvider.CONNECT), 15_000, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                ready.countDown();
            }
        }, settings)) {
            assertThat(ready.await(5, TimeUnit.SECONDS)).as("same-CA wrong-SAN control connection").isTrue();
            assertThat(client.getChildren("/", false)).contains("zookeeper");
        }
    }

    private static ServiceConfig config(String connect)
    {
        return new ServiceConfig("zookeeper-it", Map.of(ZooKeeperProvider.CONNECT, connect, ZooKeeperProvider.AUTHENTICATION, "none",
                ZooKeeperProvider.CONNECT_TIMEOUT, "5000", ZooKeeperProvider.REQUEST_TIMEOUT, "1000", ZooKeeperProvider.SESSION_TIMEOUT, "15000"));
    }

    private static ServiceConfig digest(ServiceConfig service)
    {
        return with(service, ZooKeeperProvider.AUTHENTICATION, "digest", ZooKeeperProvider.USER, "alice", ZooKeeperProvider.PASSWORD, "s3cret");
    }

    private static ServiceConfig tlsConfig(GenericContainer<?> server, boolean mutual)
    {
        ServiceConfig service = with(config(endpoint(server, 2281)), ZooKeeperProvider.SECURE, "true",
                ZooKeeperProvider.TRUSTSTORE, certificates.trustStore().toString(), ZooKeeperProvider.TRUSTSTORE_TYPE, "pkcs12",
                ZooKeeperProvider.TRUSTSTORE_PASSWORD, ZooKeeperTlsCertificates.CLIENT_PASSWORD);
        return mutual ? with(service, ZooKeeperProvider.KEYSTORE, certificates.clientStore().toString(),
                ZooKeeperProvider.KEYSTORE_TYPE, "pkcs12", ZooKeeperProvider.KEYSTORE_PASSWORD, ZooKeeperTlsCertificates.CLIENT_PASSWORD) : service;
    }

    private static ServiceConfig with(ServiceConfig service, String... pairs)
    {
        Map<String, String> values = new HashMap<>(service.values());
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(pairs[index], pairs[index + 1]);
        }
        return new ServiceConfig(service.serviceName(), values);
    }

    private static GenericContainer<?> tlsServer(String name, Path keyStore, boolean mutual) throws IOException
    {
        String config = baseConfig() + "secureClientPort=2281\nserverCnxnFactory=org.apache.zookeeper.server.NettyServerCnxnFactory\n"
                + "ssl.keyStore.location=/certificates/server.p12\nssl.keyStore.password=" + ZooKeeperTlsCertificates.SERVER_PASSWORD
                + "\nssl.keyStore.type=PKCS12\nssl.trustStore.location=/certificates/trust.p12\nssl.trustStore.password="
                + ZooKeeperTlsCertificates.SERVER_PASSWORD + "\nssl.trustStore.type=PKCS12\nssl.clientHostnameVerification=false\nssl.clientAuth="
                + (mutual ? "NEED" : "NONE") + "\n";
        return server(name, config, 2181, 2281).withNetworkAliases("zk-tls")
                .withCopyFileToContainer(MountableFile.forHostPath(keyStore, 0444), "/certificates/server.p12")
                .withCopyFileToContainer(MountableFile.forHostPath(certificates.serverTrustStore(), 0444), "/certificates/trust.p12");
    }

    private static GenericContainer<?> ensembleMember(Network network, int id, String peers) throws IOException
    {
        return server("ensemble-" + id, baseConfig() + "initLimit=10\nsyncLimit=3\n" + peers, 2181)
                .withNetwork(network).withNetworkAliases("zk-" + id).withEnv("ZOO_MY_ID", Integer.toString(id));
    }

    private static GenericContainer<?> server(String name, String configuration, Integer... ports) throws IOException
    {
        Path config = files.resolve(name + ".cfg");
        Files.writeString(config, configuration, StandardCharsets.UTF_8);
        return new GenericContainer<>(IMAGE).withExposedPorts(ports).withEnv("JVMFLAGS", "-Xms32m -Xmx128m -XX:ActiveProcessorCount=2")
                .withCopyFileToContainer(MountableFile.forHostPath(config, 0444), "/conf/zoo.cfg")
                .waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofSeconds(90));
    }

    private static String baseConfig()
    {
        return "tickTime=1000\ndataDir=/data\ndataLogDir=/datalog\nclientPort=2181\nadmin.enableServer=false\n"
                + "4lw.commands.whitelist=srvr\nminSessionTimeout=2000\nmaxSessionTimeout=30000\n";
    }

    private static String endpoint(GenericContainer<?> server, int port)
    {
        return server.getHost() + ":" + server.getMappedPort(port);
    }

    private static GenericContainer<?> leader(List<GenericContainer<?>> nodes) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline) {
            List<GenericContainer<?>> leaders = new ArrayList<>();
            int followers = 0;
            for (GenericContainer<?> node : nodes) {
                String status = status(node);
                if (status.contains("Mode: leader")) {
                    leaders.add(node);
                }
                if (status.contains("Mode: follower")) {
                    followers++;
                }
            }
            if (leaders.size() == 1 && followers == nodes.size() - 1) {
                return leaders.get(0);
            }
            Thread.sleep(200);
        }
        throw new IOException("the test ensemble did not elect one leader and synchronize its followers within 45 seconds");
    }

    private static void awaitNoQuorum(GenericContainer<?> survivor) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            String status = status(survivor);
            if (!status.contains("Mode: leader") && !status.contains("Mode: follower")) {
                return;
            }
            Thread.sleep(200);
        }
        fail("the sole remaining server continued to serve without a quorum");
    }

    private static String status(GenericContainer<?> server)
    {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(server.getHost(), server.getMappedPort(2181)), 1000);
            socket.setSoTimeout(2000);
            socket.getOutputStream().write("srvr".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readNBytes(16_384), StandardCharsets.UTF_8);
        }
        catch (IOException disconnected) {
            return "not serving: " + disconnected.getClass().getSimpleName();
        }
    }

    private static void stop(GenericContainer<?> server)
    {
        // Stop only a member created by this test, retaining it until try-with-resources closes it and captures logs.
        server.getDockerClient().stopContainerCmd(server.getContainerId()).withTimeout(3).exec();
    }

    private static void saveLogs(GenericContainer<?> server, String name)
    {
        try {
            Files.createDirectories(LOGS);
            Files.writeString(LOGS.resolve(name + ".log"), server.getLogs(), StandardCharsets.UTF_8);
        }
        catch (IOException | RuntimeException unavailable) {
            System.err.println("Could not save ZooKeeper container logs: " + unavailable);
        }
    }
}
