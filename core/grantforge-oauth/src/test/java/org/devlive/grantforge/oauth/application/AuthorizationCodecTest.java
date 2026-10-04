// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.devlive.grantforge.oauth.application.AuthorizationCodec.Slot;
import org.devlive.grantforge.oauth.domain.AuthorizationContent;
import org.devlive.grantforge.oauth.domain.StoredAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationCodecTest
{
    private static final Instant NOW = Instant.now();

    private final AuthorizationCodec codec = new AuthorizationCodec(new TestPrincipals());

    private final RegisteredClient client = RegisteredClient.withId("7").clientId("gf_web").redirectUri("https://app.example/cb")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).build();

    @Test
    void readsTheClaimsOfThePresentedJwt() throws Exception
    {
        String jwt = jwt();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client).id("auth-1").principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, jwt, NOW, NOW.plusSeconds(60)))
                .token(new OidcIdToken("id-1", NOW, NOW.plusSeconds(60), Map.of("sub", "42"))).build();
        StoredAuthorization stored = stored(codec.content(authorization, NOW));

        OAuth2Authorization byAccess = codec.authorization(stored, client, jwt, Slot.ACCESS);
        assertThat(requireNonNull(byAccess.getAccessToken()).getClaims()).containsEntry("sub", "42").containsEntry("tid", "3")
                .containsKey("exp").satisfies(claims -> assertThat(claims.get("exp")).isInstanceOf(Instant.class));
        // An ID token found through another token says only whom it is about and for which client.
        assertThat(requireNonNull(byAccess.getToken(OidcIdToken.class)).getToken().getClaims())
                .containsEntry("sub", "42").containsEntry("aud", List.of("gf_web"));
        OAuth2Authorization byIdToken = codec.authorization(stored, client, "id-1", Slot.ID_TOKEN);
        assertThat(requireNonNull(byIdToken.getToken(OidcIdToken.class)).getToken().getTokenValue()).isEqualTo("id-1");
        assertThat(requireNonNull(byIdToken.getAccessToken()).getClaims()).isNull();
    }

    @Test
    void refusesWhatItCannotStore()
    {
        OAuth2Authorization.Builder base = OAuth2Authorization.withRegisteredClient(client).id("auth-1").principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);

        assertThatThrownBy(() -> codec.content(base.attribute("other", "x").build(), NOW)).isInstanceOf(IllegalStateException.class);
        OAuth2Authorization longState = OAuth2Authorization.withRegisteredClient(client).id("auth-2").principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(OAuth2AuthorizationRequest.class.getName(), OAuthFixture.request("gf_web", "s".repeat(256))).build();
        assertThatThrownBy(() -> codec.content(longState, NOW)).isInstanceOfSatisfying(OAuth2AuthenticationException.class, error ->
                assertThat(error.getError().getErrorCode()).isEqualTo("invalid_request"));
        // Without tokens, an authorization expires at once.
        AuthorizationContent empty = codec.content(OAuth2Authorization.withRegisteredClient(client).id("auth-3").principalName("42")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(Set.of()).build(), NOW);
        assertThat(empty.expiresAt()).isEqualTo(NOW);
        assertThat(empty.accountId()).isNull();
    }

    private static StoredAuthorization stored(AuthorizationContent content)
    {
        StoredAuthorization stored = StoredAuthorization.create("auth-1", content);
        ReflectionTestUtils.setField(stored, "version", 0L);
        return stored;
    }

    private static String jwt() throws Exception
    {
        RSAKey key = new RSAKeyGenerator(2048).keyID("k").generate();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("42").claim("tid", "3").issuedAt(NOW).expiresAt(NOW.plusSeconds(60)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
