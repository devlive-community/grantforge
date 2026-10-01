// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import org.devlive.grantforge.identity.domain.MemberRow;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A member of a user group or holder of a position.
 *
 * @param accountId the account ID, as a string
 * @param username the login name
 * @param displayName the display name, if any
 * @param email the e-mail address, if any
 * @param addedAt when the account joined the group or got the position
 */
public record MemberResponse(String accountId, String username, @Nullable String displayName,
        @Nullable String email, Instant addedAt)
{
    /**
     * Converts a row.
     *
     * @param member the row
     * @return the response
     */
    public static MemberResponse from(MemberRow member)
    {
        return new MemberResponse(Long.toString(member.accountId()), member.username(), member.displayName(),
                member.email(), member.addedAt());
    }
}
