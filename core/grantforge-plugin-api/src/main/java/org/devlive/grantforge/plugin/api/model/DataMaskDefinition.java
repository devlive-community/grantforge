// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Declares that a service type supports {@link PolicyType#DATA_MASK} policies.
 *
 * @param resources the resource levels a masking policy ends at, for example {@code column}
 * @param maskTypes the masks offered, in the order the console lists them
 */
public record DataMaskDefinition(Set<String> resources, List<MaskTypeDefinition> maskTypes)
{
    /**
     * Checks and copies the values.
     *
     * @throws IllegalArgumentException if either list is empty or a mask type name repeats
     */
    public DataMaskDefinition
    {
        resources = Set.copyOf(requireNonNull(resources, "resources"));
        maskTypes = List.copyOf(requireNonNull(maskTypes, "maskTypes"));
        if (resources.isEmpty() || maskTypes.isEmpty()) {
            throw new IllegalArgumentException("data masking needs at least one resource and one mask type");
        }
        Set<String> names = new HashSet<>();
        for (MaskTypeDefinition mask : maskTypes) {
            if (!names.add(mask.name())) {
                throw new IllegalArgumentException("mask type " + mask.name() + " declared twice");
            }
        }
    }
}
