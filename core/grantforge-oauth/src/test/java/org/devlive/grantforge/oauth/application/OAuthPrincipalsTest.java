// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthPrincipalsTest
{
    @Test
    void readsTheLatestFactorOfASignIn()
    {
        List<GrantedAuthority> authorities = new ArrayList<>(OAuthPrincipals.passwordAt(Instant.EPOCH));
        authorities.addAll(OAuthPrincipals.passwordAt(Instant.EPOCH.plusSeconds(5)));
        authorities.add(new SimpleGrantedAuthority("ROLE_X"));

        assertThat(OAuthPrincipals.signedInAt(new TestingAuthenticationToken("ada", null, authorities))).contains(Instant.EPOCH.plusSeconds(5));
        assertThat(OAuthPrincipals.signedInAt(new TestingAuthenticationToken("ada", null, List.of()))).isEmpty();
        assertThat(OAuthPrincipals.passwordAt(Instant.EPOCH)).singleElement()
                .satisfies(authority -> assertThat(authority.getAuthority()).isEqualTo("FACTOR_PASSWORD"));
    }
}
