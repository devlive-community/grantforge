// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantBindingFilterTest
{
    private final TenantBindingFilter filter = new TenantBindingFilter();
    private final AtomicReference<OptionalLong> seen = new AtomicReference<>(OptionalLong.empty());
    private final FilterChain recording = (request, response) -> seen.set(TenantContext.currentTenantId());

    @AfterEach
    void clear()
    {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(Object principal)
    {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @Test
    void bindsTheSessionTenantForTheRestOfTheRequest() throws Exception
    {
        signIn(new SessionUser(5, 42, "alice"));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), recording);

        assertThat(seen.get()).hasValue(42);
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void leavesAnonymousAndForeignPrincipalsUnbound() throws Exception
    {
        seen.set(OptionalLong.of(-1));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), recording);
        assertThat(seen.get()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("operator", null));
        seen.set(OptionalLong.of(-1));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), recording);
        assertThat(seen.get()).isEmpty();
    }

    @Test
    void checkedFailuresOfTheChainPassThrough()
    {
        signIn(new SessionUser(5, 42, "alice"));

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> {
                    throw new IOException("broken pipe");
                })).isInstanceOf(IOException.class).hasMessage("broken pipe");
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> {
                    throw new ServletException("bad");
                })).isInstanceOf(ServletException.class).hasMessage("bad");
    }
}
