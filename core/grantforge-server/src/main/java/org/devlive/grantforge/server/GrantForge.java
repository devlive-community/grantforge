// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import static java.util.Objects.requireNonNull;

/**
 * Starts the GrantForge server.
 *
 * <p>The class name is part of the distribution contract: {@code script/bin/*.sh} launch and
 * locate the process by {@code org.devlive.grantforge.server.GrantForge}.
 *
 * <p>Components, entities and repositories are picked up from every module below
 * {@code org.devlive.grantforge}, not only from the server package, except the Java SDK applications use
 * ({@code org.devlive.grantforge.sdk}), whose configuration is only for them.
 */
@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@ComponentScan(basePackages = GrantForge.BASE_PACKAGE, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.devlive\\.grantforge\\.sdk\\..*")})
@AutoConfigurationPackage(basePackages = GrantForge.BASE_PACKAGE)
public class GrantForge
{
    /** Root package of all GrantForge modules. */
    static final String BASE_PACKAGE = "org.devlive.grantforge";

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
