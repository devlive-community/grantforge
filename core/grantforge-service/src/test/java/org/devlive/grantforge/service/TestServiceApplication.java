// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.plugin.host.PluginHostConfiguration;
import org.devlive.grantforge.plugin.host.StoredPluginSwitches;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

/** Minimal Spring Boot configuration so slice tests can bootstrap this module, with the plugin host. */
@SpringBootApplication(scanBasePackages = {"org.devlive.grantforge.service", "org.devlive.grantforge.audit"})
@AutoConfigurationPackage(basePackages = {"org.devlive.grantforge.service", "org.devlive.grantforge.identity",
        "org.devlive.grantforge.audit", "org.devlive.grantforge.plugin.host"})
@Import({PluginHostConfiguration.class, StoredPluginSwitches.class})
public class TestServiceApplication
{
    /**
     * Stands in for the server's clock.
     *
     * @return the UTC system clock
     */
    @Bean
    Clock clock()
    {
        return Clock.systemUTC();
    }
}
