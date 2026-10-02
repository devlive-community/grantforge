// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.RoleInheritance;

import java.util.List;

/**
 * How a role sits in the inheritance graph of its tenant.
 *
 * @param role the role
 * @param parents the roles it inherits from directly
 * @param ancestors every role it inherits from, nearest first
 * @param descendants every role that inherits from it, nearest first
 */
public record RoleInheritanceResponse(RoleResponse role, List<RoleResponse> parents, List<Related> ancestors,
        List<Related> descendants)
{
    /** Copies the lists. */
    public RoleInheritanceResponse
    {
        parents = List.copyOf(parents);
        ancestors = List.copyOf(ancestors);
        descendants = List.copyOf(descendants);
    }

    /**
     * A role related by inheritance.
     *
     * @param role the role
     * @param distance how many links away it is; 1 for a direct parent or child
     */
    public record Related(RoleResponse role, int distance)
    {
        static Related from(RoleInheritance.Related related)
        {
            return new Related(RoleResponse.from(related.role()), related.distance());
        }
    }

    /**
     * Converts a view.
     *
     * @param inheritance the view
     * @return the response
     */
    public static RoleInheritanceResponse from(RoleInheritance inheritance)
    {
        return new RoleInheritanceResponse(RoleResponse.from(inheritance.role()),
                inheritance.parents().stream().map(RoleResponse::from).toList(),
                inheritance.ancestors().stream().map(Related::from).toList(),
                inheritance.descendants().stream().map(Related::from).toList());
    }
}
