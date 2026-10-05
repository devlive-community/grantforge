// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

/**
 * The plugin host: plugins are looked up at start-up from the classpath and from
 * {@code grantforge.plugins.directory} (default {@code plugins}, relative to the working directory, or the repository's
 * plugin modules when the server runs from the sources; see {@link PluginDirectory}); calls into plugins may take
 * {@code grantforge.plugins.call-timeout} (default 10 seconds).
 */
@Configuration(proxyBeanMethods = false)
public class PluginHostConfiguration
{
    /**
     * Calls plugin code with a time limit.
     *
     * @param limit how long one call may take
     * @return the caller
     */
    @Bean(destroyMethod = "close")
    public PluginCalls pluginCalls(@Value("${grantforge.plugins.call-timeout:10s}") Duration limit)
    {
        return new PluginCalls(limit);
    }

    /**
     * The installed plugins.
     *
     * @param directory the plugins directory as configured; blank to pick it (see {@link PluginDirectory})
     * @param switches which plugins are switched off
     * @param calls calls plugin code with a time limit
     * @return the registry, empty until the start-up scan
     */
    @Bean(destroyMethod = "close")
    // The plugin API that plugins share must come from the loader that loaded the server, not a thread's.
    @SuppressWarnings("PMD.UseProperClassLoader")
    public PluginRegistry pluginRegistry(@Value("${grantforge.plugins.directory:}") String directory, PluginSwitches switches,
            PluginCalls calls)
    {
        Path chosen = PluginDirectory.choose(directory, PluginDirectory.codeSource(PluginHostConfiguration.class));
        LoggerFactory.getLogger(PluginHostConfiguration.class).info("Plugins directory: {}", chosen.toAbsolutePath());
        return new PluginRegistry(chosen, switches, calls, PluginHostConfiguration.class.getClassLoader());
    }

    /**
     * Loads the plugins once the server has started; failures only set the failing plugin aside.
     *
     * @param registry the registry
     * @return the runner
     */
    @Bean
    public ApplicationRunner pluginScan(PluginRegistry registry)
    {
        return args -> registry.scan();
    }
}
