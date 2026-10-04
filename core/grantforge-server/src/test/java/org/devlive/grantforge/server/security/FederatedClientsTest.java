// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.IdentitySourceSettings;
import org.devlive.grantforge.identity.application.OidcSettings;
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.identity.domain.IdentitySourceRepository;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FederatedClientsTest
{
    private static FakeProvider provider;

    private final IdentitySourceRepository sources = mock(IdentitySourceRepository.class);
    private final IdentitySourceSettings settings = mock(IdentitySourceSettings.class);
    private final FederatedClients clients = new FederatedClients(sources, settings);

    @BeforeAll
    static void startProvider() throws Exception
    {
        provider = FakeProvider.start();
    }

    @AfterAll
    static void stopProvider()
    {
        provider.close();
    }

    private IdentitySource source(String code, IdentitySourceType type, boolean enabled, String issuer, long version)
    {
        IdentitySource source = IdentitySource.create(code, type);
        source.configure(code, enabled, true, "{}", null);
        ReflectionTestUtils.setField(source, "version", version);
        when(sources.findByCode(code)).thenReturn(Optional.of(source));
        when(settings.oidc(source)).thenReturn(new OidcSettings(issuer, "grantforge", "openid email", "", "", ""));
        return source;
    }

    @Test
    void describesProvidersByTheirDiscoveryDocumentOnce()
    {
        IdentitySource okta = source("okta", IdentitySourceType.OIDC, true, provider.issuer(), 1);

        ClientRegistration registration = clients.findByRegistrationId("okta");

        assertThat(registration).isNotNull();
        assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo(provider.issuer() + "/token");
        assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "email");
        // Without a secret the client is a public one.
        assertThat(registration.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
        assertThat(clients.findByRegistrationId("okta")).isSameAs(registration);
        // A changed source is read again.
        ReflectionTestUtils.setField(okta, "version", 2L);
        when(settings.secret(okta)).thenReturn("s");
        assertThat(clients.findByRegistrationId("okta")).isNotSameAs(registration).extracting(ClientRegistration::getClientAuthenticationMethod)
                .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(clients.settings("okta")).isPresent();
    }

    @Test
    void knowsNoDisabledOrUnreachableProviders()
    {
        source("off", IdentitySourceType.OIDC, false, provider.issuer(), 1);
        source("ldap", IdentitySourceType.LDAP, true, provider.issuer(), 1);
        source("down", IdentitySourceType.OIDC, true, "http://localhost:1", 1);

        assertThat(clients.findByRegistrationId("off")).isNull();
        assertThat(clients.findByRegistrationId("ldap")).isNull();
        assertThat(clients.findByRegistrationId("nobody")).isNull();
        assertThat(clients.findByRegistrationId("down")).isNull();
        assertThat(clients.settings("off")).isEmpty();
    }
}
