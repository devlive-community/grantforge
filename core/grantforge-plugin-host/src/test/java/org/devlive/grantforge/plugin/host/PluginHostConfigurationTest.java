// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PluginHostConfigurationTest
{
    @TempDir
    private Path plugins;

    @Test
    void createsTheCallerAndTheRegistryAndScansAtStartUp()
            throws Exception
    {
        PluginHostConfiguration configuration = new PluginHostConfiguration();
        try (PluginCalls calls = configuration.pluginCalls(Duration.ofSeconds(1));
             PluginRegistry registry = configuration.pluginRegistry(plugins.toString(), new PluginSwitches()
             {
                 @Override
                 public boolean enabled(String pluginId)
                 {
                     return true;
                 }

                 @Override
                 public void set(String pluginId, boolean enabled)
                 {
                     // Not needed.
                 }
             }, calls)) {
            ApplicationRunner scan = configuration.pluginScan(registry);
            scan.run(new DefaultApplicationArguments());
            assertThat(registry.plugins()).isEmpty();
        }
    }
}
