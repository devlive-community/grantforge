// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.IdentityErrorCode;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Keeps users who must choose a new password (an administrator demanded it, or it expired) to the calls that
 * let them do so: reading who they are, changing the password and signing out. Every other API answers
 * {@link IdentityErrorCode#PASSWORD_CHANGE_REQUIRED} until the password is changed.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordChangeGuard
        implements WebMvcConfigurer, HandlerInterceptor
{
    /** Session attribute present while the session's user must change the password. */
    static final String REQUIRED = PasswordChangeGuard.class.getName() + ".REQUIRED";

    /**
     * Marks or clears the pending password change of a session.
     *
     * @param session the session
     * @param required whether the user must choose a new password
     */
    static void require(HttpSession session, boolean required)
    {
        if (required) {
            session.setAttribute(REQUIRED, Boolean.TRUE);
        }
        else {
            session.removeAttribute(REQUIRED);
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(this).addPathPatterns("/api/**").excludePathPatterns("/api/v1/me",
                "/api/v1/me/authorization", "/api/v1/me/password", "/api/v1/auth/**", "/api/v1/bootstrap",
                "/api/v1/setup", "/api/v1/register");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(REQUIRED) != null) {
            throw new GrantForgeException(IdentityErrorCode.PASSWORD_CHANGE_REQUIRED, "the password must be changed first");
        }
        return true;
    }
}
