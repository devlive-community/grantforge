// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.devlive.grantforge.authz.domain.ResourceDependency;

import static java.util.Objects.requireNonNull;

/**
 * A dependency between two resources.
 *
 * @param id the dependency ID
 * @param resourceId the resource that needs the other
 * @param dependsOnId the resource it needs
 * @param kind how strongly
 * @param source where the dependency comes from
 */
public record DependencyView(long id, long resourceId, long dependsOnId, DependencyKind kind, DependencySource source)
{
    /** Validates the values. */
    public DependencyView
    {
        requireNonNull(kind, "kind");
        requireNonNull(source, "source");
    }

    /**
     * Converts a dependency.
     *
     * @param dependency the dependency
     * @return the view
     */
    public static DependencyView from(ResourceDependency dependency)
    {
        return new DependencyView(dependency.requireId(), dependency.getResourceId(), dependency.getDependsOnId(),
                dependency.getKind(), dependency.getSource());
    }
}
