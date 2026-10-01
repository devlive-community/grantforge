// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A new password an administrator sets; the user must change it at the next sign-in.
 *
 * @param password the new password
 */
public record PasswordResetRequest(@NotBlank @Size(max = 1024) @Nullable String password)
{
    /**
     * Hides the password from logs and debuggers.
     *
     * @return a description without the password
     */
    @Override
    public String toString()
    {
        return "PasswordResetRequest[]";
    }
}
