// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * What the signed-in user may reach in the console. The console hides navigation, pages and buttons whose
 * resource code is missing; the server enforces the same rules on every API call.
 *
 * @param version changes whenever the user's permissions change, so the console knows to reload them
 * @param unrestricted whether every console resource is reachable, regardless of {@code resources}
 * @param resources the codes of the reachable console resources, such as {@code system.user}
 */
public record AuthorizationResponse(long version, boolean unrestricted, List<String> resources)
{
    /** Copies the resource list. */
    public AuthorizationResponse
    {
        resources = List.copyOf(requireNonNull(resources, "resources"));
    }

    /**
     * Returns the snapshot used until roles and resources exist: every signed-in user reaches every console
     * resource, as with the single administrator first-run setup creates.
     *
     * @return the snapshot
     */
    public static AuthorizationResponse everything()
    {
        return new AuthorizationResponse(0, true, List.of());
    }
}
