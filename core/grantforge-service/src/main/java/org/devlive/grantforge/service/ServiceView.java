// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A service as administrators see it: never with its secrets, only which secrets are set.
 *
 * @param id the service's id
 * @param name its name, unique within the tenant
 * @param label what the console shows
 * @param description a longer explanation, or {@code null}
 * @param serviceType the service type's name
 * @param serviceTypeLabel the service type's label, or {@code null} while no active plugin provides it
 * @param enabled whether the service is in use
 * @param values the configuration values that are not secret, as set
 * @param secretsSet the secret fields that have a value
 */
public record ServiceView(long id, String name, String label, @Nullable String description, String serviceType,
        @Nullable String serviceTypeLabel, boolean enabled, Map<String, String> values, Set<String> secretsSet)
{
    /** Copies the collections. */
    public ServiceView
    {
        requireNonNull(name, "name");
        requireNonNull(label, "label");
        requireNonNull(serviceType, "serviceType");
        values = Map.copyOf(requireNonNull(values, "values"));
        secretsSet = Set.copyOf(requireNonNull(secretsSet, "secretsSet"));
    }

    /**
     * Returns whether an active plugin provides the service type.
     *
     * @return {@code true} if the service can be tested, looked into and given policies
     */
    public boolean available()
    {
        return serviceTypeLabel != null;
    }
}
