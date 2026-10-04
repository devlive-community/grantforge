// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ClientGrant;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * What an OAuth client may do.
 *
 * @param name its name
 * @param redirectUris where the authorization server may send users back to
 * @param scopes what the client may ask for
 * @param grants how the client may obtain tokens
 * @param accessTokenTtl how long access tokens last
 * @param refreshTokenTtl how long refresh tokens last
 * @param enabled whether the client may obtain tokens at all
 */
public record ClientSettings(String name, List<String> redirectUris, Set<String> scopes, Set<ClientGrant> grants, Duration accessTokenTtl,
        Duration refreshTokenTtl, boolean enabled)
{
    /** Checks and copies the parts. */
    public ClientSettings
    {
        requireNonNull(name, "name");
        redirectUris = List.copyOf(redirectUris);
        scopes = Set.copyOf(scopes);
        grants = Set.copyOf(grants);
        requireNonNull(accessTokenTtl, "accessTokenTtl");
        requireNonNull(refreshTokenTtl, "refreshTokenTtl");
    }
}
