// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static java.util.Objects.requireNonNull;

/**
 * Starts the GrantForge server.
 *
 * <p>The class name is part of the distribution contract: {@code script/bin/*.sh} launch and
 * locate the process by {@code org.devlive.grantforge.server.GrantForge}.
 */
@SpringBootApplication(proxyBeanMethods = false)
public class GrantForge
{
    /**
     * Instantiated only by Spring (reflectively) as the root configuration class; private so
     * the class is never used as a general-purpose object.
     */
    private GrantForge()
    {
    }

    /**
     * Command line entry point.
     *
     * @param args Spring Boot arguments such as {@code --server.port=9999}; never {@code null}
     */
    public static void main(String[] args)
    {
        start(args);
    }

    /**
     * Starts the application and returns its context so callers (and tests) can stop it.
     *
     * @param args Spring Boot arguments; must not be {@code null}, may be empty
     * @return the running application context
     */
    public static ConfigurableApplicationContext start(String... args)
    {
        return SpringApplication.run(GrantForge.class, requireNonNull(args, "args"));
    }
}
