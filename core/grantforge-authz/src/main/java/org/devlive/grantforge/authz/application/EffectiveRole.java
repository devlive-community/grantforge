// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A role an account has, with every way it has it.
 *
 * @param role the role
 * @param sources the assignments that give it: directly, or through a group, department or position
 * @param active whether the role grants anything now: it is enabled and at least one source applies now
 */
public record EffectiveRole(RoleView role, List<AssignmentView> sources, boolean active)
{
    /** Copies the sources. */
    public EffectiveRole
    {
        requireNonNull(role, "role");
        sources = List.copyOf(requireNonNull(sources, "sources"));
    }
}
