// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.SodMode;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * A separation-of-duties constraint as an administrator enters it.
 *
 * @param code the code, unique in the tenant; fixed once created
 * @param name the name
 * @param description a longer explanation, or {@code null}
 * @param roleIds the mutually exclusive roles, at least two
 * @param maxRoles how many of them one account may hold, from 1 to one less than the roles
 * @param mode what a conflict does
 * @param enabled whether the constraint applies
 */
public record SodConstraintCommand(String code, String name, @Nullable String description, Set<Long> roleIds, int maxRoles, SodMode mode,
        boolean enabled)
{
    /** Copies the roles. */
    public SodConstraintCommand
    {
        roleIds = Set.copyOf(requireNonNull(roleIds, "roleIds"));
        requireNonNull(mode, "mode");
    }
}
