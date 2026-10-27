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
 * Loads the Doris plugin from the sources and from a release's directory layout, with MariaDB Connector/J only in the plugin's own class
 * loader, and has it try a port no frontend listens on: what a real cluster answers is covered by the plugin's own integration tests. The
 * module is built first, as this one depends on it.
 */
class DorisPluginTest
{
    @TempDir
    private Path installed;

    @Test
    void loadsTheBuiltModuleWithItsClients() throws IOException
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(DorisPluginTest.class)), "no repository");
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(plugins, enabled(), calls, DorisPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin doris = registry.plugins().stream().filter(plugin -> plugin.id().equals("doris")).findFirst().orElseThrow();
            assertThat(doris.status()).as(String.valueOf(doris.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(doris.location()).isEqualTo("grantforge-plugin-doris");
            ServiceConfig config = closedPort();
            ConnectionResult result = registry.call("doris", provider -> {
                assertThat(provider.getClass().getClassLoader()).isInstanceOf(PluginClassLoader.class);
                return provider.testConnection(config);
            });
            assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
            assertThat(result.message()).as("the client itself answered").doesNotContain("NoClassDefFoundError", "ClassNotFoundException");
        }
    }

    @Test
    void loadsAReleaseDirectoryWithoutTheServersLibraries() throws IOException
    {
        installReleaseDirectory();
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(installed, enabled(), calls, DorisPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin doris = registry.plugins().get(0);
            assertThat(doris.status()).as(String.valueOf(doris.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(doris.location()).isEqualTo("doris");
            assertThat(registry.serviceType("doris")).hasValueSatisfying(type -> assertThat(type.resources()).extracting(level -> level.name())
                    .containsExactly("catalog", "database", "table", "column", "workload_group"));
            ServiceConfig config = closedPort();
            List<ConfigProblem> valid = registry.call("doris", provider -> provider.validateConfig(config));
            assertThat(valid).isEmpty();
            List<ConfigProblem> wrong = registry.call("doris", provider -> provider.validateConfig(new ServiceConfig("warehouse",
                    Map.of("doris.hosts", "fe 1", "username", "lookup", "ssl.mode", "required"))));
            assertThat(wrong).extracting(ConfigProblem::field).containsExactly("doris.hosts", "ssl.mode");
            assertThatThrownBy(() -> registry.call("doris", provider -> provider.lookup(new LookupRequest(config, "catalog", "", Map.of(), 10))))
                    .isInstanceOf(PluginCallException.class).cause().isInstanceOfSatisfying(LookupException.class,
                            failed -> assertThat(failed.getReason()).as(String.valueOf(failed.getMessage())).isEqualTo(LookupException.Reason.UNREACHABLE));
        }
    }

    private static ServiceConfig closedPort() throws IOException
    {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        return new ServiceConfig("warehouse", Map.of("doris.hosts", "127.0.0.1:" + port, "username", "lookup", "password", "secret", "timeout",
                "2000"));
    }

    private void installReleaseDirectory() throws IOException
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(DorisPluginTest.class)), "no repository");
        Path built = plugins.resolve("grantforge-plugin-doris/target");
        Path release = Files.createDirectories(installed.resolve("doris"));
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
