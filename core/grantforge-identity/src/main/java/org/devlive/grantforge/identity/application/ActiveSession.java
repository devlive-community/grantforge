// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A console session as listed to its owner or an administrator.
 *
 * @param id the session's handle, used to revoke it (not the session ID)
 * @param accountId the owner
 * @param username the owner's login name
 * @param displayName the owner's display name, if any
 * @param clientIp the client's address, if known
 * @param userAgent the client's user agent, if known
 * @param signedInAt when the session started
 * @param lastSeenAt when it was last used, at the precision of the activity interval
 * @param current whether this is the session of the request that lists it
 */
public record ActiveSession(
        long id,
        long accountId,
        String username,
        @Nullable String displayName,
        @Nullable String clientIp,
        @Nullable String userAgent,
        Instant signedInAt,
        Instant lastSeenAt,
        boolean current)
{
    /** Validates the values. */
    public ActiveSession
    {
        requireNonNull(username, "username");
        requireNonNull(signedInAt, "signedInAt");
        requireNonNull(lastSeenAt, "lastSeenAt");
    }
}
