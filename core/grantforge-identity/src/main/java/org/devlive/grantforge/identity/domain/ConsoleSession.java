// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A signed-in console session, indexed for listing and revoking. The session itself lives in Spring Session's
 * {@code GF_SESSION} table; this row only names its owner and client and is addressed by its own ID, so the
 * session ID (the cookie's secret) never leaves the server.
 */
@Entity
@Table(name = "gf_console_session")
public class ConsoleSession
        extends TenantScopedEntity
{
    /** Longest stored client address (an IPv6 address with an IPv4 tail). */
    public static final int MAX_CLIENT_IP = 45;

    /** Longest stored user agent; longer values are cut. */
    public static final int MAX_USER_AGENT = 255;

    @Column(name = "session_id", nullable = false, updatable = false, length = 36)
    private String sessionId = "";

    @Column(name = "account_id", nullable = false, updatable = false)
    private long accountId;

    @Column(name = "client_ip", updatable = false, length = MAX_CLIENT_IP)
    private @Nullable String clientIp;

    @Column(name = "user_agent", updatable = false, length = MAX_USER_AGENT)
    private @Nullable String userAgent;

    @Column(name = "signed_in_at", nullable = false, updatable = false)
    private @Nullable Instant signedInAt;

    @Column(name = "last_seen_at", nullable = false)
    private @Nullable Instant lastSeenAt;

    /** For JPA. */
    protected ConsoleSession()
    {
    }

    /**
     * Indexes a session that just started.
     *
     * @param sessionId Spring Session's ID of the session
     * @param accountId the signed-in account
     * @param clientIp the client's address, if known
     * @param userAgent the client's user agent, if known; cut to {@link #MAX_USER_AGENT} characters
     * @param now the sign-in time
     * @return the entry
     */
    public static ConsoleSession start(String sessionId, long accountId, @Nullable String clientIp,
            @Nullable String userAgent, Instant now)
    {
        ConsoleSession session = new ConsoleSession();
        session.sessionId = Strings.requireNonBlank(sessionId, "sessionId");
        session.accountId = accountId;
        session.clientIp = cut(clientIp, MAX_CLIENT_IP);
        session.userAgent = cut(userAgent, MAX_USER_AGENT);
        session.signedInAt = requireNonNull(now, "now");
        session.lastSeenAt = now;
        return session;
    }

    private static @Nullable String cut(@Nullable String value, int max)
    {
        String text = Strings.blankToNull(value);
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * Returns Spring Session's ID of the session; never expose it, it grants the session.
     *
     * @return the session ID
     */
    public String getSessionId()
    {
        return sessionId;
    }

    /**
     * Returns the signed-in account.
     *
     * @return the account ID
     */
    public long getAccountId()
    {
        return accountId;
    }

    /**
     * Returns the client's address.
     *
     * @return the address, or {@code null} if unknown
     */
    public @Nullable String getClientIp()
    {
        return clientIp;
    }

    /**
     * Returns the client's user agent.
     *
     * @return the user agent, or {@code null} if unknown
     */
    public @Nullable String getUserAgent()
    {
        return userAgent;
    }

    /**
     * Returns when the session started.
     *
     * @return the sign-in time
     */
    public Instant getSignedInAt()
    {
        return requireNonNull(signedInAt, "signedInAt");
    }

    /**
     * Returns when the session was last used, at the precision of the activity interval.
     *
     * @return the last activity
     */
    public Instant getLastSeenAt()
    {
        return requireNonNull(lastSeenAt, "lastSeenAt");
    }
}
