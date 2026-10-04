// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StoredAuthorizationTest
{
    @Test
    void givesBackWhatItWasGivenAndReplacesIt()
    {
        AuthorizationContent content = OAuthTestData.signedIn("c", "a", "r");
        StoredAuthorization stored = StoredAuthorization.create("auth-1", content);

        assertThat(stored.getAuthorizationId()).isEqualTo("auth-1");
        assertThat(stored.content()).isEqualTo(content);
        assertThat(stored.getRefresh().getHash()).isEqualTo("r");

        AuthorizationContent machine = new AuthorizationContent("8", "gf_m", null, null, null, null, "client_credentials", Set.of(), null,
                IssuedToken.none(), content.access(), Set.of(), IssuedToken.none(), IssuedToken.none(), content.expiresAt());
        stored.replace(machine);
        assertThat(stored.content()).isEqualTo(machine);
        assertThat(stored.getRefresh().isPresent()).isFalse();
    }
}
