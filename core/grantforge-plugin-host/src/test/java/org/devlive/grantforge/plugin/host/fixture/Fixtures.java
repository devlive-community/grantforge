// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host.fixture;

import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;

import java.util.Locale;

/**
 * Providers that the tests package as plugins. Their compiled classes are copied into plugin packages, so plugin
 * class loaders load their own copies.
 */
public final class Fixtures
{
    private Fixtures()
    {
    }

    static ServiceTypeDefinition named(String name, String description)
    {
        return ServiceTypeDefinition.builder(name).label(name.toUpperCase(Locale.ROOT)).description(description)
                .resources(ResourceDefinition.builder("path").build()).accessTypes(AccessTypeDefinition.of("read", "Read")).build();
    }

    /** Provides "alpha" and tells, in its description, whether it can see the server's classes. */
    public static final class Alpha
            implements ServiceTypeProvider
    {
        @Override
        public ServiceTypeDefinition definition()
        {
            boolean seesHost;
            try {
                Class.forName("org.devlive.grantforge.plugin.host.PluginRegistry", false, Alpha.class.getClassLoader());
                seesHost = true;
            }
            catch (ClassNotFoundException expected) {
                seesHost = false;
            }
            return named("alpha", seesHost ? "sees the server" : "isolated");
        }
    }

    /** Provides "beta". */
    public static final class Beta
            implements ServiceTypeProvider
    {
        @Override
        public ServiceTypeDefinition definition()
        {
            return named("beta", "second");
        }
    }

    /** Fails to describe itself. */
    public static final class Broken
            implements ServiceTypeProvider
    {
        @Override
        public ServiceTypeDefinition definition()
        {
            throw new IllegalStateException("cannot describe myself");
        }
    }

    /** Takes far too long to describe itself. */
    public static final class Slow
            implements ServiceTypeProvider
    {
        @Override
        public ServiceTypeDefinition definition()
        {
            try {
                Thread.sleep(60_000);
            }
            catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
            return named("slow", "too late");
        }
    }

    /** Not a provider at all. */
    public static final class NotAProvider
    {
    }
}
