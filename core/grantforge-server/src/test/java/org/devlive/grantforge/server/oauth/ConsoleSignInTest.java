// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleSignInTest
{
    @Test
    void sendsBrowsersToTheConsoleSignInWithTheRequestToReturnTo() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/gf/oauth2/authorize");
        request.setContextPath("/gf");
        request.setQueryString("client_id=gf_a&state=x y");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ConsoleSignIn().commence(request, response, new InsufficientAuthenticationException("anonymous"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/gf/#/auth/login?authorize=%2Foauth2%2Fauthorize%3Fclient_id%3Dgf_a%26state%3Dx+y");
        MockHttpServletResponse bare = new MockHttpServletResponse();
        new ConsoleSignIn().commence(new MockHttpServletRequest("GET", "/oauth2/authorize"), bare, new InsufficientAuthenticationException("x"));
        assertThat(bare.getRedirectedUrl()).isEqualTo("/#/auth/login?authorize=%2Foauth2%2Fauthorize");
    }
}
