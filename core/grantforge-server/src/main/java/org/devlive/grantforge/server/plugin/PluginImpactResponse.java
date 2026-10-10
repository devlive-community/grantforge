// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.plugin;

import org.devlive.grantforge.service.PluginImpact;

import java.util.List;

/**
 * What switching a plugin off would affect, for the console to show before it asks for confirmation.
 *
 * @param pluginId the plugin's id, which the console has the administrator type to confirm
 * @param pluginName what the console calls it
 * @param serviceTypes the names of the service types it provides, which become unavailable
 * @param services the services of every tenant using them, by tenant and then by name
 */
public record PluginImpactResponse(String pluginId, String pluginName, List<String> serviceTypes, List<AffectedService> services)
{
    /** Copies the lists. */
    public PluginImpactResponse
    {
        serviceTypes = List.copyOf(serviceTypes);
        services = List.copyOf(services);
    }

    /**
     * A service left without its service type.
     *
     * @param tenantCode the code of the tenant it belongs to
     * @param tenantName that tenant's name
     * @param id the service
     * @param name its name
     * @param label what the console shows
     * @param serviceType the service type it uses
     * @param enabled whether it is in use
     * @param policies how many policies it has, which can no longer be changed
     * @param agents how many agents enforce it, which get no new policies
     */
    public record AffectedService(String tenantCode, String tenantName, String id, String name, String label, String serviceType, boolean enabled,
            long policies, long agents)
    {
    }

    /**
     * Converts a report.
     *
     * @param report the report
     * @return the response
     */
    public static PluginImpactResponse from(PluginImpact.Report report)
    {
        return new PluginImpactResponse(report.pluginId(), report.pluginName(), report.serviceTypes(), report.services().stream()
                .map(service -> new AffectedService(service.tenantCode(), service.tenantName(), Long.toString(service.serviceId()), service.name(),
                        service.label(), service.serviceType(), service.enabled(), service.policies(), service.agents()))
                .toList());
    }
}
