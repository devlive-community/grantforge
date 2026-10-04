// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Translates between the console's signed-in user and what an authorization stores, so this module does not depend on how
 * the server keeps sessions. The server provides the implementation.
 */
public interface OAuthPrincipals
{
    /**
     * Returns the account a sign-in is for.
     *
     * @param authentication the sign-in, or a client's own authentication
     * @return the account, or empty if the authentication is not an account's
     */
    Optional<OAuthSubject> subjectOf(Authentication authentication);

    /**
     * Rebuilds the sign-in an authorization was made for, with the password factor issued at
     * {@link OAuthSubject#authenticatedAt()}, as the ID token's {@code auth_time} is read from it.
     *
     * @param subject the account
     * @return an authenticated authentication of the account
     */
    Authentication authenticationOf(OAuthSubject subject);

    /**
     * Returns when a sign-in last proved a factor, such as the password.
     *
     * @param authentication the sign-in
     * @return the time, or empty if it carries no factor
     */
    static Optional<Instant> signedInAt(Authentication authentication)
    {
        return authentication.getAuthorities().stream().filter(FactorGrantedAuthority.class::isInstance)
                .map(authority -> ((FactorGrantedAuthority) authority).getIssuedAt()).max(Instant::compareTo);
    }

    /**
     * Returns the authorities of a sign-in with the password at a time.
     *
     * @param at when the password was proved
     * @return the password factor
     */
    static List<GrantedAuthority> passwordAt(Instant at)
    {
        return List.of(FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(at).build());
    }
}
