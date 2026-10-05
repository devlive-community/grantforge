// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NoAuthorizedClientsTest
{
    @Test
    void keepsNothing()
    {
        NoAuthorizedClients clients = new NoAuthorizedClients();
        TestingAuthenticationToken user = new TestingAuthenticationToken("frank", null);
        MockHttpServletRequest request = new MockHttpServletRequest();

        clients.saveAuthorizedClient(mock(OAuth2AuthorizedClient.class), user, request, new MockHttpServletResponse());
        clients.removeAuthorizedClient("okta", user, request, new MockHttpServletResponse());

        assertThat((OAuth2AuthorizedClient) clients.loadAuthorizedClient("okta", user, request)).isNull();
        assertThat(request.getSession(false)).isNull();
    }
}
