// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ResourceType;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Everything an account may use in the console now, and the roles that give it.
 *
 * @param roles every role the account has, active or not, with how it has it
 * @param resources the console resources it may use, in catalog order
 * @param permissions the API permissions it holds, by code
 */
public record EffectiveAccess(List<EffectiveRole> roles, List<Item> resources, List<Item> permissions)
{
    /** Copies the lists. */
    public EffectiveAccess
    {
        roles = List.copyOf(roles);
        resources = List.copyOf(resources);
        permissions = List.copyOf(permissions);
    }

    /**
     * A resource or permission the account may use.
     *
     * @param code its code; for a permission the API permission, such as {@code system.user.read}
     * @param name its name
     * @param nameKey the console's message key of the name, or {@code null}
     * @param type its type
     * @param parentCode the code of the resource above it, or {@code null} at the top
     */
    public record Item(String code, String name, @Nullable String nameKey, ResourceType type, @Nullable String parentCode)
    {
        /** Checks the parts. */
        public Item
        {
            requireNonNull(code, "code");
            requireNonNull(name, "name");
            requireNonNull(type, "type");
        }
    }
}
