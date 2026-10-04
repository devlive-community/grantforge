// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.devlive.grantforge.sdk.GrantForgeException.Reason;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** Checks in code what the user of the current request may do in this application. */
public final class GrantForge
{
    private final GrantForgeClient client;
    private final AccessTokenResolver tokens;

    /**
     * Creates the checks.
     *
     * @param client asks GrantForge
     * @param tokens finds the current user's token
     */
    public GrantForge(GrantForgeClient client, AccessTokenResolver tokens)
    {
        this.client = requireNonNull(client, "client");
        this.tokens = requireNonNull(tokens, "tokens");
    }

    /**
     * Returns what the current user may do.
     *
     * @return the user's permissions
     * @throws GrantForgeException {@link Reason#UNAUTHENTICATED} if the request carries no token GrantForge accepts, or as
     *         {@link GrantForgeClient#authorization(String)}
     */
    public UserAuthorization current()
    {
        String token = tokens.currentToken();
        if (token == null) {
            throw new GrantForgeException(Reason.UNAUTHENTICATED, "the request carries no GrantForge access token", null);
        }
        return client.authorization(token);
    }

    /**
     * Returns whether the current user may call an API.
     *
     * @param permission the API's permission code
     * @return {@code true} if granted
     */
    public boolean hasPermission(String permission)
    {
        return current().hasPermission(permission);
    }

    /**
     * Returns whether the current user may use a resource, such as a page or a button.
     *
     * @param resource the resource's code
     * @return {@code true} if granted
     */
    public boolean hasResource(String resource)
    {
        return current().hasResource(resource);
    }

    /**
     * Requires permissions of the current user.
     *
     * @param permissions the permission codes, all of which the user must hold
     * @throws GrantForgeException {@link Reason#FORBIDDEN} naming the first one missing, or as {@link #current()}
     */
    public void require(String... permissions)
    {
        UserAuthorization user = current();
        for (String permission : List.of(permissions)) {
            if (!user.hasPermission(permission)) {
                throw new GrantForgeException(Reason.FORBIDDEN, "user " + user.username() + " lacks permission " + permission, null);
            }
        }
    }
}
