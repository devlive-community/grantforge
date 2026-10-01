// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;

/**
 * Input of first-run setup.
 *
 * @param token the setup token from the server log or {@code grantforge.setup.token}
 * @param tenantName display name of the first tenant; {@code null} or blank means "Default"
 * @param username login name of the first administrator
 * @param password raw password of the first administrator
 * @param displayName optional display name of the administrator
 */
public record SetupCommand(
        @Nullable String token,
        @Nullable String tenantName,
        String username,
        String password,
        @Nullable String displayName)
{
    /**
     * Hides the password and token from logs and debuggers.
     *
     * @return a description without secrets
     */
    @Override
    public String toString()
    {
        return "SetupCommand[tenantName=" + tenantName + ", username=" + username + "]";
    }
}
