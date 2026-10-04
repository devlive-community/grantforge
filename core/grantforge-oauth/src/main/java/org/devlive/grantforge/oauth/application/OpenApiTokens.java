// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Recognises the access tokens applications call the open API with. A token must be signed by this authorization server
 * and unexpired, and its authorization must still exist: a token revoked, replayed or issued to a client since deleted or
 * disabled stops working at once, not when it expires. A token issued for an account works only while the account may
 * still sign in.
 */
@Component
public final class OpenApiTokens
{
    private final JwtDecoder decoder;
    private final StoredAuthorizations authorizations;
    private final OAuthClientRepository clients;
    private final OAuthPrincipals principals;
    private final TokenClaims accounts;

    /**
     * Creates the recogniser.
     *
     * @param decoder checks signatures and expiry
     * @param authorizations the stored authorizations
     * @param clients the catalog's clients, for their applications
     * @param principals translates sign-ins
     * @param accounts whether accounts may still sign in
     */
    public OpenApiTokens(JwtDecoder decoder, StoredAuthorizations authorizations, OAuthClientRepository clients, OAuthPrincipals principals,
            TokenClaims accounts)
    {
        this.decoder = requireNonNull(decoder, "decoder");
        this.authorizations = requireNonNull(authorizations, "authorizations");
        this.clients = requireNonNull(clients, "clients");
        this.principals = requireNonNull(principals, "principals");
        this.accounts = requireNonNull(accounts, "accounts");
    }

    /**
     * Recognises a bearer token.
     *
     * @param token the token
     * @return the caller, or empty if the token does not, or no longer, work
     */
    public Optional<OpenCaller> authenticate(String token)
    {
        try {
            decoder.decode(token);
        }
        catch (JwtException invalid) {
            return Optional.empty();
        }
        OAuth2Authorization authorization = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        OAuth2Authorization.Token<OAuth2AccessToken> access = authorization == null ? null : authorization.getAccessToken();
        if (authorization == null || access == null || !access.isActive()) {
            return Optional.empty();
        }
        OAuthClient client = parse(authorization.getRegisteredClientId()).flatMap(clients::findById).filter(OAuthClient::isEnabled)
                .orElse(null);
        if (client == null) {
            return Optional.empty();
        }
        Authentication signedIn = authorization.getAttribute(Principal.class.getName());
        OAuthSubject subject = signedIn == null ? null : principals.subjectOf(signedIn).orElse(null);
        if (subject != null && !accounts.mayStillSignIn(subject)) {
            return Optional.empty();
        }
        return Optional.of(new OpenCaller(client.getClientId(), client.getApplicationId(), subject, access.getToken().getScopes()));
    }

    private static Optional<Long> parse(String id)
    {
        try {
            return Optional.of(Long.parseLong(id));
        }
        catch (NumberFormatException notOurs) {
            return Optional.empty();
        }
    }
}
