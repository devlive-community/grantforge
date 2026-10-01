// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;

import java.util.List;

/**
 * A plugin's entry point for one service type. Built-in plugins register implementations through
 * {@code META-INF/services/org.devlive.grantforge.plugin.api.ServiceTypeProvider}; external plugins name them in
 * their {@link PluginDescriptor}. Implementations need a public no-argument constructor and must be thread-safe.
 *
 * <p>The server calls the methods with a time limit and treats exceptions as failures of this plugin only.
 */
public interface ServiceTypeProvider
{
    /**
     * Describes the service type. Called once when the plugin loads; the result must not change while it runs.
     *
     * @return the definition
     */
    ServiceTypeDefinition definition();

    /**
     * Checks a configuration beyond what the definition's fields express, for example that two values agree. The
     * server has already checked the fields themselves.
     *
     * @param config the configuration
     * @return problems, empty when it is valid
     */
    default List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return List.of();
    }

    /**
     * Tries to reach the target system with a configuration.
     *
     * @param config the configuration
     * @return the outcome; {@link ConnectionResult#unsupported()} unless overridden
     */
    default ConnectionResult testConnection(ServiceConfig config)
    {
        return ConnectionResult.unsupported();
    }

    /**
     * Lists existing values of a resource level whose definition says {@code lookupSupported}.
     *
     * @param request what to look up
     * @return at most {@link LookupRequest#limit()} values; empty unless overridden
     */
    default List<String> lookup(LookupRequest request)
    {
        return List.of();
    }
}
