// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.Serial;

/**
 * Binds the signed-in user's tenant for the rest of the request, so every repository call and Hibernate
 * session opened while handling it sees only that tenant's data. Runs after the security filters have
 * restored the session's authentication and before any transaction opens (open-in-view is off).
 * Anonymous requests stay unbound: tenant-scoped queries then match nothing.
 */
public final class TenantBindingFilter
        extends OncePerRequestFilter
{
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionUser user)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            TenantContext.runInTenant(user.tenantId(), () -> {
                try {
                    chain.doFilter(request, response);
                }
                catch (IOException | ServletException failure) {
                    throw new ChainFailure(failure);
                }
            });
        }
        catch (ChainFailure failure) {
            failure.rethrow();
        }
    }

    /** Carries a checked exception of the filter chain through the tenant binding callback. */
    private static final class ChainFailure
            extends RuntimeException
    {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Exception failure;

        ChainFailure(Exception failure)
        {
            super(failure);
            this.failure = failure;
        }

        void rethrow()
                throws ServletException, IOException
        {
            if (failure instanceof IOException io) {
                throw io;
            }
            throw (ServletException) failure;
        }
    }
}
