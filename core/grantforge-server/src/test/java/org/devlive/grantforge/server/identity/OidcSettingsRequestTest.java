// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class OidcSettingsRequestTest
{
    @Test
    void copiesAuthorizationParameters()
    {
        Map<String, String> parameters = new HashMap<>(Map.of("provider_hint", "github"));
        OidcSettingsRequest request = new OidcSettingsRequest("https://login.example.com", "c", null, null, null, null, parameters);
        parameters.put("provider_hint", "changed");
        requireNonNull(request.authorizationParameters()).put("provider_hint", "changed again");
        assertThat(request.settings().authorizationParameters()).containsEntry("provider_hint", "github");
    }

    @Test
    void appliesTheDefaults()
    {
        assertThat(new OidcSettingsRequest("https://login.example.com", "c", null, "upn", null, null).settings())
                .satisfies(settings -> {
                    assertThat(settings.usernameClaim()).isEqualTo("upn");
                    assertThat(settings.emailClaim()).isEqualTo("email");
                });
    }
}
