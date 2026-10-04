// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import org.devlive.grantforge.oauth.application.OAuthPrincipals;
import org.devlive.grantforge.oauth.application.OAuthSubject;
import org.devlive.grantforge.server.security.SessionUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPrincipalsTest
{
    private static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");

    private final SessionPrincipals principals = new SessionPrincipals(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void translatesSessionUsersBothWays()
    {
        Instant signedIn = NOW.minusSeconds(60);
        Authentication session = UsernamePasswordAuthenticationToken.authenticated(new SessionUser(42, 3, "ada"), null,
                OAuthPrincipals.passwordAt(signedIn));

        OAuthSubject subject = principals.subjectOf(session).orElseThrow();
        assertThat(subject).isEqualTo(new OAuthSubject(42, 3, "ada", signedIn));
        Authentication rebuilt = principals.authenticationOf(subject);
        assertThat(rebuilt.getPrincipal()).isEqualTo(new SessionUser(42, 3, "ada"));
        assertThat(rebuilt.isAuthenticated()).isTrue();
        assertThat(OAuthPrincipals.signedInAt(rebuilt)).contains(signedIn);
    }

    @Test
    void countsOlderSessionsAsSignedInNowAndIgnoresOtherPrincipals()
    {
        Authentication old = UsernamePasswordAuthenticationToken.authenticated(new SessionUser(42, 3, "ada"), null, List.of());

        assertThat(principals.subjectOf(old)).map(OAuthSubject::authenticatedAt).contains(NOW);
        assertThat(principals.subjectOf(new TestingAuthenticationToken("gf_web", null))).isEmpty();
    }
}
