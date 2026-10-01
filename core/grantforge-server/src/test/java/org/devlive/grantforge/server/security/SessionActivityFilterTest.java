// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.FilterChain;
import org.devlive.grantforge.identity.application.ConsoleSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SessionActivityFilterTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    private final ConsoleSessionService sessions = mock(ConsoleSessionService.class);
    private final AtomicInteger passed = new AtomicInteger();
    private final FilterChain chain = (request, response) -> passed.incrementAndGet();

    @AfterEach
    void clear()
    {
        SecurityContextHolder.clearContext();
    }

    private SessionActivityFilter filter(Instant now)
    {
        return new SessionActivityFilter(sessions, Duration.ofMinutes(1), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static MockHttpServletRequest request(MockHttpSession session)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("User-Agent", "Firefox");
        return request;
    }

    @Test
    void recordsActivityOncePerInterval() throws Exception
    {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SessionUser(5, 42, "alice"), null, List.of()));
        MockHttpSession session = new MockHttpSession(null, "sid");

        filter(NOW).doFilter(request(session), new MockHttpServletResponse(), chain);
        filter(NOW.plusSeconds(59)).doFilter(request(session), new MockHttpServletResponse(), chain);
        filter(NOW.plusSeconds(60)).doFilter(request(session), new MockHttpServletResponse(), chain);

        verify(sessions, times(2)).touch("sid", 5, "10.0.0.1", "Firefox");
        assertThat(session.getAttribute(SessionActivityFilter.RECORDED_AT)).isEqualTo(NOW.plusSeconds(60).toEpochMilli());
        assertThat(passed).hasValue(3);
    }

    @Test
    void ignoresRequestsWithoutASignedInSession() throws Exception
    {
        filter(NOW).doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        filter(NOW).doFilter(request(new MockHttpSession()), new MockHttpServletResponse(), chain);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("operator", null));
        filter(NOW).doFilter(request(new MockHttpSession()), new MockHttpServletResponse(), chain);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SessionUser(5, 42, "alice"), null, List.of()));
        filter(NOW).doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        verify(sessions, never()).touch(anyString(), anyLong(), any(), any());
        assertThat(passed).hasValue(4);
    }
}
