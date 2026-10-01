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
 * First-run setup input. The password policy is checked by the service, which knows the configured limits.
 *
 * @param token the setup token printed in the server log
 * @param tenantName name of the first tenant; defaults to "Default"
 * @param username login name of the first administrator
 * @param password password of the first administrator
 * @param displayName optional display name of the administrator
 */
public record SetupRequest(
        @NotBlank @Size(max = 256) @Nullable String token,
        @Size(max = 128) @Nullable String tenantName,
        @NotBlank @Pattern(regexp = "\\s*[A-Za-z0-9._@-]{3,64}\\s*") @Nullable String username,
        @NotBlank @Size(max = 1024) @Nullable String password,
        @Size(max = 128) @Nullable String displayName)
{
    /**
     * Hides the password and token from logs and debuggers.
     *
     * @return a description without secrets
     */
    @Override
    public String toString()
    {
        return "SetupRequest[tenantName=" + tenantName + ", username=" + username + "]";
    }
}
