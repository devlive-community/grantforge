// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A signing key of the authorization server, without its private half.
 *
 * @param keyId the key ID tokens name in their header
 * @param algorithm the JWS algorithm
 * @param activatedAt when it began signing
 * @param retiredAt when it stopped, or {@code null} for the active key
 * @param publishedUntil until when a retired key stays in the key set, or {@code null} for the active key
 */
public record SigningKeyView(String keyId, String algorithm, Instant activatedAt, @Nullable Instant retiredAt,
        @Nullable Instant publishedUntil)
{
    /** Checks the parts. */
    public SigningKeyView
    {
        requireNonNull(keyId, "keyId");
        requireNonNull(algorithm, "algorithm");
        requireNonNull(activatedAt, "activatedAt");
    }

    /**
     * Returns whether the key signs new tokens.
     *
     * @return {@code true} for the active key
     */
    public boolean active()
    {
        return retiredAt == null;
    }
}
