// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A client just registered or given a new secret, with the secret in clear: the only time it is shown.
 *
 * @param client the client
 * @param secret the secret, or {@code null} for a public client
 */
public record IssuedClient(OAuthClientView client, @Nullable String secret)
{
    /** Checks the client. */
    public IssuedClient
    {
        requireNonNull(client, "client");
    }

    /** Hides the secret from logs and debuggers. */
    @Override
    public String toString()
    {
        return "IssuedClient[client=" + client.clientId() + "]";
    }
}
