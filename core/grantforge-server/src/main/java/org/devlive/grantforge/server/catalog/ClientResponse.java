// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.OAuthClientView;
import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * An OAuth client, without its secret; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the client's record
 * @param applicationId its application
 * @param clientId its public identifier
 * @param name its name
 * @param type confidential or public
 * @param redirectUris where users may be sent back to
 * @param scopes what it may ask for, sorted
 * @param grants how it may obtain tokens, in declaration order
 * @param accessTokenMinutes how long access tokens last
 * @param refreshTokenHours how long refresh tokens last
 * @param enabled whether it may obtain tokens
 * @param secretRotatedAt when its secret was last set, for a confidential client
 * @param previousSecretExpiresAt when the secret before the last rotation stops working, while it works
 * @param createdAt when it was registered
 */
public record ClientResponse(String id, String applicationId, String clientId, String name, ClientType type, List<String> redirectUris,
        List<String> scopes, List<ClientGrant> grants, long accessTokenMinutes, long refreshTokenHours, boolean enabled,
        @Nullable Instant secretRotatedAt, @Nullable Instant previousSecretExpiresAt, Instant createdAt)
{
    /** Copies the lists. */
    public ClientResponse
    {
        redirectUris = List.copyOf(redirectUris);
        scopes = List.copyOf(scopes);
        grants = List.copyOf(grants);
    }

    /**
     * Converts a view.
     *
     * @param client the view
     * @return the response
     */
    public static ClientResponse from(OAuthClientView client)
    {
        return new ClientResponse(Long.toString(client.id()), Long.toString(client.applicationId()), client.clientId(), client.name(),
                client.type(), client.redirectUris(), client.scopes().stream().sorted().toList(),
                client.grants().stream().sorted(Comparator.naturalOrder()).toList(), client.accessTokenTtl().toMinutes(),
                client.refreshTokenTtl().toHours(), client.enabled(), client.secretRotatedAt(), client.previousSecretExpiresAt(),
                client.createdAt());
    }
}
