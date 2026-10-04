// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import org.devlive.grantforge.oauth.application.SigningKeyView;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A signing key of the authorization server.
 *
 * @param keyId the key ID tokens name in their header
 * @param algorithm the JWS algorithm
 * @param activatedAt when it began signing
 * @param retiredAt when it stopped, or {@code null} for the active key
 * @param publishedUntil until when a retired key stays in the key set, or {@code null} for the active key
 * @param active whether it signs new tokens
 */
public record OAuthSigningKeyResponse(String keyId, String algorithm, Instant activatedAt, @Nullable Instant retiredAt,
        @Nullable Instant publishedUntil, boolean active)
{
    /**
     * Converts a view.
     *
     * @param key the view
     * @return the response
     */
    public static OAuthSigningKeyResponse from(SigningKeyView key)
    {
        return new OAuthSigningKeyResponse(key.keyId(), key.algorithm(), key.activatedAt(), key.retiredAt(), key.publishedUntil(),
                key.active());
    }
}
