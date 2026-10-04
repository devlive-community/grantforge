// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * A sign-in whose password was right and that waits for the second factor, kept in the session for a few minutes. It
 * signs nobody in: the session stays anonymous until {@code POST /api/v1/auth/mfa} completes it.
 *
 * @param accountId the account
 * @param tenantId its tenant
 * @param expiresAt when the second factor stops being accepted, in epoch milliseconds
 */
record PendingSignIn(long accountId, long tenantId, long expiresAt)
        implements Serializable
{
    /** How long the second factor may take. */
    static final Duration TIMEOUT = Duration.ofMinutes(5);

    private static final String ATTRIBUTE = PendingSignIn.class.getName();

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Keeps a sign-in waiting in the session.
     *
     * @param session the session
     * @param accountId the account
     * @param tenantId its tenant
     * @param now the current time
     */
    static void start(HttpSession session, long accountId, long tenantId, Instant now)
    {
        session.setAttribute(ATTRIBUTE, new PendingSignIn(accountId, tenantId, now.plus(TIMEOUT).toEpochMilli()));
    }

    /**
     * Returns the sign-in waiting in the session, forgetting one that took too long.
     *
     * @param session the session, if any
     * @param now the current time
     * @return the sign-in, or {@code null}
     */
    static @Nullable PendingSignIn current(@Nullable HttpSession session, Instant now)
    {
        if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof PendingSignIn pending)) {
            return null;
        }
        if (now.toEpochMilli() > pending.expiresAt()) {
            session.removeAttribute(ATTRIBUTE);
            return null;
        }
        return pending;
    }

    /**
     * Forgets the waiting sign-in.
     *
     * @param session the session
     */
    static void clear(HttpSession session)
    {
        session.removeAttribute(ATTRIBUTE);
    }
}
