// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A visitor's own new account. The password policy is checked by the service.
 *
 * @param username the login name
 * @param password the password
 * @param displayName the display name, if any
 */
public record RegistrationRequest(
        @NotBlank @Pattern(regexp = "\\s*[A-Za-z0-9._@-]{3,64}\\s*") @Nullable String username,
        @NotBlank @Size(max = 1024) @Nullable String password,
        @Size(max = 128) @Nullable String displayName)
{
    /**
     * Hides the password from logs and debuggers.
     *
     * @return a description without the password
     */
    @Override
    public String toString()
    {
        return "RegistrationRequest[username=" + username + "]";
    }
}
