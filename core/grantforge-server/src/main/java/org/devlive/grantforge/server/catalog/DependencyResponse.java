// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.DependencyView;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;

/**
 * A dependency between two resources; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the dependency ID
 * @param resourceId the resource that needs the other
 * @param dependsOnId the resource it needs
 * @param kind how strongly
 * @param source where it comes from; declared dependencies cannot be removed by hand
 */
public record DependencyResponse(String id, String resourceId, String dependsOnId, DependencyKind kind, DependencySource source)
{
    /**
     * Converts a view.
     *
     * @param dependency the view
     * @return the response
     */
    public static DependencyResponse from(DependencyView dependency)
    {
        return new DependencyResponse(Long.toString(dependency.id()), Long.toString(dependency.resourceId()),
                Long.toString(dependency.dependsOnId()), dependency.kind(), dependency.source());
    }
}
