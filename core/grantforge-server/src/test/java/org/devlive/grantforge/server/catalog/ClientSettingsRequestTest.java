// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.devlive.grantforge.authz.application.ClientSettings;
import org.devlive.grantforge.authz.domain.ClientGrant;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClientSettingsRequestTest
{
    @Test
    void fillsInDefaults()
    {
        ClientSettings settings = new ClientSettingsRequest("CRM", null, null, Set.of(ClientGrant.CLIENT_CREDENTIALS), null, null, null)
                .settings();

        assertThat(settings).isEqualTo(new ClientSettings("CRM", List.of(), Set.of(), Set.of(ClientGrant.CLIENT_CREDENTIALS),
                Duration.ofMinutes(15), Duration.ofDays(30), true));
        assertThat(new ClientSettingsRequest(null, List.of("https://a.example/cb"), Set.of("openid"), Set.of(), 5L, 2L, false).settings())
                .isEqualTo(new ClientSettings("", List.of("https://a.example/cb"), Set.of("openid"), Set.of(), Duration.ofMinutes(5),
                        Duration.ofHours(2), false));
    }

    @Test
    void requiresANameAndGrants()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ClientSettingsRequest good = new ClientSettingsRequest("CRM", null, null, Set.of(ClientGrant.CLIENT_CREDENTIALS), null, null, null);
            assertThat(factory.getValidator().validate(good)).isEmpty();
            assertThat(factory.getValidator().validate(new ClientSettingsRequest(" ", List.of(""), null, Set.of(), null, null, null))).hasSize(3);
        }
    }
}
