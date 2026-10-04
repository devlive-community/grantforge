// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Sign-ins whose principal is the subject itself, standing in for the server's session users. */
@Component
class TestPrincipals
        implements OAuthPrincipals
{
    @Override
    public Optional<OAuthSubject> subjectOf(Authentication authentication)
    {
        return authentication.getPrincipal() instanceof OAuthSubject subject ? Optional.of(subject) : Optional.empty();
    }

    @Override
    public Authentication authenticationOf(OAuthSubject subject)
    {
        return UsernamePasswordAuthenticationToken.authenticated(subject, null, OAuthPrincipals.passwordAt(subject.authenticatedAt()));
    }

    static Authentication signedIn(long accountId, long tenantId, String username)
    {
        return new TestPrincipals().authenticationOf(new OAuthSubject(accountId, tenantId, username, OAuthFixture.NOW));
    }
}
