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
 * Loads the OpenSearch plugin from the sources and from a release's directory layout, with OpenSearch's REST client only in the plugin's
 * own class loader, and has it try a port no node listens on: what a real server answers is covered by the plugin's own integration
 * tests. The module is built first, as this one depends on it.
 */
class OpenSearchPluginTest
{
    @TempDir
    private Path installed;

    @Test
    void loadsTheBuiltModuleWithItsClients() throws IOException
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(OpenSearchPluginTest.class)), "no repository");
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(plugins, enabled(), calls, OpenSearchPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin opensearch = registry.plugins().stream().filter(plugin -> plugin.id().equals("opensearch")).findFirst().orElseThrow();
            assertThat(opensearch.status()).as(String.valueOf(opensearch.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(opensearch.location()).isEqualTo("grantforge-plugin-opensearch");
            ServiceConfig config = closedPort();
            ConnectionResult result = registry.call("opensearch", provider -> {
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
             PluginRegistry registry = new PluginRegistry(installed, enabled(), calls, OpenSearchPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin opensearch = registry.plugins().get(0);
            assertThat(opensearch.status()).as(String.valueOf(opensearch.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(opensearch.location()).isEqualTo("opensearch");
            assertThat(registry.serviceType("opensearch")).hasValueSatisfying(type -> assertThat(type.resources()).extracting(level -> level.name())
                    .containsExactly("index", "field", "cluster"));
            ServiceConfig config = closedPort();
            List<ConfigProblem> valid = registry.call("opensearch", provider -> provider.validateConfig(config));
            assertThat(valid).isEmpty();
            List<ConfigProblem> wrong = registry.call("opensearch", provider -> provider.validateConfig(new ServiceConfig("warehouse",
                    Map.of("opensearch.hosts", "opensearch 1", "authentication", "certificate"))));
            assertThat(wrong).extracting(ConfigProblem::field).containsExactly("opensearch.hosts", "ssl.certificate", "ssl.key");
            assertThatThrownBy(() -> registry.call("opensearch", provider -> provider.lookup(new LookupRequest(config, "index", "", Map.of(), 10))))
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
        return new ServiceConfig("search", Map.of("opensearch.hosts", "http://127.0.0.1:" + port, "username", "lookup", "password", "secret",
                "timeout", "2000"));
    }

    private void installReleaseDirectory() throws IOException
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(OpenSearchPluginTest.class)), "no repository");
        Path built = plugins.resolve("grantforge-plugin-opensearch/target");
        Path release = Files.createDirectories(installed.resolve("opensearch"));
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
