// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleType;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A role of the tenant.
 *
 * @param id the role ID
 * @param code the code, unique in the tenant
 * @param name the display name
 * @param description the explanation, or {@code null}
 * @param type whether GrantForge or an administrator created it
 * @param enabled whether it grants anything
 */
public record RoleView(long id, String code, String name, @Nullable String description, RoleType type, boolean enabled)
{
    /** Validates the values. */
    public RoleView
    {
        requireNonNull(code, "code");
        requireNonNull(name, "name");
        requireNonNull(type, "type");
    }

    /**
     * Converts a role.
     *
     * @param role the role
     * @return the view
     */
    public static RoleView from(Role role)
    {
        return new RoleView(role.requireId(), role.getCode(), role.getName(), role.getDescription(), role.getType(),
                role.isEnabled());
    }
}
