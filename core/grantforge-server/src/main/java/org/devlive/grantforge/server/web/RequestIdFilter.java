// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns every request a correlation ID.
 *
 * <p>A client-supplied {@value #HEADER} is reused only if it is short and limited to
 * {@code [A-Za-z0-9._-]}; anything else is replaced by a random UUID so that request IDs can never
 * inject content into logs or headers. The ID is stored in the logging {@link MDC} under
 * {@value #MDC_KEY}, echoed in the response header and exposed to error responses via
 * {@link #currentId(HttpServletRequest)}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter
        extends OncePerRequestFilter
{
    /** Request and response header carrying the ID. */
    public static final String HEADER = "X-Request-Id";
    /** Logging MDC key holding the ID for the duration of the request. */
    public static final String MDC_KEY = "requestId";

    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String requestId = resolve(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        }
        finally {
            // Threads are pooled: a leftover MDC value would be logged for an unrelated request.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Returns the ID assigned to the request by this filter.
     *
     * @param request the current request
     * @return the ID, or {@code null} if the request did not pass through the filter
     */
    public static @Nullable String currentId(HttpServletRequest request)
    {
        return request.getAttribute(ATTRIBUTE) instanceof String id ? id : null;
    }

    /**
     * Returns the incoming ID when it is safe to reuse, otherwise a new random one.
     *
     * @param incoming the header value; may be {@code null}
     * @return a safe request ID; never {@code null}
     */
    static String resolve(@Nullable String incoming)
    {
        return incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }
}
