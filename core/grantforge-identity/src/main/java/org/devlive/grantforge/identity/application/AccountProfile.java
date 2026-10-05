// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * What the console shows about the signed-in user.
 *
 * @param accountId the account
 * @param username the login name
 * @param displayName the display name, if any
 * @param email the e-mail address, if any
 * @param tenantCode the code of the user's tenant
 * @param tenantName the name of the user's tenant
 * @param systemAccount whether the account is a protected system account
 * @param passwordChangeRequired whether the user must choose a new password
 * @param lastLoginAt the time of the latest successful sign-in, if any
 * @param identitySource the name of the identity source that signs the account in and keeps its password, if any
 */
public record AccountProfile(
        long accountId,
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
}
