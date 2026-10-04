// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNullElse;

/**
 * An OAuth client, without its secret.
 *
 * @param id the client's record
 * @param applicationId its application
 * @param clientId its public identifier
 * @param name its name
 * @param type confidential or public
 * @param redirectUris where users may be sent back to
 * @param scopes what it may ask for
 * @param grants how it may obtain tokens
 * @param accessTokenTtl how long access tokens last
 * @param refreshTokenTtl how long refresh tokens last
 * @param enabled whether it may obtain tokens
 * @param secretRotatedAt when its secret was last set, for a confidential client
 * @param previousSecretExpiresAt when the secret before the last rotation stops working, while it works
 * @param createdAt when it was registered
 */
public record OAuthClientView(long id, long applicationId, String clientId, String name, ClientType type, List<String> redirectUris,
        Set<String> scopes, Set<ClientGrant> grants, Duration accessTokenTtl, Duration refreshTokenTtl, boolean enabled,
        @Nullable Instant secretRotatedAt, @Nullable Instant previousSecretExpiresAt, Instant createdAt)
{
    /** Copies the collections. */
    public OAuthClientView
    {
        redirectUris = List.copyOf(redirectUris);
        scopes = Set.copyOf(scopes);
        grants = Set.copyOf(grants);
    }

    /**
     * Describes a client at a moment.
     *
     * @param client the client
     * @param now the current time, to leave out a previous secret that no longer works
     * @return the view
     */
    public static OAuthClientView from(OAuthClient client, Instant now)
    {
        Instant previous = client.previousSecretHash(now) == null ? null : client.getPreviousSecretExpiresAt();
        return new OAuthClientView(client.requireId(), client.getApplicationId(), client.getClientId(), client.getName(), client.getType(),
                client.getRedirectUris(), client.getScopes(), client.getGrants(), client.getAccessTokenTtl(), client.getRefreshTokenTtl(),
                client.isEnabled(), client.getSecretRotatedAt(), previous, requireNonNullElse(client.getCreatedAt(), now));
    }
}
