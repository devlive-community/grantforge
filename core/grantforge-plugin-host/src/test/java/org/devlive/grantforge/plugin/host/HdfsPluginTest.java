// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.net.httpserver.HttpServer;
import org.devlive.grantforge.hdfs.HdfsProvider;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.PluginDescriptor;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Installs the HDFS plugin as the release ships it, a directory with its classes and its own Jackson in lib/, and talks
 * to a WebHDFS endpoint through it: the plugin must work in its own class loader, which sees none of the server's
 * libraries.
 */
class HdfsPluginTest
{
    @TempDir
    private Path plugins;

    private static Path location(Class<?> type) throws URISyntaxException
    {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private void install() throws IOException, URISyntaxException
    {
        Path target = plugins.resolve("hdfs");
        Path built = location(HdfsProvider.class);
        Files.createDirectories(target.resolve("lib"));
        for (Class<?> library : List.of(JsonMapper.class, JsonParser.class, JsonProperty.class)) {
            Path jar = location(library);
            Files.copy(jar, target.resolve("lib").resolve(jar.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.isRegularFile(built)) {
            Files.copy(built, target.resolve("lib").resolve("grantforge-plugin-hdfs.jar"));
            try (var jar = new java.util.jar.JarFile(built.toFile())) {
                Files.copy(jar.getInputStream(jar.getEntry(PluginDescriptor.FILE_NAME)), target.resolve(PluginDescriptor.FILE_NAME));
            }
            return;
        }
        Files.copy(built.resolve(PluginDescriptor.FILE_NAME), target.resolve(PluginDescriptor.FILE_NAME));
        try (Stream<Path> files = Files.walk(built)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path copy = target.resolve("classes").resolve(built.relativize(file).toString());
                Files.createDirectories(copy.getParent());
                Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Test
    void loadsWithItsOwnJacksonAndReachesWebHdfs() throws Exception
    {
        install();
        HttpServer namenode = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        namenode.createContext("/webhdfs/v1", exchange -> {
            String body = exchange.getRequestURI().getQuery().contains("LISTSTATUS")
                    ? "{\"FileStatuses\":{\"FileStatus\":[{\"pathSuffix\":\"user\",\"type\":\"DIRECTORY\"}]}}"
                    : "{\"FileStatus\":{\"pathSuffix\":\"\",\"type\":\"DIRECTORY\"}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        namenode.start();
        PluginSwitches on = new PluginSwitches()
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
        ServiceConfig config = new ServiceConfig("lake", Map.of("url", "http://127.0.0.1:" + namenode.getAddress().getPort()));
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(10));
             PluginRegistry registry = new PluginRegistry(plugins, on, calls, HdfsPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin hdfs = registry.plugins().stream().filter(plugin -> plugin.id().equals("hdfs")).findFirst().orElseThrow();
            assertThat(hdfs.status()).as(String.valueOf(hdfs.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(registry.serviceType("hdfs")).map(type -> type.label()).contains("HDFS");
            ConnectionResult connected = registry.call("hdfs", provider -> {
                assertThat(provider.getClass().getClassLoader()).isInstanceOf(PluginClassLoader.class);
                return provider.testConnection(config);
            });
            assertThat(connected.status()).as(String.valueOf(connected.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            List<String> found = registry.call("hdfs", provider -> provider.lookup(new LookupRequest(config, "path", "/u", Map.of(), 10)));
            assertThat(found).containsExactly("/user");
        }
        finally {
            namenode.stop(0);
        }
    }
}
