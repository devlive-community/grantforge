// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TokenClaims.class, TestPrincipals.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TokenClaimsTest
{
    private static final OAuth2TokenType ID_TOKEN = new OAuth2TokenType(OidcParameterNames.ID_TOKEN);

    @Autowired
    private TokenClaims claims;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    private long tenant;

    private long ada;

    @BeforeEach
    void createAccount()
    {
        tenant = tenants.save(Tenant.create("acme", "Acme")).requireId();
        ada = TenantContext.callInTenant(tenant, () -> accounts.save(UserAccount.create("ada", "h", Instant.EPOCH)
                .withDisplayName("Ada Lovelace").withEmail("ada@acme.example")).requireId());
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    @Test
    void saysWhoTheTokenIsForAndMoreInIdTokensTheScopesAllow()
    {
        Authentication signedIn = TestPrincipals.signedIn(ada, tenant, "ada");

        assertThat(customized(signedIn, OAuth2TokenType.ACCESS_TOKEN, Set.of("openid", "profile", "email")))
                .containsEntry(TokenClaims.TENANT, Long.toString(tenant)).containsEntry("preferred_username", "ada")
                .doesNotContainKeys("name", "email");
        assertThat(customized(signedIn, ID_TOKEN, Set.of("openid", "profile", "email")))
                .containsEntry("name", "Ada Lovelace").containsEntry("email", "ada@acme.example").containsEntry("email_verified", false);
        assertThat(customized(signedIn, ID_TOKEN, Set.of("openid"))).doesNotContainKeys("name", "email");
        // Tokens a client obtains for itself are about no account.
        assertThat(customized(new TestingAuthenticationToken("gf_web", null), OAuth2TokenType.ACCESS_TOKEN, Set.of())).containsOnlyKeys("sub");
    }

    @Test
    void refusesAccountsThatCanNoLongerSignIn()
    {
        Authentication signedIn = TestPrincipals.signedIn(ada, tenant, "ada");
        OAuthSubject subject = new OAuthSubject(ada, tenant, "ada", OAuthFixture.NOW);
        assertThat(claims.mayStillSignIn(subject)).isTrue();
        change(UserAccount::lockIndefinitely);
        assertThat(claims.mayStillSignIn(subject)).isFalse();
        assertThatThrownBy(() -> customized(signedIn, OAuth2TokenType.ACCESS_TOKEN, Set.of())).isInstanceOfSatisfying(
                OAuth2AuthenticationException.class, error -> assertThat(error.getError().getErrorCode()).isEqualTo("invalid_grant"));
        change(UserAccount::unlock);
        change(UserAccount::requirePasswordChange);
        assertThatThrownBy(() -> customized(signedIn, OAuth2TokenType.ACCESS_TOKEN, Set.of())).isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> customized(TestPrincipals.signedIn(ada + 1, tenant, "ghost"), OAuth2TokenType.ACCESS_TOKEN, Set.of()))
                .isInstanceOf(OAuth2AuthenticationException.class);
        Tenant acme = tenants.findById(tenant).orElseThrow();
        acme.suspend();
        tenants.save(acme);
        assertThatThrownBy(() -> customized(TestPrincipals.signedIn(ada, tenant, "ada"), ID_TOKEN, Set.of()))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void answersUserInfoForTheAccountOfTheAccessToken()
    {
        RegisteredClient client = RegisteredClient.withId("7").clientId("gf_web").redirectUri("https://app.example/cb")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).build();
        Instant now = Instant.now();
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "a", now, now.plusSeconds(60));
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client).id("auth-1").principalName(Long.toString(ada))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(Set.of("openid", "email"))
                .attribute(Principal.class.getName(), TestPrincipals.signedIn(ada, tenant, "ada")).accessToken(token).build();

        Map<String, Object> answered = claims.userInfo(userInfo(authorization, token)).getClaims();
        assertThat(answered).containsEntry("sub", Long.toString(ada)).containsEntry("email", "ada@acme.example").doesNotContainKey("name");
        OAuth2Authorization machine = OAuth2Authorization.from(authorization).attributes(attributes -> attributes.remove(Principal.class.getName()))
                .build();
        assertThatThrownBy(() -> claims.userInfo(userInfo(machine, token))).isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                error -> assertThat(error.getError().getErrorCode()).isEqualTo("invalid_token"));
    }

    private Map<String, Object> customized(Authentication principal, OAuth2TokenType type, Set<String> scopes)
    {
        JwtClaimsSet.Builder values = JwtClaimsSet.builder().subject("s");
        claims.customize(JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), values).principal(principal).tokenType(type)
                .authorizedScopes(scopes).build());
        return values.build().getClaims();
    }

    private static OidcUserInfoAuthenticationContext userInfo(OAuth2Authorization authorization, OAuth2AccessToken token)
    {
        return OidcUserInfoAuthenticationContext.with(new OidcUserInfoAuthenticationToken(TestPrincipals.signedIn(1, 1, "x")))
                .accessToken(token).authorization(authorization).build();
    }

    private void change(Consumer<UserAccount> change)
    {
        TenantContext.runInTenant(tenant, () -> {
            UserAccount account = accounts.findById(ada).orElseThrow();
            change.accept(account);
            accounts.save(account);
        });
    }
}
