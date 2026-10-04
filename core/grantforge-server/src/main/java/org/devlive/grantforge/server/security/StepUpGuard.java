// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.security.RequireStepUp;
import org.devlive.grantforge.identity.application.MfaProperties;
import org.devlive.grantforge.identity.application.MfaService;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

import static java.util.Objects.requireNonNull;

/**
 * Asks for the second factor again before sensitive operations ({@link RequireStepUp}, D-71). A user with two-step
 * sign-in gives it at sign-in, which covers such operations for {@code grantforge.security.mfa.step-up-window}; later
 * they answer {@link SecurityErrorCode#STEP_UP_REQUIRED} until the user confirms with {@code POST /api/v1/auth/step-up}.
 * Users without two-step sign-in pass, unless {@code grantforge.security.mfa.required-for-sensitive} demands it.
 */
@Configuration(proxyBeanMethods = false)
public class StepUpGuard
        implements WebMvcConfigurer, HandlerInterceptor
{
    /** Session attribute with when the session's user last gave the second factor, in epoch milliseconds. */
    static final String VERIFIED_AT = StepUpGuard.class.getName() + ".VERIFIED_AT";

    private final MfaService mfa;
    private final MfaProperties properties;
    private final Clock clock;

    /**
     * Creates the guard.
     *
     * @param mfa tells who signs in in two steps
     * @param properties the window and whether two-step sign-in is demanded
     * @param clock the current time
     */
    public StepUpGuard(MfaService mfa, MfaProperties properties, Clock clock)
    {
        this.mfa = requireNonNull(mfa, "mfa");
        this.properties = requireNonNull(properties, "properties");
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Records that the session's user just gave the second factor.
     *
     * @param session the session
     * @param clock the current time
     */
    static void verified(HttpSession session, Clock clock)
    {
        session.setAttribute(VERIFIED_AT, clock.millis());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        if (!(handler instanceof HandlerMethod method) || !sensitive(method)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionUser user)) {
            return true;
        }
        if (!mfa.enabled(user.accountId())) {
            if (properties.requiredForSensitive()) {
                throw new GrantForgeException(SecurityErrorCode.MFA_SETUP_REQUIRED, "account " + user.accountId() + " lacks two-step sign-in");
            }
            return true;
        }
        HttpSession session = request.getSession(false);
        Object at = session == null ? null : session.getAttribute(VERIFIED_AT);
        if (!(at instanceof Long verifiedAt) || clock.millis() - verifiedAt > properties.stepUpWindow().toMillis()) {
            throw new GrantForgeException(SecurityErrorCode.STEP_UP_REQUIRED, "account " + user.accountId() + " must confirm again");
        }
        return true;
    }

    private static boolean sensitive(HandlerMethod method)
    {
        return AnnotatedElementUtils.hasAnnotation(method.getMethod(), RequireStepUp.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RequireStepUp.class);
    }
}
