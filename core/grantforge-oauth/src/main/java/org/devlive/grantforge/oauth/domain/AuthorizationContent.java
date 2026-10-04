// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Everything an authorization holds, without token values.
 *
 * @param registeredClientId the client's record, as the authorization server identifies it
 * @param principalName the account ID, or the client ID for tokens a client obtains for itself
 * @param accountId the account, or {@code null} for a client's own tokens
 * @param tenantId the account's tenant, or {@code null}
 * @param username the account's login name at authorization, or {@code null}
 * @param authenticatedAt when the account signed in, or {@code null}
 * @param grantType how the authorization began, such as {@code authorization_code}
 * @param authorizedScopes the scopes granted
 * @param request the authorization request of a code, or {@code null}
 * @param code the authorization code
 * @param access the access token
 * @param accessScopes the scopes of the access token
 * @param refresh the refresh token
 * @param idToken the ID token
 * @param expiresAt when the last of its tokens expires, after which the row goes
 */
public record AuthorizationContent(String registeredClientId, String principalName, @Nullable Long accountId, @Nullable Long tenantId,
        @Nullable String username, @Nullable Instant authenticatedAt, String grantType, Set<String> authorizedScopes, @Nullable CodeRequest request, IssuedToken code,
        IssuedToken access, Set<String> accessScopes, IssuedToken refresh, IssuedToken idToken, Instant expiresAt)
{
    /** Checks and copies the parts. */
    public AuthorizationContent
    {
        requireNonNull(registeredClientId, "registeredClientId");
        requireNonNull(principalName, "principalName");
        requireNonNull(grantType, "grantType");
        authorizedScopes = Set.copyOf(authorizedScopes);
        requireNonNull(code, "code");
        requireNonNull(access, "access");
        accessScopes = Set.copyOf(accessScopes);
        requireNonNull(refresh, "refresh");
        requireNonNull(idToken, "idToken");
        requireNonNull(expiresAt, "expiresAt");
    }
}
