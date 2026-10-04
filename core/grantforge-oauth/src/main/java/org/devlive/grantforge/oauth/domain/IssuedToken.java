// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * A token an authorization holds, as its SHA-256 hash: the value itself is never stored.
 */
@Embeddable
public class IssuedToken
{
    @Column(name = "hash", length = 64)
    private @Nullable String hash;

    @Column(name = "issued_at")
    private @Nullable Instant issuedAt;

    @Column(name = "expires_at")
    private @Nullable Instant expiresAt;

    @Column(name = "invalidated", nullable = false)
    private boolean invalidated;

    /** For JPA and for an empty slot. */
    protected IssuedToken()
    {
    }

    /**
     * Describes a token.
     *
     * @param hash the token's hash
     * @param issuedAt when it was issued
     * @param expiresAt when it expires
     * @param invalidated whether it was revoked or used up
     */
    public IssuedToken(String hash, @Nullable Instant issuedAt, @Nullable Instant expiresAt, boolean invalidated)
    {
        this.hash = hash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.invalidated = invalidated;
    }

    /**
     * Returns an empty slot, for an authorization without such a token.
     *
     * @return the slot
     */
    public static IssuedToken none()
    {
        return new IssuedToken();
    }

    /**
     * Returns whether the slot holds a token.
     *
     * @return {@code true} if there is a token
     */
    public boolean isPresent()
    {
        return hash != null;
    }

    /**
     * Returns the token's hash.
     *
     * @return the hash, or {@code null} for an empty slot
     */
    public @Nullable String getHash()
    {
        return hash;
    }

    /**
     * Returns when the token was issued.
     *
     * @return the time, or {@code null}
     */
    public @Nullable Instant getIssuedAt()
    {
        return issuedAt;
    }

    /**
     * Returns when the token expires.
     *
     * @return the time, or {@code null}
     */
    public @Nullable Instant getExpiresAt()
    {
        return expiresAt;
    }

    /**
     * Returns whether the token was revoked or used up.
     *
     * @return {@code true} if it no longer works
     */
    public boolean isInvalidated()
    {
        return invalidated;
    }

    @Override
    public boolean equals(@Nullable Object other)
    {
        return other instanceof IssuedToken token && Objects.equals(hash, token.hash) && Objects.equals(issuedAt, token.issuedAt)
                && Objects.equals(expiresAt, token.expiresAt) && invalidated == token.invalidated;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(hash, issuedAt, expiresAt, invalidated);
    }

    @Override
    public String toString()
    {
        return hash == null ? "IssuedToken[none]" : "IssuedToken[expiresAt=" + expiresAt + ", invalidated=" + invalidated + "]";
    }
}
