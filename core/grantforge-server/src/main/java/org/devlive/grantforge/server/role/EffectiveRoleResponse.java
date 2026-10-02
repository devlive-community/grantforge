// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.EffectiveRole;

import java.util.List;

/**
 * A role an account has, with every assignment that gives it.
 *
 * @param role the role
 * @param sources the assignments: direct, or through a group, department or position
 * @param active whether the role grants anything now
 */
public record EffectiveRoleResponse(RoleResponse role, List<AssignmentResponse> sources, boolean active)
{
    /** Copies the sources. */
    public EffectiveRoleResponse
    {
        sources = List.copyOf(sources);
    }

    /**
     * Converts an effective role.
     *
     * @param role the effective role
     * @return the response
     */
    public static EffectiveRoleResponse from(EffectiveRole role)
    {
        return new EffectiveRoleResponse(RoleResponse.from(role.role()), role.sources().stream().map(AssignmentResponse::from).toList(),
                role.active());
    }
}
