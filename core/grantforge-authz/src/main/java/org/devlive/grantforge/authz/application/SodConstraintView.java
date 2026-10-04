// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.SodMode;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A separation-of-duties constraint as the console shows it.
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
public record SodConstraintView(long id, String code, String name, @Nullable String description, List<RoleView> roles, int maxRoles,
        SodMode mode, boolean enabled)
{
    /** Copies the roles. */
    public SodConstraintView
    {
        roles = List.copyOf(requireNonNull(roles, "roles"));
    }
}
