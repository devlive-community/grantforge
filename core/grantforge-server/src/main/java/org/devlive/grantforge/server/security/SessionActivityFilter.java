// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.devlive.grantforge.identity.application.ConsoleSessionService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * Records that a signed-in session was used, at most once per activity interval, so the session list shows
 * when each session was last active. Runs inside {@link TenantBindingFilter}, with the user's tenant bound.
 */
public final class SessionActivityFilter
        extends OncePerRequestFilter
{
    /** Session attribute holding when activity was last recorded, in epoch milliseconds. */
    static final String RECORDED_AT = SessionActivityFilter.class.getName() + ".RECORDED_AT";

    private final ConsoleSessionService sessions;
    private final long intervalMillis;
    private final Clock clock;

    /**
     * Creates the filter.
     *
     * @param sessions the session index
     * @param interval how often activity is recorded
     * @param clock source of the current time
     */
    public SessionActivityFilter(ConsoleSessionService sessions, Duration interval, Clock clock)
    {
        this.sessions = requireNonNull(sessions, "sessions");
        this.intervalMillis = requireNonNull(interval, "interval").toMillis();
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns the client's address as the server sees it (behind a proxy, configure
     * {@code server.forward-headers-strategy} so this is the original client).
     *
     * @param request the request
     * @return the address, if known
     */
    static @Nullable String clientIp(HttpServletRequest request)
    {
        return request.getRemoteAddr();
    }

    /**
     * Returns the client's user agent.
     *
     * @param request the request
     * @return the user agent, if sent
     */
    static @Nullable String userAgent(HttpServletRequest request)
    {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (session != null && authentication != null && authentication.getPrincipal() instanceof SessionUser user) {
            long now = clock.millis();
            Object recorded = session.getAttribute(RECORDED_AT);
            if (!(recorded instanceof Long at) || now - at >= intervalMillis) {
                session.setAttribute(RECORDED_AT, now);
                sessions.touch(session.getId(), user.accountId(), clientIp(request), userAgent(request));
            }
        }
        chain.doFilter(request, response);
    }
}
