// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * An account as the user list shows it, read in one query with its primary department.
 *
 * @param id the account ID
 * @param username the login name
 * @param displayName the display name, if any
 * @param email the e-mail address, if any
 * @param status whether an administrator enabled it
 * @param lockedUntil when its lock ends, if locked
 * @param systemAccount whether it is a protected system account
 * @param mustChangePassword whether the password must be changed at the next sign-in
 * @param lastLoginAt the last successful sign-in, if any
 * @param createdAt when it was created
 * @param primaryUnitId the primary department, if any
 * @param primaryUnitName the primary department's name, if any
 */
public record UserRow(
        long id,
        String username,
        @Nullable String displayName,
        @Nullable String email,
        AccountStatus status,
        @Nullable Instant lockedUntil,
        boolean systemAccount,
        boolean mustChangePassword,
        @Nullable Instant lastLoginAt,
        Instant createdAt,
        @Nullable Long primaryUnitId,
        @Nullable String primaryUnitName)
{
    /** Validates the values. */
    public UserRow
    {
        requireNonNull(username, "username");
        requireNonNull(status, "status");
        requireNonNull(createdAt, "createdAt");
    }
}
