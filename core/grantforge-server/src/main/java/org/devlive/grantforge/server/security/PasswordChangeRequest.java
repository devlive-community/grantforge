// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A new password for the signed-in user. The password policy is checked by the service.
 *
 * @param currentPassword the current password, to confirm it is the user
 * @param newPassword the new password
 */
public record PasswordChangeRequest(
        @NotBlank @Size(max = 1024) @Nullable String currentPassword,
        @NotBlank @Size(max = 1024) @Nullable String newPassword)
{
    /**
     * Hides the passwords from logs and debuggers.
     *
     * @return a description without secrets
     */
    @Override
    public String toString()
    {
        return "PasswordChangeRequest[]";
    }
}
