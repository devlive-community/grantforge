// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Clients and authorizations for the OAuth tests. */
final class OAuthFixture
{
    static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");

    private OAuthFixture()
    {
    }

    static OAuthClient client(ApplicationRepository applications, OAuthClientRepository clients, String clientId, ClientType type,
            boolean enabled)
    {
        long application = applications.findByCode("crm").orElseGet(() -> applications.save(Application.create("crm", "CRM", null)))
                .requireId();
        OAuthClient client = OAuthClient.create(application, clientId, type, type == ClientType.CONFIDENTIAL ? "{noop}secret" : null, NOW);
        client.configure("CRM web", List.of("https://app.example/cb"), Set.of("openid", "profile"),
                Set.of(ClientGrant.AUTHORIZATION_CODE, ClientGrant.REFRESH_TOKEN, ClientGrant.CLIENT_CREDENTIALS),
                Duration.ofMinutes(15), Duration.ofDays(30), enabled);
        return clients.save(client);
    }

    static OAuth2AuthorizationRequest request(String clientId, String state)
    {
        return OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://id.example/oauth2/authorize").clientId(clientId)
                .redirectUri("https://app.example/cb").scopes(Set.of("openid", "profile")).state(state)
                .additionalParameters(Map.of("code_challenge", "challenge", "code_challenge_method", "S256", "nonce", "n1")).build();
    }

    static OAuth2Authorization codeIssued(RegisteredClient client, String id, String code)
    {
        return OAuth2Authorization.withRegisteredClient(client).id(id).principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(Set.of("openid", "profile"))
                .attribute(Principal.class.getName(), TestPrincipals.signedIn(42, 3, "ada"))
                .attribute(OAuth2AuthorizationRequest.class.getName(), request(client.getClientId(), "s1"))
                .token(new OAuth2AuthorizationCode(code, NOW, NOW.plusSeconds(300)))
                .build();
    }
}
