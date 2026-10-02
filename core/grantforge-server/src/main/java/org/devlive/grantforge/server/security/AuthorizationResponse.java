// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.authz.application.AuthorizationSnapshot;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * What the signed-in user may reach in the console, worked out from all of their effective roles. The console
 * hides navigation, pages and buttons whose resource code is missing; the server enforces the same rules on
 * every API call.
 *
 * @param version changes whenever the user's permissions change, so the console knows to reload them
 * @param unrestricted whether the user administers the platform; the catalog and the API review are theirs alone
 * @param roles the codes of the user's effective roles
 * @param resources the codes of the usable console resources, such as {@code system.user}
 * @param permissions the API permissions the user holds, such as {@code system.user.update}
 */
public record AuthorizationResponse(long version, boolean unrestricted, List<String> roles, List<String> resources,
        List<String> permissions)
{
    /** Copies the lists. */
    public AuthorizationResponse
    {
        roles = List.copyOf(requireNonNull(roles, "roles"));
        resources = List.copyOf(requireNonNull(resources, "resources"));
        permissions = List.copyOf(requireNonNull(permissions, "permissions"));
    }

    /**
     * Turns a snapshot into a response with sorted lists.
     *
     * @param snapshot the user's permissions
     * @param platformAdministrator whether the user administers the platform
     * @return the response
     */
    public static AuthorizationResponse from(AuthorizationSnapshot snapshot, boolean platformAdministrator)
    {
        List<String> roles = snapshot.roles().stream().sorted().toList();
        List<String> resources = snapshot.resources().stream().sorted().toList();
        List<String> permissions = snapshot.permissions().stream().sorted().toList();
        return new AuthorizationResponse(versionOf(snapshot), platformAdministrator, roles, resources, permissions);
    }

    /**
     * Returns the version of a snapshot: a fingerprint of its roles, resources and permissions, so it changes
     * when they do. Answers to calls that need a permission report it in {@link PermissionGuard#VERSION_HEADER}.
     *
     * @param snapshot the user's permissions
     * @return the version
     */
    public static long versionOf(AuthorizationSnapshot snapshot)
    {
        return Integer.toUnsignedLong(Objects.hash(snapshot.roles().stream().sorted().toList(),
                snapshot.resources().stream().sorted().toList(), snapshot.permissions().stream().sorted().toList()));
    }
}
