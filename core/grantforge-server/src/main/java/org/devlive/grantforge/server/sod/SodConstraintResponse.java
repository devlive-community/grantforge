// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.sod;

import org.devlive.grantforge.authz.application.SodConstraintView;
import org.devlive.grantforge.authz.domain.SodMode;
import org.devlive.grantforge.server.role.RoleResponse;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A separation-of-duties constraint.
 *
 * @param id the ID
 * @param code the code
 * @param name the name
 * @param description the explanation, if any
 * @param roles the mutually exclusive roles, by name
 * @param maxRoles how many of them one account may hold
 * @param mode what a conflict does
 * @param enabled whether the constraint applies
 */
public record SodConstraintResponse(String id, String code, String name, @Nullable String description, List<RoleResponse> roles, int maxRoles,
        SodMode mode, boolean enabled)
{
    /**
     * Converts a view.
     *
     * @param view the constraint
     * @return the response
     */
    public static SodConstraintResponse from(SodConstraintView view)
    {
        return new SodConstraintResponse(Long.toString(view.id()), view.code(), view.name(), view.description(),
                view.roles().stream().map(RoleResponse::from).toList(), view.maxRoles(), view.mode(), view.enabled());
    }
}
