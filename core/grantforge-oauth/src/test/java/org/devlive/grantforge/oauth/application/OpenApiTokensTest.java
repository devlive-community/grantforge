// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenApiTokensTest
{
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final StoredAuthorizations authorizations = mock(StoredAuthorizations.class);
    private final OAuthClientRepository clients = mock(OAuthClientRepository.class);
    private final TokenClaims accounts = mock(TokenClaims.class);
    private final OpenApiTokens tokens = new OpenApiTokens(decoder, authorizations, clients, new TestPrincipals(), accounts);
    private final RegisteredClient registered = RegisteredClient.withId("7").clientId("gf_web").redirectUri("https://app.example/cb")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).build();
    private OAuthClient client;

    @BeforeEach
    void stubAValidToken()
    {
        when(decoder.decode("t")).thenReturn(mock(Jwt.class));
        client = OAuthClient.create(3, "gf_web", ClientType.PUBLIC, null, Instant.EPOCH);
        client.configure("Web", List.of(), Set.of(), Set.of(), Duration.ofMinutes(15), Duration.ofDays(1), true);
        ReflectionTestUtils.setField(client, "id", 7L);
        when(clients.findById(7L)).thenReturn(Optional.of(client));
        when(accounts.mayStillSignIn(any())).thenReturn(true);
    }

    @Test
    void recognisesTokensOfAccountsAndOfClients()
    {
        when(authorizations.findByToken("t", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization(true, Instant.now().plusSeconds(60)));

        assertThat(tokens.authenticate("t")).hasValueSatisfying(caller -> {
            assertThat(caller.clientId()).isEqualTo("gf_web");
            assertThat(caller.applicationId()).isEqualTo(3);
            assertThat(caller.subject()).isEqualTo(new OAuthSubject(42, 3, "ada", OAuthFixture.NOW));
            assertThat(caller.mayReadPermissions()).isTrue();
        });
        when(authorizations.findByToken("t", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization(false, Instant.now().plusSeconds(60)));
        assertThat(tokens.authenticate("t")).hasValueSatisfying(caller -> assertThat(caller.subject()).isNull());
    }

    @Test
    void refusesTokensThatNoLongerWork()
    {
        when(decoder.decode("forged")).thenThrow(new BadJwtException("bad signature"));
        assertThat(tokens.authenticate("forged")).isEmpty();
        // Signed but revoked: its authorization is gone.
        assertThat(tokens.authenticate("t")).isEmpty();
        when(authorizations.findByToken("t", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization(true, Instant.now().minusSeconds(1)));
        assertThat(tokens.authenticate("t")).isEmpty();
        when(authorizations.findByToken("t", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(authorization(true, Instant.now().plusSeconds(60)));
        when(accounts.mayStillSignIn(any())).thenReturn(false);
        assertThat(tokens.authenticate("t")).isEmpty();
        when(accounts.mayStillSignIn(any())).thenReturn(true);
        client.configure("Web", List.of(), Set.of(), Set.of(), Duration.ofMinutes(15), Duration.ofDays(1), false);
        assertThat(tokens.authenticate("t")).isEmpty();
        when(authorizations.findByToken("t", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(OAuth2Authorization.from(
                authorization(true, Instant.now().plusSeconds(60))).build());
        when(clients.findById(7L)).thenReturn(Optional.empty());
        assertThat(tokens.authenticate("t")).isEmpty();
    }

    @Test
    void ignoresAuthorizationsOfOtherServers()
    {
        RegisteredClient foreign = RegisteredClient.from(registered).id("not-a-number").build();
        when(authorizations.findByToken("t", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(OAuth2Authorization.withRegisteredClient(foreign)
                .principalName("x").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .accessToken(token(Instant.now().plusSeconds(60))).build());

        assertThat(tokens.authenticate("t")).isEmpty();
    }

    private OAuth2Authorization authorization(boolean forAccount, Instant expiresAt)
    {
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registered).principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).accessToken(token(expiresAt));
        if (forAccount) {
            builder.attribute(Principal.class.getName(), TestPrincipals.signedIn(42, 3, "ada"));
        }
        return builder.build();
    }

    private static OAuth2AccessToken token(Instant expiresAt)
    {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "t", expiresAt.minusSeconds(120), expiresAt,
                Set.of("openid", "permissions"));
    }
}
