// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import jakarta.servlet.ServletException;
import org.devlive.grantforge.oauth.application.OAuthSubject;
import org.devlive.grantforge.oauth.application.OpenApiTokens;
import org.devlive.grantforge.oauth.application.OpenCaller;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenTokenFilterTest
{
    private final OpenApiTokens tokens = mock(OpenApiTokens.class);
    private final OpenTokenFilter filter = new OpenTokenFilter(tokens);

    @Test
    void signsInCallersBoundToTheTenantOfTheirAccount() throws Exception
    {
        OpenCaller user = new OpenCaller("gf_a", 1, new OAuthSubject(42, 3, "ada", Instant.EPOCH), Set.of("permissions"));
        when(tokens.authenticate(anyString())).thenReturn(Optional.empty());
        when(tokens.authenticate("good")).thenReturn(Optional.of(user));
        List<String> seen = new ArrayList<>();

        filter.doFilter(request("Bearer good"), new MockHttpServletResponse(), (request, response) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            seen.add(String.valueOf(authentication == null ? null : authentication.getPrincipal()));
            seen.add(TenantContext.currentTenantId().toString());
        });
        filter.doFilter(request("Bearer bad"), new MockHttpServletResponse(),
                (request, response) -> seen.add(String.valueOf(SecurityContextHolder.getContext().getAuthentication())));
        filter.doFilter(request(null), new MockHttpServletResponse(), (request, response) -> seen.add("anonymous"));

        assertThat(seen).containsExactly(user.toString(), OptionalLong.of(3).toString(), "null", "anonymous");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void leavesClientsOwnTokensUnboundAndPassesFailuresOn()
    {
        when(tokens.authenticate("machine")).thenReturn(Optional.of(new OpenCaller("gf_a", 1, null, Set.of())));
        when(tokens.authenticate("good")).thenReturn(Optional.of(new OpenCaller("gf_a", 1, new OAuthSubject(42, 3, "ada", Instant.EPOCH),
                Set.of())));

        assertThatThrownBy(() -> filter.doFilter(request("Bearer machine"), new MockHttpServletResponse(), (request, response) -> {
            assertThat(TenantContext.currentTenantId()).isEmpty();
            throw new ServletException("broken");
        })).isInstanceOf(ServletException.class).hasMessage("broken");
        assertThatThrownBy(() -> filter.doFilter(request("Bearer good"), new MockHttpServletResponse(), (request, response) -> {
            throw new ServletException("inside");
        })).isInstanceOf(ServletException.class).hasMessage("inside");
        assertThatThrownBy(() -> filter.doFilter(request("Bearer good"), new MockHttpServletResponse(), (request, response) -> {
            throw new IOException("closed");
        })).isInstanceOf(IOException.class).hasMessage("closed");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest request(@Nullable String authorization)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/open/me/authorization");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }
}
