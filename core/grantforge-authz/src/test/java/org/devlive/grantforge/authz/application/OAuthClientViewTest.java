// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthClientViewTest
{
    @Test
    void showsThePreviousSecretOnlyWhileItWorks()
    {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        OAuthClient client = OAuthClient.create(2, "gf_a", ClientType.CONFIDENTIAL, "old", now);
        client.configure("CRM", List.of("https://a.example/cb"), Set.of("openid"), Set.of(ClientGrant.AUTHORIZATION_CODE),
                Duration.ofMinutes(15), Duration.ofDays(30), true);
        ReflectionTestUtils.setField(client, "id", 7L);
        client.rotateSecret("new", now, Duration.ofHours(1));

        OAuthClientView during = OAuthClientView.from(client, now);
        assertThat(during.id()).isEqualTo(7);
        assertThat(during.applicationId()).isEqualTo(2);
        assertThat(during.previousSecretExpiresAt()).isEqualTo(now.plus(Duration.ofHours(1)));
        assertThat(during.createdAt()).isEqualTo(now);
        assertThat(OAuthClientView.from(client, now.plus(Duration.ofHours(2))).previousSecretExpiresAt()).isNull();
    }
}
