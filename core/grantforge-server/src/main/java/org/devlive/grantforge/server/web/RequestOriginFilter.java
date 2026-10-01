// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.devlive.grantforge.audit.application.AuditContext;
import org.devlive.grantforge.audit.application.RequestOrigin;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Makes the request's correlation ID, client address and user agent available to audit events
 * ({@link AuditContext}) for the rest of the request. Runs right after {@link RequestIdFilter}. Behind a proxy,
 * configure {@code server.forward-headers-strategy} so the address is the original client's.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestOriginFilter
        extends OncePerRequestFilter
{
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        RequestOrigin origin = new RequestOrigin(RequestIdFilter.currentId(request), request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT));
        try (AuditContext.Scope ignored = AuditContext.bind(origin)) {
            chain.doFilter(request, response);
        }
    }
}
