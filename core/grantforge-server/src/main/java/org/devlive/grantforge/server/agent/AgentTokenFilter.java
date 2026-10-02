// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.service.agent.AgentCredential;
import org.devlive.grantforge.service.agent.AgentTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.Serial;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Signs agents in by the token in their {@code Authorization: Bearer} header and binds the tenant of the token's service
 * for the rest of the request. Without a usable token the request stays anonymous and the chain refuses it.
 */
public final class AgentTokenFilter
        extends OncePerRequestFilter
{
    private static final String BEARER = "Bearer ";

    private final AgentTokens tokens;

    /**
     * Creates the filter.
     *
     * @param tokens signs agents in
     */
    public AgentTokenFilter(AgentTokens tokens)
    {
        this.tokens = requireNonNull(tokens, "tokens");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        Optional<AgentCredential> credential = header != null && header.startsWith(BEARER)
                ? tokens.authenticate(header.substring(BEARER.length()).strip()) : Optional.empty();
        if (credential.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        AgentCredential agent = credential.orElseThrow();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(agent, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            TenantContext.runInTenant(agent.tenantId(), () -> {
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
        finally {
            SecurityContextHolder.clearContext();
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
