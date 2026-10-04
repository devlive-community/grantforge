// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * What applications need to use the authorization server, and its keys.
 *
 * @param issuer the issuer identifier tokens carry
 * @param discoveryUrl the OpenID Connect discovery document, from which clients read every endpoint
 * @param keys the signing keys, newest first
 */
public record OAuthServerResponse(String issuer, String discoveryUrl, List<OAuthSigningKeyResponse> keys)
{
    /** Checks and copies the parts. */
    public OAuthServerResponse
    {
        requireNonNull(issuer, "issuer");
        requireNonNull(discoveryUrl, "discoveryUrl");
        keys = List.copyOf(keys);
    }
}
