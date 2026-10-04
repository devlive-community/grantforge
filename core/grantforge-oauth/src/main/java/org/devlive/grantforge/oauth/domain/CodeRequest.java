// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.jspecify.annotations.Nullable;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * What the authorization request asked for, kept until its code is exchanged: the token request must repeat the redirect
 * URI and prove the PKCE challenge, and the ID token carries the nonce.
 *
 * @param authorizationUri the authorization endpoint the request went to
 * @param redirectUri where the code was sent
 * @param scopes the scopes asked for
 * @param state the client's state, or {@code null}
 * @param codeChallenge the PKCE challenge, or {@code null}
 * @param codeChallengeMethod the PKCE method, or {@code null}
 * @param nonce the OpenID Connect nonce, or {@code null}
 */
public record CodeRequest(String authorizationUri, @Nullable String redirectUri, Set<String> scopes, @Nullable String state,
        @Nullable String codeChallenge, @Nullable String codeChallengeMethod, @Nullable String nonce)
{
    /** Checks and copies the parts. */
    public CodeRequest
    {
        requireNonNull(authorizationUri, "authorizationUri");
        scopes = Set.copyOf(scopes);
    }
}
