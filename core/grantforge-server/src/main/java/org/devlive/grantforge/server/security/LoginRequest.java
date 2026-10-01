// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Console sign-in input.
 *
 * @param username the login name (any case)
 * @param password the password
 */
public record LoginRequest(
        @NotBlank @Size(max = 64) @Nullable String username,
        @NotBlank @Size(max = 1024) @Nullable String password)
{
    /**
     * Hides the password from logs and debuggers.
     *
     * @return a description without the password
     */
    @Override
    public String toString()
    {
        return "LoginRequest[username=" + username + "]";
    }
}
