// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The dependencies around one resource.
 *
 * @param requires what the resource needs
 * @param requiredBy what needs the resource
 */
public record ResourceDependencies(List<DependencyView> requires, List<DependencyView> requiredBy)
{
    /** Copies the lists. */
    public ResourceDependencies
    {
        requires = List.copyOf(requireNonNull(requires, "requires"));
        requiredBy = List.copyOf(requireNonNull(requiredBy, "requiredBy"));
    }
}
