// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A member of a user group or holder of a position, with the account's names.
 *
 * @param accountId the account
 * @param username the login name
 * @param displayName the display name, if any
 * @param email the e-mail address, if any
 * @param addedAt when the account joined the group or got the position
 */
public record MemberRow(long accountId, String username, @Nullable String displayName, @Nullable String email,
        Instant addedAt)
{
    /** Validates the values. */
    public MemberRow
    {
        requireNonNull(username, "username");
        requireNonNull(addedAt, "addedAt");
    }
}
