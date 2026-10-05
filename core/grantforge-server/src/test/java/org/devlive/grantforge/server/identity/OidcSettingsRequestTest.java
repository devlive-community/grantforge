// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OidcSettingsRequestTest
{
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
