// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.devlive.grantforge.oauth.application.OAuthSubject;
import org.devlive.grantforge.oauth.application.OpenApiTokens;
import org.devlive.grantforge.oauth.application.OpenCaller;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Signs in an open API call by its bearer token, as an {@link OpenCaller}, bound to the tenant of the account the token
 * is for. A call without a working token goes on unauthenticated, and the chain refuses it.
 */
public final class OpenTokenFilter
        extends OncePerRequestFilter
{
    private static final String BEARER = "Bearer ";

    private final OpenApiTokens tokens;

    /**
     * Creates the filter.
     *
     * @param tokens recognises access tokens
     */
    public OpenTokenFilter(OpenApiTokens tokens)
    {
        this.tokens = requireNonNull(tokens, "tokens");
    }

    // The wrappers only carry the chain's checked exceptions through the tenant lambda; the original is rethrown.
    @SuppressWarnings("PMD.PreserveStackTrace")
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        OpenCaller caller = header != null && header.startsWith(BEARER) ? tokens.authenticate(header.substring(BEARER.length()).strip())
                .orElse(null) : null;
        if (caller == null) {
            chain.doFilter(request, response);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(caller, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            OAuthSubject subject = caller.subject();
            if (subject == null) {
                chain.doFilter(request, response);
            }
            else {
                TenantContext.runInTenant(subject.tenantId(), () -> proceed(chain, request, response));
            }
        }
        catch (ChainFailure failure) {
            throw failure.servlet;
        }
        catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void proceed(FilterChain chain, HttpServletRequest request, HttpServletResponse response)
    {
        try {
            chain.doFilter(request, response);
        }
        catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        catch (ServletException failure) {
            throw new ChainFailure(failure);
        }
    }

    /** Carries a servlet failure out of the tenant binding. */
    private static final class ChainFailure
            extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private final transient ServletException servlet;

        ChainFailure(ServletException servlet)
        {
            super(servlet);
            this.servlet = servlet;
        }
    }
}
