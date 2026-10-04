// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientTest
{
    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void keepsItsSettings()
    {
        OAuthClient client = OAuthClient.create(1, "gf_a", ClientType.CONFIDENTIAL, "hash", NOW);
        client.configure("CRM", List.of("https://crm.example/cb", "http://localhost:8080/cb"), Set.of("openid", "email"),
                Set.of(ClientGrant.AUTHORIZATION_CODE, ClientGrant.REFRESH_TOKEN), Duration.ofMinutes(15), Duration.ofDays(30), true);

        assertThat(client.getApplicationId()).isOne();
        assertThat(client.getClientId()).isEqualTo("gf_a");
        assertThat(client.getName()).isEqualTo("CRM");
        assertThat(client.getRedirectUris()).containsExactly("https://crm.example/cb", "http://localhost:8080/cb");
        assertThat(client.getScopes()).containsExactlyInAnyOrder("openid", "email");
        assertThat(client.getGrants()).containsExactlyInAnyOrder(ClientGrant.AUTHORIZATION_CODE, ClientGrant.REFRESH_TOKEN);
        assertThat(client.getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(client.getRefreshTokenTtl()).isEqualTo(Duration.ofDays(30));
        assertThat(client.isEnabled()).isTrue();
        assertThat(client.getSecretHash()).isEqualTo("hash");
        assertThat(client.getSecretRotatedAt()).isEqualTo(NOW);

        client.configure("CRM", List.of(), Set.of(), Set.of(ClientGrant.CLIENT_CREDENTIALS), Duration.ofMinutes(5), Duration.ofHours(1), false);
        assertThat(client.getRedirectUris()).isEmpty();
        assertThat(client.getScopes()).isEmpty();
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void rotatedSecretsKeepTheOldOneForTheGracePeriod()
    {
        OAuthClient client = OAuthClient.create(1, "gf_a", ClientType.CONFIDENTIAL, "old", NOW);
        Instant later = NOW.plusSeconds(60);

        client.rotateSecret("new", later, Duration.ofHours(1));
        assertThat(client.getSecretHash()).isEqualTo("new");
        assertThat(client.getSecretRotatedAt()).isEqualTo(later);
        assertThat(client.previousSecretHash(later)).isEqualTo("old");
        assertThat(client.getPreviousSecretExpiresAt()).isEqualTo(later.plus(Duration.ofHours(1)));
        assertThat(client.previousSecretHash(later.plus(Duration.ofHours(1)))).isNull();

        client.rotateSecret("newer", later, Duration.ZERO);
        assertThat(client.previousSecretHash(later)).isNull();
        assertThat(client.getPreviousSecretExpiresAt()).isNull();
    }

    @Test
    void onlyConfidentialClientsHaveSecrets()
    {
        assertThatThrownBy(() -> OAuthClient.create(1, "gf_a", ClientType.CONFIDENTIAL, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OAuthClient.create(1, "gf_a", ClientType.PUBLIC, "hash", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        OAuthClient spa = OAuthClient.create(1, "gf_b", ClientType.PUBLIC, null, NOW);

        assertThat(spa.getSecretHash()).isNull();
        assertThat(spa.getSecretRotatedAt()).isNull();
        assertThatThrownBy(() -> spa.rotateSecret("x", NOW, Duration.ZERO)).isInstanceOf(IllegalStateException.class);
    }
}
