// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.example.ExampleProvider;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.PluginDescriptor;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Installs the example plugin, built against the plugin API alone, the way an operator would: as a jar, or as a
 * directory of classes while the build has not packaged it yet. It must load in its own class loader and work.
 */
class ExamplePluginTest
{
    @TempDir
    private Path plugins;

    /** Copies the example's build output into the plugins directory, as a jar or as a plugin directory. */
    private void install()
            throws IOException, URISyntaxException
    {
        Path built = Path.of(ExampleProvider.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(built)) {
            Files.copy(built, plugins.resolve("example.jar"));
            return;
        }
        Path target = plugins.resolve("example");
        Files.createDirectories(target.resolve("classes"));
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
    void loadsInItsOwnClassLoaderAndWorks()
            throws Exception
    {
        install();
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
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(5));
             PluginRegistry registry = new PluginRegistry(plugins, on, calls, ExamplePluginTest.class.getClassLoader())) {
            registry.scan();
            InstalledPlugin example = registry.plugins().stream().filter(plugin -> plugin.id().equals("example")).findFirst().orElseThrow();
            assertThat(example.status()).as(String.valueOf(example.problem())).isEqualTo(PluginStatus.ACTIVE);
            assertThat(registry.serviceType("example")).map(type -> type.label()).contains("Example warehouse");
            ConnectionResult connected = registry.call("example", provider -> {
                // The provider comes from the plugin's own loader, not from the test's class path.
                assertThat(provider.getClass().getClassLoader()).isInstanceOf(PluginClassLoader.class);
                return provider.testConnection(new ServiceConfig("dw", Map.of("password", ExampleProvider.PASSWORD)));
            });
            assertThat(connected.status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            assertThat(registry.serviceTypes()).extracting(type -> type.name()).isEqualTo(List.of("example"));
        }
    }
}
