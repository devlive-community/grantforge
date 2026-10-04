// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * What a signed-in user may do in the calling application. IDs are strings because they exceed JavaScript's safe
 * integers.
 *
 * @param application the application's code
 * @param accountId the user's account
 * @param tenantId the account's tenant
 * @param username the user's login name
 * @param version a fingerprint of the rest, which changes when the permissions do; also the response's ETag
 * @param roles the codes of the user's active roles
 * @param resources the codes of the application's resources the user may use: menus, pages, buttons, data entities
 * @param permissions the codes of the application's APIs the user may call
 * @param computedAt when the permissions were worked out
 */
public record OpenAuthorizationResponse(String application, String accountId, String tenantId, String username, long version,
        List<String> roles, List<String> resources, List<String> permissions, Instant computedAt)
{
    /** Checks and copies the parts. */
    public OpenAuthorizationResponse
    {
        requireNonNull(application, "application");
        roles = List.copyOf(roles);
        resources = List.copyOf(resources);
        permissions = List.copyOf(permissions);
        requireNonNull(computedAt, "computedAt");
    }
}
