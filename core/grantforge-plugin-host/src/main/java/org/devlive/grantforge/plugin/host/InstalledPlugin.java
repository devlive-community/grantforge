// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * An installed plugin as the server sees it.
 *
 * @param id the plugin's id; for a package without a readable descriptor, its location
 * @param version the plugin's own version, or {@code null} if unknown
 * @param name what the console shows
 * @param description a longer explanation, or {@code null}
 * @param apiVersion the plugin API version it was built against, or {@code null} if unknown
 * @param source where it comes from
 * @param location its file or directory in the plugins directory, or its provider class for a built-in plugin
 * @param status whether its service types are in use
 * @param problem why it is set aside, or {@code null}
 * @param serviceTypes the service types it provides while active
 */
public record InstalledPlugin(String id, @Nullable String version, String name, @Nullable String description,
        @Nullable String apiVersion, PluginSource source, String location, PluginStatus status, @Nullable String problem,
        List<ServiceTypeDefinition> serviceTypes)
{
    /** Checks the values and copies the list. */
    public InstalledPlugin
    {
        requireNonNull(id, "id");
        requireNonNull(name, "name");
        requireNonNull(source, "source");
        requireNonNull(location, "location");
        requireNonNull(status, "status");
        serviceTypes = List.copyOf(requireNonNull(serviceTypes, "serviceTypes"));
    }
}
