// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Runs an import of the old database at start-up when {@code grantforge.legacy.source-url} is set. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "grantforge.legacy", name = "source-url")
@EnableConfigurationProperties(LegacyImportProperties.class)
public class LegacyImportConfiguration
{
    /**
     * The runner, which ends the process with its exit code when done.
     *
     * @param properties the settings
     * @param importer the importer
     * @param context the application, closed before the process exits
     * @return the runner
     */
    @Bean
    // The import is a run of its own: when it is done, so is the process, with the import's exit code.
    @SuppressWarnings("PMD.DoNotTerminateVM")
    LegacyImportRunner legacyImportRunner(LegacyImportProperties properties, LegacyImporter importer, ConfigurableApplicationContext context)
    {
        return new LegacyImportRunner(properties, importer, code -> System.exit(SpringApplication.exit(context, () -> code)));
    }
}
