// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * What a signed-in user may do in this application, as GrantForge answers it.
 *
 * @param application the application's code in GrantForge's catalog
 * @param accountId the user's account
 * @param tenantId the account's tenant
 * @param username the user's login name
 * @param version a fingerprint of the rest, which changes when the permissions do
 * @param roles the codes of the user's active roles
 * @param resources the codes of the application's resources the user may use: menus, pages, buttons
 * @param permissions the codes of the application's APIs the user may call
 * @param computedAt when GrantForge worked the permissions out
 */
public record UserAuthorization(String application, String accountId, String tenantId, String username, long version, List<String> roles,
        Set<String> resources, Set<String> permissions, Instant computedAt)
{
    /** Checks and copies the parts. */
    public UserAuthorization
    {
        requireNonNull(application, "application");
        requireNonNull(accountId, "accountId");
        requireNonNull(tenantId, "tenantId");
        requireNonNull(username, "username");
        roles = List.copyOf(roles);
        resources = Set.copyOf(resources);
        permissions = Set.copyOf(permissions);
        requireNonNull(computedAt, "computedAt");
    }

    /**
     * Returns whether the user may call an API.
     *
     * @param permission the API's permission code
     * @return {@code true} if granted
     */
    public boolean hasPermission(String permission)
    {
        return permissions.contains(permission);
    }

    /**
     * Returns whether the user may use a resource, such as a page or a button.
     *
     * @param resource the resource's code
     * @return {@code true} if granted
     */
    public boolean hasResource(String resource)
    {
        return resources.contains(resource);
    }

    /**
     * Returns whether the user holds a role.
     *
     * @param role the role's code
     * @return {@code true} if one of the user's active roles
     */
    public boolean hasRole(String role)
    {
        return roles.contains(role);
    }
}
