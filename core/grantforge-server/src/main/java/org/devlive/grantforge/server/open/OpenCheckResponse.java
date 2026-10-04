// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import java.util.Map;

/**
 * Whether a signed-in user holds permissions of the calling application.
 *
 * @param permissions each permission asked about, and whether the user holds it
 */
public record OpenCheckResponse(Map<String, Boolean> permissions)
{
    /** Copies the answers. */
    public OpenCheckResponse
    {
        permissions = Map.copyOf(permissions);
    }
}
