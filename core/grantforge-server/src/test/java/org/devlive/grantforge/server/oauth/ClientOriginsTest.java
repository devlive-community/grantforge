// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientOriginsTest
{
    @Test
    void allowsTheOriginsOfEnabledClientsRedirectUris()
    {
        OAuthClientRepository clients = mock(OAuthClientRepository.class);
        when(clients.findAll()).thenReturn(List.of(client(true, "https://Shop.example/cb", "http://localhost:5173/cb", "com.shop.app:/cb"),
                client(false, "https://off.example/cb")));
        ClientOrigins origins = new ClientOrigins(clients, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        CorsConfiguration shop = origins.getCorsConfiguration(request("https://shop.example"));
        assertThat(shop).isNotNull();
        assertThat(shop.getAllowedOrigins()).containsExactly("https://shop.example");
        assertThat(shop.getAllowedMethods()).containsExactly("GET", "POST");
        assertThat(shop.getExposedHeaders()).containsExactly(HttpHeaders.ETAG);
        assertThat(shop.getAllowCredentials()).isFalse();
        assertThat(origins.getCorsConfiguration(request("http://localhost:5173"))).isNotNull();
        assertThat(origins.getCorsConfiguration(request("https://off.example"))).isNull();
        assertThat(origins.getCorsConfiguration(request("https://evil.example"))).isNull();
        assertThat(origins.getCorsConfiguration(new MockHttpServletRequest())).isNull();
        assertThat(origins.origins()).isEqualTo(Set.of("https://shop.example", "http://localhost:5173"));
        // Read once a minute.
        verify(clients, times(1)).findAll();
    }

    @Test
    void readsOriginsOfWebUrisOnly()
    {
        assertThat(ClientOrigins.originOf("https://a.example:8443/x?y")).isEqualTo("https://a.example:8443");
        assertThat(ClientOrigins.originOf("com.shop.app:/cb")).isNull();
        assertThat(ClientOrigins.originOf("https:/no-host")).isNull();
    }

    private static OAuthClient client(boolean enabled, String... redirectUris)
    {
        OAuthClient client = OAuthClient.create(1, "gf_" + redirectUris[0].hashCode(), ClientType.PUBLIC, null, Instant.EPOCH);
        client.configure("C", List.of(redirectUris), Set.of(), Set.of(ClientGrant.AUTHORIZATION_CODE), Duration.ofMinutes(15),
                Duration.ofDays(1), enabled);
        return client;
    }

    private static MockHttpServletRequest request(String origin)
    {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/open/me/authorization");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        return request;
    }
}
