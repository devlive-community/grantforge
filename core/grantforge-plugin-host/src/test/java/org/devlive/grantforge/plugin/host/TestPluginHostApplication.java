// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/** Minimal Spring Boot configuration so slice tests can bootstrap this module. */
@SpringBootApplication
public class TestPluginHostApplication
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
