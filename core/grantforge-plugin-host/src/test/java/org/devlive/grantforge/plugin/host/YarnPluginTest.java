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
 * Loads the YARN plugin from the sources and from a release's directory layout, with Hadoop's YARN client only in the
 * plugin's own class loader, and has it try a port no ResourceManager listens on: what a real ResourceManager answers is
 * covered by the plugin's own integration tests. The module is built first, as this one depends on it.
 */
class YarnPluginTest
{
    @TempDir
    private Path installed;

    @Test
    void loadsTheBuiltModuleWithItsClients() throws IOException
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(YarnPluginTest.class)), "no repository");
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(plugins, enabled(), calls, YarnPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin yarn = registry.plugins().stream().filter(plugin -> plugin.id().equals("yarn")).findFirst().orElseThrow();
            assertThat(yarn.status()).as(String.valueOf(yarn.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(yarn.location()).isEqualTo("grantforge-plugin-yarn");
            ServiceConfig config = closedPort();
            ConnectionResult result = registry.call("yarn", provider -> {
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
             PluginRegistry registry = new PluginRegistry(installed, enabled(), calls, YarnPluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin yarn = registry.plugins().get(0);
            assertThat(yarn.status()).as(String.valueOf(yarn.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(yarn.location()).isEqualTo("yarn");
            assertThat(registry.serviceType("yarn")).hasValueSatisfying(type -> assertThat(type.resources()).extracting(level -> level.name())
                    .containsExactly("queue", "application", "cluster"));
            ServiceConfig config = closedPort();
            List<ConfigProblem> valid = registry.call("yarn", provider -> provider.validateConfig(config));
            assertThat(valid).isEmpty();
            List<ConfigProblem> wrong = registry.call("yarn", provider -> provider.validateConfig(new ServiceConfig("compute",
                    Map.of("yarn.resourcemanager.address", "rm 1"))));
            assertThat(wrong).extracting(ConfigProblem::field).containsExactly("yarn.resourcemanager.address", "username");
            assertThatThrownBy(() -> registry.call("yarn", provider -> provider.lookup(new LookupRequest(config, "queue", "", Map.of(), 10))))
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
        return new ServiceConfig("compute", Map.of("yarn.resourcemanager.address", "127.0.0.1:" + port, "username", "grantforge", "timeout", "2000"));
    }

    private void installReleaseDirectory() throws IOException
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(YarnPluginTest.class)), "no repository");
        Path built = plugins.resolve("grantforge-plugin-yarn/target");
        Path release = Files.createDirectories(installed.resolve("yarn"));
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
