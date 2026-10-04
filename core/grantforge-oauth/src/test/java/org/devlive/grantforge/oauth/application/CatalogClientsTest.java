// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(CatalogClients.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogClientsTest
{
    @Autowired
    private CatalogClients registered;

    @Autowired
    private OAuthClientRepository clients;

    @Autowired
    private ApplicationRepository applications;

    @AfterEach
    void deleteRows()
    {
        clients.deleteAllInBatch();
        applications.deleteAllInBatch();
    }

    @Test
    void describesConfidentialClientsWithPkceRotationAndNoConsent()
    {
        OAuthClient web = OAuthFixture.client(applications, clients, "gf_web", ClientType.CONFIDENTIAL, true);
        web.rotateSecret("{noop}new", OAuthFixture.NOW.plusSeconds(10), Duration.ofDays(7));
        clients.save(web);

        RegisteredClient client = requireNonNull(registered.findByClientId("gf_web"));
        assertThat(client.getId()).isEqualTo(Long.toString(web.requireId()));
        assertThat(client.getClientName()).isEqualTo("CRM web");
        assertThat(client.getRedirectUris()).containsExactly("https://app.example/cb");
        assertThat(client.getScopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(client.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(AuthorizationGrantType.AUTHORIZATION_CODE,
                AuthorizationGrantType.REFRESH_TOKEN, AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(client.getClientAuthenticationMethods()).containsExactlyInAnyOrder(ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                ClientAuthenticationMethod.CLIENT_SECRET_POST);
        // The previous secret works during its grace period.
        assertThat(client.getClientSecret()).isEqualTo("{noop}new" + ClientSecrets.SEPARATOR + "{noop}secret");
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(15));
        assertThat(client.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(30));
        assertThat(client.getTokenSettings().getAuthorizationCodeTimeToLive()).isEqualTo(CatalogClients.CODE_TTL);
        assertThat(registered.findById(client.getId())).isNotNull();
    }

    @Test
    void publicClientsAuthenticateWithPkceAloneAndDisabledOnesAreNotFound()
    {
        OAuthFixture.client(applications, clients, "gf_spa", ClientType.PUBLIC, true);
        OAuthClient off = OAuthFixture.client(applications, clients, "gf_off", ClientType.PUBLIC, false);

        RegisteredClient spa = requireNonNull(registered.findByClientId("gf_spa"));
        assertThat(spa.getClientSecret()).isNull();
        assertThat(spa.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(registered.findByClientId("gf_off")).isNull();
        assertThat(registered.findById(Long.toString(off.requireId()))).isNull();
        assertThat(registered.findById("not-a-number")).isNull();
        assertThat(registered.findByClientId("nope")).isNull();
        assertThatThrownBy(() -> registered.save(spa)).isInstanceOf(UnsupportedOperationException.class);
    }
}
