// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.PluginDescriptor;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loads the Trino plugin from the sources and from a release's directory layout, with Trino's JDBC driver only in the
 * plugin's own class loader, and has it try a port no coordinator listens on: what a real coordinator answers is covered
 * by the plugin's own integration tests. The module is built first, as this one depends on it.
 */
class TrinoPluginTest
{
    @TempDir
    private Path installed;

    @Test
    void loadsTheBuiltModuleWithItsClients() throws IOException
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(TrinoPluginTest.class)), "no repository");
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(plugins, enabled(), calls, TrinoPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin trino = registry.plugins().stream().filter(plugin -> plugin.id().equals("trino")).findFirst().orElseThrow();
            assertThat(trino.status()).as(String.valueOf(trino.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(trino.location()).isEqualTo("grantforge-plugin-trino");
            ServiceConfig config = closedPort();
            ConnectionResult result = registry.call("trino", provider -> {
                assertThat(provider.getClass().getClassLoader()).isInstanceOf(PluginClassLoader.class);
                return provider.testConnection(config);
            });
            assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
            assertThat(result.message()).as("the driver itself answered").doesNotContain("NoClassDefFoundError", "ClassNotFoundException");
        }
    }

    @Test
    void loadsAReleaseDirectoryWithoutTheServersLibraries() throws IOException
    {
        installReleaseDirectory();
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(installed, enabled(), calls, TrinoPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin trino = registry.plugins().get(0);
            assertThat(trino.status()).as(String.valueOf(trino.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(trino.location()).isEqualTo("trino");
            assertThat(registry.serviceType("trino")).hasValueSatisfying(type -> assertThat(type.resources()).extracting(level -> level.name())
                    .contains("catalog", "schema", "table", "column", "user"));
            ServiceConfig config = closedPort();
            List<ConfigProblem> valid = registry.call("trino", provider -> provider.validateConfig(config));
            assertThat(valid).isEmpty();
            List<ConfigProblem> wrong = registry.call("trino", provider -> provider.validateConfig(new ServiceConfig("warehouse",
                    Map.of("jdbc.url", "jdbc:mysql://db"))));
            assertThat(wrong).extracting(ConfigProblem::field).containsExactly("jdbc.url", "user");
            assertThatThrownBy(() -> registry.call("trino", provider -> provider.lookup(new LookupRequest(config, "catalog", "", Map.of(), 10))))
                    .isInstanceOf(PluginCallException.class).cause().isInstanceOfSatisfying(LookupException.class,
                            failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
        }
    }

    private static ServiceConfig closedPort() throws IOException
    {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        return new ServiceConfig("warehouse", Map.of("jdbc.url", "jdbc:trino://127.0.0.1:" + port, "user", "grantforge", "timeout", "2000"));
    }

    private void installReleaseDirectory() throws IOException
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(TrinoPluginTest.class)), "no repository");
        Path built = plugins.resolve("grantforge-plugin-trino/target");
        Path release = Files.createDirectories(installed.resolve("trino"));
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
