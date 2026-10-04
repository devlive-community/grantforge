// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.AccountProfile;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The signed-in user as the console shows it.
 *
 * @param username the login name
 * @param displayName the display name, if any
 * @param email the e-mail address, if any
 * @param tenantCode the code of the user's organization
 * @param tenantName the name of the user's organization
 * @param systemAccount whether the account is a protected system account
 * @param passwordChangeRequired whether the user must choose a new password
 * @param lastLoginAt the latest successful sign-in, if any
 * @param identitySource the identity source that signs the user in and keeps the password, if any
 */
public record MeResponse(
        String username,
        @Nullable String displayName,
        @Nullable String email,
        String tenantCode,
        String tenantName,
        boolean systemAccount,
        boolean passwordChangeRequired,
        @Nullable Instant lastLoginAt,
        @Nullable String identitySource)
{
    /**
     * Converts a profile.
     *
     * @param profile the profile
     * @return the response
     */
    public static MeResponse from(AccountProfile profile)
    {
        return new MeResponse(profile.username(), profile.displayName(), profile.email(), profile.tenantCode(),
                profile.tenantName(), profile.systemAccount(), profile.passwordChangeRequired(), profile.lastLoginAt(),
                profile.identitySource());
    }
}
