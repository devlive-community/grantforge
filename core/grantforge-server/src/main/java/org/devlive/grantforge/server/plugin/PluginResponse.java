// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.plugin;

import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.devlive.grantforge.plugin.host.InstalledPlugin;
import org.devlive.grantforge.plugin.host.PluginSource;
import org.devlive.grantforge.plugin.host.PluginStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An installed plugin.
 *
 * @param id the plugin's id
 * @param version its own version, if known
 * @param name what the console shows
 * @param description a longer explanation
 * @param apiVersion the plugin API version it was built against, if known
 * @param source built-in or installed into the plugins directory
 * @param location its file or directory, or its provider class for a built-in plugin
 * @param status whether its service types are in use
 * @param problem why it is set aside
 * @param serviceTypes the service types it provides while active
 */
public record PluginResponse(String id, @Nullable String version, String name, @Nullable String description,
        @Nullable String apiVersion, PluginSource source, String location, PluginStatus status, @Nullable String problem,
        List<ServiceType> serviceTypes)
{
    /** Copies the list. */
    public PluginResponse
    {
        serviceTypes = List.copyOf(serviceTypes);
    }

    /**
     * A service type a plugin provides, in brief.
     *
     * @param name its name
     * @param label what the console shows
     * @param description a longer explanation
     * @param version the definition's version
     * @param resources the names of its resource levels, in order
     * @param accessTypes the names of its access types, in order
     * @param dataMask whether it supports data masking
     * @param rowFilter whether it supports row filtering
     */
    public record ServiceType(String name, String label, @Nullable String description, int version, List<String> resources,
            List<String> accessTypes, boolean dataMask, boolean rowFilter)
    {
        /** Copies the lists. */
        public ServiceType
        {
            resources = List.copyOf(resources);
            accessTypes = List.copyOf(accessTypes);
        }

        static ServiceType from(ServiceTypeDefinition definition)
        {
            return new ServiceType(definition.name(), definition.label(), definition.description(), definition.version(),
                    definition.resources().stream().map(ResourceDefinition::name).toList(),
                    definition.accessTypes().stream().map(AccessTypeDefinition::name).toList(), definition.dataMask() != null,
                    definition.rowFilter() != null);
        }
    }

    /**
     * Converts a plugin.
     *
     * @param plugin the plugin
     * @return the response
     */
    public static PluginResponse from(InstalledPlugin plugin)
    {
        return new PluginResponse(plugin.id(), plugin.version(), plugin.name(), plugin.description(), plugin.apiVersion(),
                plugin.source(), plugin.location(), plugin.status(), plugin.problem(),
                plugin.serviceTypes().stream().map(ServiceType::from).toList());
    }
}
