// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Declares that a service type supports {@link PolicyType#ROW_FILTER} policies.
 *
 * @param resources the resource levels a row filter policy ends at, for example {@code table}
 */
public record RowFilterDefinition(Set<String> resources)
{
    /**
     * Checks and copies the values.
     *
     * @throws IllegalArgumentException if no resource is given
     */
    public RowFilterDefinition
    {
        resources = Set.copyOf(requireNonNull(resources, "resources"));
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("row filtering needs at least one resource");
        }
    }
}
