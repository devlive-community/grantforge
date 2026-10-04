// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import java.time.Instant;
import java.util.Set;

/** Authorizations for the domain tests. */
final class OAuthTestData
{
    static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");

    private OAuthTestData()
    {
    }

    static AuthorizationContent signedIn(String code, String access, String refresh)
    {
        return new AuthorizationContent("7", "42", 42L, 3L, "ada", NOW, "authorization_code", Set.of("openid", "profile"),
                new CodeRequest("https://id.example/oauth2/authorize", "https://app.example/cb", Set.of("openid", "profile"), "s1",
                        "challenge", "S256", "n1"),
                new IssuedToken(code, NOW, NOW.plusSeconds(300), true), new IssuedToken(access, NOW, NOW.plusSeconds(900), false),
                Set.of("openid", "profile"), new IssuedToken(refresh, NOW, NOW.plusSeconds(3600), false), IssuedToken.none(),
                NOW.plusSeconds(3600));
    }
}
