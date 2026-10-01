// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.ActiveSession;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A console session as the session list shows it.
 *
 * @param id the session's handle for revoking it, a string because it exceeds JavaScript's safe integers
 * @param username the owner's login name
 * @param displayName the owner's display name, if any
 * @param clientIp the client's address, if known
 * @param userAgent the client's user agent, if known
 * @param signedInAt when the session started
 * @param lastSeenAt when it was last used, to the minute by default
 * @param current whether this is the session of the request
 */
public record SessionResponse(
        String id,
        String username,
        @Nullable String displayName,
        @Nullable String clientIp,
        @Nullable String userAgent,
        Instant signedInAt,
        Instant lastSeenAt,
        boolean current)
{
    /**
     * Converts a session.
     *
     * @param session the session
     * @return the response
     */
    public static SessionResponse from(ActiveSession session)
    {
        return new SessionResponse(Long.toString(session.id()), session.username(), session.displayName(),
                session.clientIp(), session.userAgent(), session.signedInAt(), session.lastSeenAt(), session.current());
    }
}
