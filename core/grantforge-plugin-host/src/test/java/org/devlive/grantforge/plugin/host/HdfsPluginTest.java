// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.PluginDescriptor;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loads the HDFS plugin from the sources (D-89) and from a release's directory layout, with Hadoop's client in an
 * isolated class loader, then connects and looks up paths through its local and WebHDFS file systems. The module is
 * built first, as this one depends on it.
 */
class HdfsPluginTest
{
    @TempDir
    private Path files;

    @TempDir
    private Path installed;

    @Test
    void loadsTheBuiltModuleWithHadoopsClient() throws Exception
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(HdfsPluginTest.class)), "no repository");
        Files.createDirectories(files.resolve("data/sales"));
        // A local file system stands in for a cluster: the same Hadoop client code lists it.
        ServiceConfig config = new ServiceConfig("lake", Map.of("fs.default.name", "file:///", "username", "hdfs"));
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(plugins, enabled(), calls, HdfsPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin hdfs = registry.plugins().stream().filter(plugin -> plugin.id().equals("hdfs")).findFirst().orElseThrow();
            assertThat(hdfs.status()).as(String.valueOf(hdfs.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(hdfs.location()).isEqualTo("grantforge-plugin-hdfs");
            ConnectionResult connected = registry.call("hdfs", provider -> {
                assertThat(provider.getClass().getClassLoader()).isInstanceOf(PluginClassLoader.class);
                return provider.testConnection(config);
            });
            assertThat(connected.status()).as(String.valueOf(connected.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            String data = files.resolve("data").toString();
            List<String> found = registry.call("hdfs", provider -> provider.lookup(new LookupRequest(config, "path", data + "/s", Map.of(), 10)));
            assertThat(found).containsExactly(data + "/sales");
        }
    }

    @Test
    void loadsAReleaseDirectoryAndUsesWebHdfsWithoutTheServersLibraries() throws Exception
    {
        installReleaseDirectory();
        try (WebHdfs namenode = new WebHdfs();
             PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(installed, enabled(), calls, HdfsPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin hdfs = registry.plugins().get(0);
            assertThat(hdfs.status()).as(String.valueOf(hdfs.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(hdfs.location()).isEqualTo("hdfs");
            assertThat(registry.serviceType("hdfs")).hasValueSatisfying(type -> {
                assertThat(type.resources()).singleElement().satisfies(path -> {
                    assertThat(path.name()).isEqualTo("path");
                    assertThat(path.matcher()).isEqualTo(MatcherType.PATH);
                    assertThat(path.lookupSupported()).isTrue();
                    assertThat(path.recursiveSupported()).isTrue();
                });
                assertThat(type.accessTypes()).extracting(access -> access.name()).contains("read", "write", "execute");
            });
            ServiceConfig allowed = namenode.config("hdfs");
            ConnectionResult connected = registry.call("hdfs", provider -> provider.testConnection(allowed));
            assertThat(connected.status()).as(String.valueOf(connected.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            assertThat(lookup(registry, allowed, "/data/s", 2)).containsExactly("/data/sales", "/data/support");
            assertThat(lookup(registry, allowed, "/data/销", 20)).containsExactly("/data/销售");
            assertThat(lookup(registry, allowed, "/missing/", 20)).isEmpty();

            ServiceConfig refused = namenode.config("nobody");
            ConnectionResult failed = registry.call("hdfs", provider -> provider.testConnection(refused));
            assertThat(failed.status()).isEqualTo(ConnectionResult.Status.FAILED);
            assertThat(failed.message()).contains("Permission denied");
            assertThatThrownBy(() -> lookup(registry, refused, "/data/", 20))
                    .isInstanceOf(PluginCallException.class).hasMessageContaining("Permission denied");
            assertThat(registry.call("hdfs", provider -> provider.testConnection(allowed)).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            assertThat(registry.plugins().get(0).status()).isEqualTo(PluginStatus.ACTIVE);
            assertThat(namenode.requests).anyMatch(query -> query.contains("user.name=hdfs"))
                    .anyMatch(query -> query.contains("user.name=nobody"));
        }
    }

    private static List<String> lookup(PluginRegistry registry, ServiceConfig config, String text, int limit)
    {
        return registry.call("hdfs", provider -> provider.lookup(new LookupRequest(config, "path", text, Map.of(), limit)));
    }

    private void installReleaseDirectory() throws IOException
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(HdfsPluginTest.class)), "no repository");
        Path built = plugins.resolve("grantforge-plugin-hdfs/target");
        Path release = Files.createDirectories(installed.resolve("hdfs"));
        Files.copy(built.resolve("classes").resolve(PluginDescriptor.FILE_NAME), release.resolve(PluginDescriptor.FILE_NAME));
        copyTree(built.resolve("classes"), release.resolve("classes"));
        copyTree(built.resolve("plugin-lib"), release.resolve("lib"));
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

    /** A protocol fixture; the plugin must load its own Hadoop and JSON libraries to read it. */
    private static final class WebHdfs
            implements AutoCloseable
    {
        private final HttpServer server;
        private final List<String> requests = new CopyOnWriteArrayList<>();

        private WebHdfs() throws IOException
        {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/webhdfs/v1", this::handle);
            server.start();
        }

        private ServiceConfig config(String user)
        {
            return new ServiceConfig("lake", Map.of("fs.default.name", "webhdfs://127.0.0.1:" + server.getAddress().getPort(),
                    "username", user));
        }

        private void handle(HttpExchange exchange) throws IOException
        {
            String query = requireNonNull(exchange.getRequestURI().getRawQuery());
            requests.add(query);
            if (query.contains("user.name=nobody")) {
                respond(exchange, 403, remote("AccessControlException", "org.apache.hadoop.security.AccessControlException", "Permission denied"));
                return;
            }
            String path = exchange.getRequestURI().getPath().substring("/webhdfs/v1".length());
            boolean root = path.isEmpty() || "/".equals(path);
            boolean data = "/data".equals(path) || "/data/".equals(path);
            if (!root && !data) {
                respond(exchange, 404, remote("FileNotFoundException", "java.io.FileNotFoundException", "File does not exist"));
                return;
            }
            if (query.contains("op=GETFILESTATUS")) {
                respond(exchange, 200, "{\"FileStatus\":" + status("", "DIRECTORY") + "}");
                return;
            }
            String children = root ? status("data", "DIRECTORY") : status("summary.csv", "FILE") + ","
                    + status("support", "DIRECTORY") + "," + status("sales", "DIRECTORY") + "," + status("销售", "DIRECTORY");
            String statuses = "{\"FileStatuses\":{\"FileStatus\":[" + children + "]}}";
            if (query.contains("op=LISTSTATUS_BATCH")) {
                respond(exchange, 200, "{\"DirectoryListing\":{\"partialListing\":" + statuses + ",\"remainingEntries\":0}}");
            }
            else {
                respond(exchange, 200, statuses);
            }
        }

        private static String remote(String type, String className, String message)
        {
            return "{\"RemoteException\":{\"exception\":\"" + type + "\",\"javaClassName\":\"" + className + "\",\"message\":\"" + message + "\"}}";
        }

        private static String status(String name, String type)
        {
            return "{\"pathSuffix\":\"" + name + "\",\"type\":\"" + type + "\",\"length\":0,\"owner\":\"hdfs\",\"group\":\"supergroup\","
                    + "\"permission\":\"755\",\"accessTime\":0,\"modificationTime\":0,\"blockSize\":134217728,\"replication\":0,"
                    + "\"fileId\":16386,\"childrenNum\":0,\"storagePolicy\":0}";
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException
        {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        @Override
        public void close()
        {
            server.stop(0);
        }
    }
}
