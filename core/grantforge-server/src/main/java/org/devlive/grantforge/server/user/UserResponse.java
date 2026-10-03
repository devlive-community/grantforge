// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import org.devlive.grantforge.identity.application.UserSummary;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.persistence.secured.SecuredField;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An account as the user list shows it; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the account ID
 * @param username the login name
 * @param displayName the display name, if any
 * @param email the e-mail address, if any
 * @param status whether an administrator enabled it
 * @param lockedUntil when its current lock ends (year 9999 for an administrator's lock), or {@code null}
 * @param systemAccount whether it is a protected system account
 * @param mustChangePassword whether the password must be changed at the next sign-in
 * @param lastLoginAt the last successful sign-in, if any
 * @param createdAt when it was created
 * @param primaryUnitId the primary department, if any
 * @param primaryUnitName the primary department's name, if any
 */
public record UserResponse(String id, String username, @Nullable String displayName,
        @SecuredField(entity = "user", field = "email", name = "E-mail") @Nullable String email,
        AccountStatus status, @Nullable Instant lockedUntil, boolean systemAccount, boolean mustChangePassword,
        @SecuredField(entity = "user", field = "lastLoginAt", name = "Last sign-in") @Nullable Instant lastLoginAt,
        Instant createdAt, @Nullable String primaryUnitId, @Nullable String primaryUnitName)
{
    /**
     * Converts a summary.
     *
     * @param user the summary
     * @return the response
     */
    public static UserResponse from(UserSummary user)
    {
        Long unit = user.primaryUnitId();
        return new UserResponse(Long.toString(user.id()), user.username(), user.displayName(), user.email(), user.status(),
                user.lockedUntil(), user.systemAccount(), user.mustChangePassword(), user.lastLoginAt(), user.createdAt(),
                unit == null ? null : Long.toString(unit), user.primaryUnitName());
    }
}
