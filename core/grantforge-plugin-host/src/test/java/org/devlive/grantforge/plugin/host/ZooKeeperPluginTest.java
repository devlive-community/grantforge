// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.server.embedded.ExitHandler;
import org.apache.zookeeper.server.embedded.ZooKeeperServerEmbedded;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.PluginDescriptor;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the ZooKeeper plugin from the sources (D-89) and from a release's directory layout, with ZooKeeper's client in
 * an isolated class loader, then connects to a ZooKeeper server and looks up znodes. The module is built first, as this
 * one depends on it.
 */
class ZooKeeperPluginTest
{
    @TempDir
    static Path data;

    @TempDir
    private Path installed;

    private static ZooKeeperServerEmbedded server;
    private static String connect;

    @BeforeAll
    static void startServer() throws Exception
    {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Properties settings = new Properties();
        settings.setProperty("clientPort", Integer.toString(port));
        settings.setProperty("clientPortAddress", "127.0.0.1");
        settings.setProperty("admin.enableServer", "false");
        server = ZooKeeperServerEmbedded.builder().baseDir(data).configuration(settings).exitHandler(ExitHandler.LOG_ONLY).build();
        server.start(30_000);
        connect = "127.0.0.1:" + port;
        CountDownLatch connected = new CountDownLatch(1);
        ZKClientConfig client = new ZKClientConfig();
        client.setProperty(ZKClientConfig.ENABLE_CLIENT_SASL_KEY, "false");
        try (ZooKeeper admin = new ZooKeeper(connect, 15_000, event -> connected.countDown(), client)) {
            assertThat(connected.await(30, TimeUnit.SECONDS)).isTrue();
            admin.create("/kafka", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            admin.create("/kafka/brokers", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            admin.create("/kafka/config", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }

    @AfterAll
    static void stopServer()
    {
        server.close();
    }

    @Test
    void loadsTheBuiltModuleWithZooKeepersClient()
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(ZooKeeperPluginTest.class)), "no repository");
        ServiceConfig config = new ServiceConfig("kafka-zk", Map.of("zookeeper.connect", connect + "/kafka"));
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(plugins, enabled(), calls, ZooKeeperPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin zookeeper = registry.plugins().stream().filter(plugin -> plugin.id().equals("zookeeper")).findFirst().orElseThrow();
            assertThat(zookeeper.status()).as(String.valueOf(zookeeper.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(zookeeper.location()).isEqualTo("grantforge-plugin-zookeeper");
            ConnectionResult connected = registry.call("zookeeper", provider -> {
                assertThat(provider.getClass().getClassLoader()).isInstanceOf(PluginClassLoader.class);
                return provider.testConnection(config);
            });
            assertThat(connected.status()).as(String.valueOf(connected.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            assertThat(lookup(registry, config, "/")).containsExactly("/brokers", "/config");
        }
    }

    @Test
    void loadsAReleaseDirectoryWithoutTheServersLibraries() throws IOException
    {
        installReleaseDirectory();
        ServiceConfig config = new ServiceConfig("zk", Map.of("zookeeper.connect", connect));
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(installed, enabled(), calls, ZooKeeperPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin zookeeper = registry.plugins().get(0);
            assertThat(zookeeper.status()).as(String.valueOf(zookeeper.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(zookeeper.location()).isEqualTo("zookeeper");
            assertThat(registry.serviceType("zookeeper")).hasValueSatisfying(type -> {
                assertThat(type.resources()).singleElement().satisfies(path -> {
                    assertThat(path.name()).isEqualTo("path");
                    assertThat(path.matcher()).isEqualTo(MatcherType.PATH);
                    assertThat(path.browseSupported()).isTrue();
                });
                assertThat(type.accessTypes()).extracting(access -> access.name()).containsExactly("read", "write", "create", "delete", "admin");
            });
            assertThat(registry.call("zookeeper", provider -> provider.testConnection(config)).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            assertThat(lookup(registry, config, "/kafka/b")).containsExactly("/kafka/brokers");
        }
    }

    private static List<String> lookup(PluginRegistry registry, ServiceConfig config, String text)
    {
        return registry.call("zookeeper", provider -> provider.lookup(new LookupRequest(config, "path", text, Map.of(), 20)));
    }

    private void installReleaseDirectory() throws IOException
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(ZooKeeperPluginTest.class)), "no repository");
        Path built = plugins.resolve("grantforge-plugin-zookeeper/target");
        Path release = Files.createDirectories(installed.resolve("zookeeper"));
        Files.copy(built.resolve("classes").resolve(PluginDescriptor.FILE_NAME), release.resolve(PluginDescriptor.FILE_NAME));
        copyTree(built.resolve("classes"), release.resolve("classes"));
        copyTree(built.resolve("plugin-lib"), release.resolve("lib"));
    }

    private static PluginSwitches enabled()
    {
        return new PluginSwitches()
        {
            @Override
            public boolean enabled(String pluginId)
            {
                return true;
            }

            @Override
            public void set(String pluginId, boolean enabled)
            {
                // Always on.
            }
        };
    }

    private static void copyTree(Path source, Path destination) throws IOException
    {
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = destination.resolve(source.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        }
    }
}
