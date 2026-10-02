// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * How a role sits in the inheritance graph of its tenant.
 *
 * @param role the role
 * @param parents the roles it inherits from directly
 * @param ancestors every role it inherits from, directly or through others, nearest first
 * @param descendants every role that inherits from it, nearest first
 */
public record RoleInheritance(RoleView role, List<RoleView> parents, List<Related> ancestors, List<Related> descendants)
{
    /** Copies the lists. */
    public RoleInheritance
    {
        requireNonNull(role, "role");
        parents = List.copyOf(requireNonNull(parents, "parents"));
        ancestors = List.copyOf(requireNonNull(ancestors, "ancestors"));
        descendants = List.copyOf(requireNonNull(descendants, "descendants"));
    }

    /**
     * A role related by inheritance.
     *
     * @param role the role
     * @param distance how many links away it is; 1 for a direct parent or child
     */
    public record Related(RoleView role, int distance)
    {
        /** Checks the role. */
        public Related
        {
            requireNonNull(role, "role");
        }
    }
}
