// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import jakarta.servlet.ServletException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.service.agent.AgentCredential;
import org.devlive.grantforge.service.agent.AgentTokens;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTokenFilterTest
{
    private final AgentTokens tokens = mock(AgentTokens.class);
    private final AgentTokenFilter filter = new AgentTokenFilter(tokens);

    @Test
    void signsAgentsInAndBindsTheTenantOfTheirService() throws Exception
    {
        AgentCredential credential = new AgentCredential(5, 6, 7);
        when(tokens.authenticate("gfa_ok")).thenReturn(Optional.of(credential));
        when(tokens.authenticate("gfa_bad")).thenReturn(Optional.empty());
        AtomicReference<Object> principal = new AtomicReference<>();
        AtomicReference<Long> tenant = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer  gfa_ok ");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            principal.set(requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal());
            tenant.set(TenantContext.requireTenantId());
        });
        assertThat(principal.get()).isEqualTo(credential);
        assertThat(tenant.get()).isEqualTo(5L);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        for (String header : new String[] {"Bearer gfa_bad", "Basic eDp4"}) {
            MockHttpServletRequest other = new MockHttpServletRequest();
            other.addHeader("Authorization", header);
            filter.doFilter(other, new MockHttpServletResponse(), (req, res) -> principal.set(SecurityContextHolder.getContext()
                    .getAuthentication()));
            assertThat(principal.get()).isNull();
        }
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> principal.set("anonymous"));
        assertThat(principal.get()).isEqualTo("anonymous");
    }

    @Test
    void passesFailuresOfTheChainOn()
    {
        when(tokens.authenticate("gfa_ok")).thenReturn(Optional.of(new AgentCredential(5, 6, 7)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer gfa_ok");
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new IOException("broken pipe");
        })).isInstanceOf(IOException.class).hasMessage("broken pipe");
        MockHttpServletRequest again = new MockHttpServletRequest();
        again.addHeader("Authorization", "Bearer gfa_ok");
        assertThatThrownBy(() -> filter.doFilter(again, new MockHttpServletResponse(), (req, res) -> {
            throw new ServletException("failed");
        })).isInstanceOf(ServletException.class);
    }
}
