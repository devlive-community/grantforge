// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.service.ServiceView;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A service; its secrets are never sent, only which are set. The ID is a string because it exceeds JavaScript's safe
 * integers.
 *
 * @param id the service's id
 * @param name its name
 * @param label what the console shows
 * @param description a longer explanation
 * @param serviceType the service type's name
 * @param serviceTypeLabel the service type's label, while an active plugin provides it
 * @param available whether an active plugin provides the type
 * @param enabled whether the service is in use
 * @param values the settings that are not secret, as set
 * @param secretsSet the secret settings that have a value, by name
 */
public record ServiceResponse(String id, String name, String label, @Nullable String description, String serviceType,
        @Nullable String serviceTypeLabel, boolean available, boolean enabled, Map<String, String> values, List<String> secretsSet)
{
    /** Copies the collections. */
    public ServiceResponse
    {
        values = Map.copyOf(values);
        secretsSet = List.copyOf(secretsSet);
    }

    /**
     * Converts a view.
     *
     * @param view the view
     * @return the response
     */
    public static ServiceResponse from(ServiceView view)
    {
        return new ServiceResponse(Long.toString(view.id()), view.name(), view.label(), view.description(), view.serviceType(),
                view.serviceTypeLabel(), view.available(), view.enabled(), view.values(), view.secretsSet().stream().sorted().toList());
    }
}
