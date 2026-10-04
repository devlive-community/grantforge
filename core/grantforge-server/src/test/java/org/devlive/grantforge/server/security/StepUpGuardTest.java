// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.security.RequireStepUp;
import org.devlive.grantforge.identity.application.MfaProperties;
import org.devlive.grantforge.identity.application.MfaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepUpGuardTest
{
    private static final Instant NOW = Instant.parse("2026-06-01T09:00:00Z");

    private final MfaService mfa = mock(MfaService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MockHttpSession session = new MockHttpSession();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    /** Handlers to guard. */
    static final class Handlers
    {
        @RequireStepUp
        void sensitive()
        {
        }

        void ordinary()
        {
        }
    }

    /** All its handlers are sensitive. */
    @RequireStepUp
    static final class SensitiveHandlers
    {
        void any()
        {
        }
    }

    @BeforeEach
    void signIn()
    {
        request.setSession(session);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new SessionUser(7, 1, "alice"), null, List.of()));
    }

    @AfterEach
    void signOut()
    {
        SecurityContextHolder.clearContext();
    }

    private static HandlerMethod handler(Object bean, String name) throws NoSuchMethodException
    {
        return new HandlerMethod(bean, bean.getClass().getDeclaredMethod(name));
    }

    private boolean pass(StepUpGuard guard, HandlerMethod handler)
    {
        return guard.preHandle(request, new MockHttpServletResponse(), handler);
    }

    private static Object codeOf(Throwable error)
    {
        return ((GrantForgeException) error).getErrorCode();
    }

    @Test
    void asksUsersWithTwoStepSignInToConfirmAgainAfterTheWindow() throws Exception
    {
        StepUpGuard guard = new StepUpGuard(mfa, new MfaProperties(Duration.ofMinutes(10), false), clock);
        HandlerMethod sensitive = handler(new Handlers(), "sensitive");
        when(mfa.enabled(7)).thenReturn(true);

        assertThatThrownBy(() -> pass(guard, sensitive)).satisfies(error -> assertThat(codeOf(error)).isEqualTo(SecurityErrorCode.STEP_UP_REQUIRED));
        StepUpGuard.verified(session, Clock.fixed(NOW.minus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        assertThat(pass(guard, sensitive)).isTrue();
        StepUpGuard.verified(session, Clock.fixed(NOW.minus(Duration.ofMinutes(11)), ZoneOffset.UTC));
        assertThatThrownBy(() -> pass(guard, sensitive)).isInstanceOf(GrantForgeException.class);
        assertThatThrownBy(() -> pass(guard, handler(new SensitiveHandlers(), "any"))).isInstanceOf(GrantForgeException.class);
        // Other handlers are not concerned.
        assertThat(pass(guard, handler(new Handlers(), "ordinary"))).isTrue();
        assertThat(guard.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void letsUsersWithoutTwoStepSignInThroughUnlessItIsDemanded() throws Exception
    {
        HandlerMethod sensitive = handler(new Handlers(), "sensitive");
        assertThat(pass(new StepUpGuard(mfa, new MfaProperties(Duration.ofMinutes(10), false), clock), sensitive)).isTrue();

        StepUpGuard demanding = new StepUpGuard(mfa, new MfaProperties(Duration.ofMinutes(10), true), clock);
        assertThatThrownBy(() -> pass(demanding, sensitive))
                .satisfies(error -> assertThat(codeOf(error)).isEqualTo(SecurityErrorCode.MFA_SETUP_REQUIRED));
        // Calls without a console session, such as anonymous ones, are left to the other checks.
        SecurityContextHolder.clearContext();
        assertThat(pass(demanding, sensitive)).isTrue();
    }
}
