// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * What an account may use in the console: the codes of the menus, pages, tabs and buttons it can reach and the
 * permission codes of the APIs it may call, worked out from all its active roles.
 *
 * @param accountId the account
 * @param roles the codes of its active roles
 * @param resources the codes of the console resources it can use (modules, menus, pages, tabs, buttons)
 * @param permissions the API permission codes it holds, as {@code @RequirePermission} names them
 * @param computedAt when the snapshot was taken; expiring grants and assignments change it over time
 */
public record AuthorizationSnapshot(long accountId, List<String> roles, Set<String> resources, Set<String> permissions,
        Instant computedAt)
{
    /** Copies the collections. */
    public AuthorizationSnapshot
    {
        roles = List.copyOf(requireNonNull(roles, "roles"));
        resources = Set.copyOf(requireNonNull(resources, "resources"));
        permissions = Set.copyOf(requireNonNull(permissions, "permissions"));
        requireNonNull(computedAt, "computedAt");
    }

    /**
     * Returns whether the account holds an API permission.
     *
     * @param permission the permission code
     * @return {@code true} if one of its active roles allows it
     */
    public boolean holds(String permission)
    {
        return permissions.contains(permission);
    }
}
