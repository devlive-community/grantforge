// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.application.ClientAccessEnded;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.devlive.grantforge.oauth.domain.RetiredTokenRepository;
import org.devlive.grantforge.oauth.domain.StoredAuthorizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({StoredAuthorizations.class, CatalogClients.class, TestPrincipals.class, AuditLog.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoredAuthorizationsTest
{
    private static final OAuth2TokenType CODE = new OAuth2TokenType("code");

    @Autowired
    private StoredAuthorizations service;

    @Autowired
    private CatalogClients registered;

    @Autowired
    private StoredAuthorizationRepository authorizations;

    @Autowired
    private RetiredTokenRepository retired;

    @Autowired
    private OAuthClientRepository clients;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private OAuthClient web;

    private RegisteredClient client;

    @BeforeEach
    void createClient()
    {
        web = OAuthFixture.client(applications, clients, "gf_web", ClientType.CONFIDENTIAL, true);
        client = requireNonNull(registered.findByClientId("gf_web"));
    }

    @AfterEach
    void deleteRows()
    {
        authorizations.deleteAllInBatch();
        retired.deleteAllInBatch();
        events.deleteAllInBatch();
        clients.deleteAllInBatch();
        applications.deleteAllInBatch();
    }

    @Test
    void storesOnlyHashesAndGivesBackThePresentedToken()
    {
        service.save(OAuthFixture.codeIssued(client, "auth-1", "code-1"));

        OAuth2Authorization found = requireNonNull(service.findByToken("code-1", CODE));
        assertThat(found.getId()).isEqualTo("auth-1");
        assertThat(found.getPrincipalName()).isEqualTo("42");
        assertThat(found.getAuthorizedScopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(requireNonNull(found.getToken(OAuth2AuthorizationCode.class)).getToken().getTokenValue()).isEqualTo("code-1");
        OAuth2AuthorizationRequest request = requireNonNull(found.getAttribute(OAuth2AuthorizationRequest.class.getName()));
        assertThat(request.getRedirectUri()).isEqualTo("https://app.example/cb");
        assertThat(request.getState()).isEqualTo("s1");
        assertThat(request.getAdditionalParameters()).containsEntry("code_challenge", "challenge").containsEntry("nonce", "n1");
        assertThat(found.<Authentication>getAttribute(Principal.class.getName()))
                .satisfies(principal -> assertThat(requireNonNull(principal).getPrincipal()).isEqualTo(new OAuthSubject(42, 3, "ada", OAuthFixture.NOW)));
        // Nothing usable is stored.
        assertThat(authorizations.findByAuthorizationId("auth-1")).hasValueSatisfying(stored ->
                assertThat(stored.content().code().getHash()).isEqualTo(TokenHashes.of("code-1")));
        assertThat(service.findByToken("code-1", OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(service.findByToken(TokenHashes.placeholder(TokenHashes.of("code-1")), null)).isNull();
        assertThat(service.findById("auth-1")).isNotNull();
        assertThat(service.findById("nope")).isNull();
        assertThat(audits()).containsExactly(AuditAction.OAUTH_AUTHORIZED + ":SUCCESS:42:gf_web");
    }

    @Test
    void rotatesRefreshTokensAndRevokesTheAuthorizationWhenAnOldOneIsReplayed()
    {
        service.save(OAuthFixture.codeIssued(client, "auth-1", "code-1"));
        OAuth2Authorization codeStage = requireNonNull(service.findByToken("code-1", CODE));
        service.save(exchanged(codeStage, "access-1", "refresh-1"));

        OAuth2Authorization byAccess = requireNonNull(service.findByToken("access-1", OAuth2TokenType.ACCESS_TOKEN));
        assertThat(requireNonNull(byAccess.getAccessToken()).getToken().getTokenValue()).isEqualTo("access-1");
        // Other tokens come back as placeholders, which find nothing and keep their hash when saved.
        assertThat(requireNonNull(byAccess.getRefreshToken()).getToken().getTokenValue()).startsWith(TokenHashes.UNKNOWN);
        assertThat(requireNonNull(byAccess.getToken(OAuth2AuthorizationCode.class)).isInvalidated()).isTrue();
        OAuth2Authorization byRefresh = requireNonNull(service.findByToken("refresh-1", null));
        service.save(OAuth2Authorization.from(byRefresh).accessToken(access("access-2")).refreshToken(refresh("refresh-2")).build());
        assertThat(service.findByToken("refresh-2", OAuth2TokenType.REFRESH_TOKEN)).isNotNull();
        assertThat(retired.findByTokenHash(TokenHashes.of("refresh-1"))).isPresent();

        // The old refresh token again: stolen or replayed. Its whole authorization goes.
        assertThat(service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(service.findByToken("refresh-2", OAuth2TokenType.REFRESH_TOKEN)).isNull();
        assertThat(service.findByToken("access-2", OAuth2TokenType.ACCESS_TOKEN)).isNull();
        assertThat(retired.count()).isZero();
        assertThat(audits()).containsExactly(AuditAction.OAUTH_AUTHORIZED + ":SUCCESS:42:gf_web",
                AuditAction.OAUTH_TOKEN_REPLAYED + ":FAILURE:42:gf_web");
    }

    @Test
    void refusesASecondChangeFromTheSameRead()
    {
        service.save(OAuthFixture.codeIssued(client, "auth-1", "code-1"));
        service.save(exchanged(requireNonNull(service.findByToken("code-1", CODE)), "access-1", "refresh-1"));
        OAuth2Authorization first = requireNonNull(service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN));
        OAuth2Authorization second = requireNonNull(service.findByToken("refresh-1", OAuth2TokenType.REFRESH_TOKEN));

        service.save(OAuth2Authorization.from(first).refreshToken(refresh("refresh-2")).build());
        assertThatThrownBy(() -> service.save(OAuth2Authorization.from(second).refreshToken(refresh("refresh-3")).build()))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, error ->
                        assertThat(error.getError().getErrorCode()).isEqualTo("invalid_grant"));
        assertThat(service.findByToken("refresh-3", null)).isNull();
    }

    @Test
    void forgetsAuthorizationsOfDisabledOrDeletedClients()
    {
        service.save(OAuthFixture.codeIssued(client, "auth-1", "code-1"));
        web.configure(web.getName(), web.getRedirectUris(), web.getScopes(), web.getGrants(), web.getAccessTokenTtl(),
                web.getRefreshTokenTtl(), false);
        clients.save(web);
        assertThat(service.findByToken("code-1", CODE)).isNull();

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                service.clientAccessEnded(new ClientAccessEnded(web.requireId(), "gf_web")));
        assertThat(authorizations.count()).isZero();
    }

    @Test
    void removesAuthorizationsAndPurgesExpiredOnes()
    {
        service.save(OAuthFixture.codeIssued(client, "auth-1", "code-1"));
        service.save(OAuthFixture.codeIssued(client, "auth-2", "code-2"));
        service.remove(requireNonNull(service.findById("auth-1")));

        assertThat(service.findById("auth-1")).isNull();
        assertThat(service.purgeExpired(OAuthFixture.NOW.plusSeconds(299))).isZero();
        assertThat(service.purgeExpired(OAuthFixture.NOW.plusSeconds(301))).isOne();
    }

    @Test
    void storesTokensAClientObtainsForItself()
    {
        OAuth2Authorization machine = OAuth2Authorization.withRegisteredClient(client).id("auth-m").principalName("gf_web")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).authorizedScopes(Set.of())
                .accessToken(access("machine-1")).build();
        service.save(machine);

        OAuth2Authorization found = requireNonNull(service.findByToken("machine-1", null));
        assertThat(found.getPrincipalName()).isEqualTo("gf_web");
        assertThat(found.<Object>getAttribute(Principal.class.getName())).isNull();
        assertThat(audits()).isEmpty();
    }

    private static OAuth2Authorization exchanged(OAuth2Authorization codeStage, String access, String refresh)
    {
        OAuth2AuthorizationCode code = requireNonNull(codeStage.getToken(OAuth2AuthorizationCode.class)).getToken();
        return OAuth2Authorization.from(codeStage).accessToken(access(access)).refreshToken(refresh(refresh))
                .token(code, metadata -> metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true)).build();
    }

    private static OAuth2AccessToken access(String value)
    {
        Instant now = Instant.now();
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value, now, now.plusSeconds(900), Set.of("openid"));
    }

    private static OAuth2RefreshToken refresh(String value)
    {
        Instant now = Instant.now();
        return new OAuth2RefreshToken(value, now, now.plusSeconds(3600));
    }

    private List<String> audits()
    {
        return events.findAll(Sort.by("occurredAt", "id")).stream()
                .map((AuditEvent event) -> event.getAction() + ":" + event.getOutcome() + ":" + event.getActorId() + ":" + event.getTargetId())
                .toList();
    }
}
