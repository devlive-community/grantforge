// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static java.util.Objects.requireNonNull;

/**
 * Answers requests the security filters reject with the same RFC 9457 problem details as the rest of the
 * API, by handing a {@link GrantForgeException} to the MVC exception resolvers (and so to the problem
 * details advice, which localizes it).
 */
public final class ProblemSecurityHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler
{
    private final HandlerExceptionResolver resolver;

    /**
     * Creates the handler.
     *
     * @param resolver Spring MVC's composite exception resolver
     */
    public ProblemSecurityHandler(HandlerExceptionResolver resolver)
    {
        this.resolver = requireNonNull(resolver, "resolver");
    }

    /**
     * Answers an unauthenticated request with 401.
     *
     * @param request the request
     * @param response the response
     * @param failure why authentication is required
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
    {
        resolver.resolveException(request, response, null,
                new GrantForgeException(CommonErrorCode.UNAUTHENTICATED, "authentication required", failure));
    }

    /**
     * Answers a forbidden request with 403, distinguishing CSRF rejections so the console can explain them.
     *
     * @param request the request
     * @param response the response
     * @param failure why access was denied
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure)
    {
        GrantForgeException problem = failure instanceof CsrfException
                ? new GrantForgeException(SecurityErrorCode.CSRF_REJECTED, "CSRF token missing or invalid", failure)
                : new GrantForgeException(CommonErrorCode.FORBIDDEN, "access denied", failure);
        resolver.resolveException(request, response, null, problem);
    }
}
