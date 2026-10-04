// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.IssuedClient;
import org.jspecify.annotations.Nullable;

/**
 * A client just registered or given a new secret; the secret is shown this once.
 *
 * @param client the client
 * @param secret the secret, or {@code null} for a public client
 */
public record IssuedClientResponse(ClientResponse client, @Nullable String secret)
{
    /**
     * Converts an issued client.
     *
     * @param issued the client and its secret
     * @return the response
     */
    public static IssuedClientResponse from(IssuedClient issued)
    {
        return new IssuedClientResponse(ClientResponse.from(issued.client()), issued.secret());
    }

    /** Hides the secret from logs. */
    @Override
    public String toString()
    {
        return "IssuedClientResponse[client=" + client.clientId() + "]";
    }
}
