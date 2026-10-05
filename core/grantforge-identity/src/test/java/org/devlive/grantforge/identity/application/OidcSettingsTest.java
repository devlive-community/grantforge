// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcSettingsTest
{
    @Test
    void alwaysAsksForOpenIdAndTakesTheStandardClaims()
    {
        OidcSettings settings = new OidcSettings("https://login.example.com/", "grantforge", "profile email", "", " ", "");

        assertThat(settings.issuer()).isEqualTo("https://login.example.com");
        assertThat(settings.scopes()).isEqualTo("openid profile email");
        assertThat(settings.usernameClaim()).isEqualTo("preferred_username");
        assertThat(settings.displayNameClaim()).isEqualTo("name");
        assertThat(settings.emailClaim()).isEqualTo("email");
        assertThat(new OidcSettings("http://localhost:8080", "c", "email openid", "upn", "", "").scopes()).isEqualTo("email openid");
        assertThat(new OidcSettings("http://localhost:8080", "c", "", "upn", "", "").scopes()).isEqualTo("openid profile email");
    }

    @Test
    void refusesWhatCannotWork()
    {
        for (Runnable invalid : new Runnable[] {
            () -> new OidcSettings(" ", "c", "", "", "", ""),
            () -> new OidcSettings("ldap://login", "c", "", "", "", ""),
            () -> new OidcSettings("::", "c", "", "", "", ""),
            () -> new OidcSettings("https://login.example.com", " ", "", "", "", ""),
        }) {
            assertThatThrownBy(invalid::run).satisfies(error ->
                    assertThat(((GrantForgeException) error).getErrorCode()).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_INVALID));
        }
    }
}
