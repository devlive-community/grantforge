// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the HDFS plugin the way a server started from the sources does (D-89): straight from its built module in the
 * repository's plugins folder, with Hadoop's client from the module's plugin-lib, in a class loader that sees none of
 * the server's libraries; then lists a folder through Hadoop's file system. The module is built first, as this one
 * depends on it.
 */
class HdfsPluginTest
{
    @TempDir
    private Path files;

    @Test
    void loadsTheBuiltModuleWithHadoopsClient() throws Exception
    {
        Path plugins = requireNonNull(PluginDirectory.sourceTree(PluginDirectory.codeSource(HdfsPluginTest.class)), "no repository");
        Files.createDirectories(files.resolve("data/sales"));
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
        // A local file system stands in for a cluster: the same Hadoop client code lists it.
        ServiceConfig config = new ServiceConfig("lake", Map.of("fs.default.name", "file:///", "username", "hdfs"));
        try (PluginCalls calls = new PluginCalls(Duration.ofSeconds(30));
             PluginRegistry registry = new PluginRegistry(plugins, on, calls, HdfsPluginTest.class.getClassLoader())) {
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
}
