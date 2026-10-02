// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ResourceDependencies;

import java.util.List;

/**
 * The dependencies around one resource.
 *
 * @param requires what the resource needs
 * @param requiredBy what needs the resource
 */
public record ResourceDependenciesResponse(List<DependencyResponse> requires, List<DependencyResponse> requiredBy)
{
    /** Copies the lists. */
    public ResourceDependenciesResponse
    {
        requires = List.copyOf(requires);
        requiredBy = List.copyOf(requiredBy);
    }

    /**
     * Converts the dependencies.
     *
     * @param dependencies the dependencies
     * @return the response
     */
    public static ResourceDependenciesResponse from(ResourceDependencies dependencies)
    {
        return new ResourceDependenciesResponse(dependencies.requires().stream().map(DependencyResponse::from).toList(),
                dependencies.requiredBy().stream().map(DependencyResponse::from).toList());
    }
}
