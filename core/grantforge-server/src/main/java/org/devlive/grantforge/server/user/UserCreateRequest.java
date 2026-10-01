// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A new account. The password policy is checked by the service.
 *
 * @param username the login name, unique across tenants
 * @param password the initial password, which must be changed at the first sign-in
 * @param profile the details and departments
 */
public record UserCreateRequest(
        @NotBlank @Size(max = 64) @Nullable String username,
        @NotBlank @Size(max = 1024) @Nullable String password,
        @NotNull @Valid @Nullable UserProfileRequest profile)
{
    /**
     * Hides the password from logs and debuggers.
     *
     * @return a description without the password
     */
    @Override
    public String toString()
    {
        return "UserCreateRequest[username=" + username + "]";
    }
}
