// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;

/**
 * Result of a successful sign-in.
 *
 * @param accountId the account
 * @param tenantId the account's tenant, which every request of the session is bound to
 * @param username the login name as stored
 * @param displayName the display name, if any
 * @param passwordChangeRequired whether the user must choose a new password (reset by an administrator,
 *         or expired under the policy)
 */
public record SignedInAccount(
        long accountId,
        long tenantId,
        String username,
        @Nullable String displayName,
        boolean passwordChangeRequired)
{
}
