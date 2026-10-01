// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.RoleView;
import org.devlive.grantforge.authz.domain.RoleType;
import org.jspecify.annotations.Nullable;

/**
 * A role; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the role ID
 * @param code the code, unique in the tenant
 * @param name the display name
 * @param description the explanation, or {@code null}
 * @param type {@code SYSTEM} roles come with every tenant and can only be copied
 * @param enabled whether the role grants anything
 */
public record RoleResponse(String id, String code, String name, @Nullable String description, RoleType type, boolean enabled)
{
    /**
     * Converts a view.
     *
     * @param role the view
     * @return the response
     */
    public static RoleResponse from(RoleView role)
    {
        return new RoleResponse(Long.toString(role.id()), role.code(), role.name(), role.description(), role.type(),
                role.enabled());
    }
}
