// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import org.devlive.grantforge.oauth.application.OAuthPrincipals;
import org.devlive.grantforge.oauth.application.OAuthSubject;
import org.devlive.grantforge.server.security.SessionUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * The console's session users as the authorization server stores them: an application signs in whoever uses the console.
 * A session from before sign-ins recorded their password factor counts as signed in now.
 */
@Component
public final class SessionPrincipals
        implements OAuthPrincipals
{
    private final Clock clock;

    /**
     * Creates the translation.
     *
     * @param clock the current time, for sessions without a recorded sign-in time
     */
    public SessionPrincipals(Clock clock)
    {
        this.clock = requireNonNull(clock, "clock");
    }

    @Override
    public Optional<OAuthSubject> subjectOf(Authentication authentication)
    {
        return authentication.getPrincipal() instanceof SessionUser user
                ? Optional.of(new OAuthSubject(user.accountId(), user.tenantId(), user.username(),
                        OAuthPrincipals.signedInAt(authentication).orElseGet(clock::instant)))
                : Optional.empty();
    }

    @Override
    public Authentication authenticationOf(OAuthSubject subject)
    {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionUser(subject.accountId(), subject.tenantId(), subject.username()),
                null, OAuthPrincipals.passwordAt(subject.authenticatedAt()));
    }
}
