// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.UserRow;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * An account as administrators list it.
 *
 * @param id the account ID
 * @param username the login name
 * @param displayName the display name, if any
 * @param email the e-mail address, if any
 * @param status whether an administrator enabled it
 * @param lockedUntil when its current lock ends, or {@code null} when it is not locked
 * @param systemAccount whether it is a protected system account
 * @param mustChangePassword whether the password must be changed at the next sign-in
 * @param lastLoginAt the last successful sign-in, if any
 * @param createdAt when it was created
 * @param primaryUnitId the primary department, if any
 * @param primaryUnitName the primary department's name, if any
 */
public record UserSummary(long id, String username, @Nullable String displayName, @Nullable String email,
        AccountStatus status, @Nullable Instant lockedUntil, boolean systemAccount, boolean mustChangePassword,
        @Nullable Instant lastLoginAt, Instant createdAt, @Nullable Long primaryUnitId, @Nullable String primaryUnitName)
{
    /** Validates the values. */
    public UserSummary
    {
        requireNonNull(username, "username");
        requireNonNull(status, "status");
        requireNonNull(createdAt, "createdAt");
    }

    /**
     * Converts a search row; a lock that already ended is not reported.
     *
     * @param row the row
     * @param now the current time
     * @return the summary
     */
    public static UserSummary from(UserRow row, Instant now)
    {
        Instant until = row.lockedUntil();
        return new UserSummary(row.id(), row.username(), row.displayName(), row.email(), row.status(),
                until != null && until.isAfter(now) ? until : null, row.systemAccount(), row.mustChangePassword(),
                row.lastLoginAt(), row.createdAt(), row.primaryUnitId(), row.primaryUnitName());
    }
}
