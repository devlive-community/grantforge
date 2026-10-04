// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.entity.BaseEntity;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/** A refresh token a rotation replaced, kept until it would have expired to recognise its replay. */
@Entity
@Table(name = "gf_oauth_retired_token")
public class RetiredToken
        extends BaseEntity
{
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash = "";

    @Column(name = "authorization_id", nullable = false, updatable = false, length = 64)
    private String authorizationId = "";

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt = Instant.EPOCH;

    /** For JPA. */
    protected RetiredToken()
    {
    }

    /**
     * Retires a refresh token.
     *
     * @param tokenHash the token's hash
     * @param authorizationId the authorization it belonged to
     * @param expiresAt when it would have expired
     * @return the record
     */
    public static RetiredToken of(String tokenHash, String authorizationId, Instant expiresAt)
    {
        RetiredToken token = new RetiredToken();
        token.tokenHash = requireNonNull(tokenHash, "tokenHash");
        token.authorizationId = requireNonNull(authorizationId, "authorizationId");
        token.expiresAt = requireNonNull(expiresAt, "expiresAt");
        return token;
    }

    /**
     * Returns the token's hash.
     *
     * @return the hash
     */
    public String getTokenHash()
    {
        return tokenHash;
    }

    /**
     * Returns the authorization the token belonged to.
     *
     * @return the authorization server's identifier
     */
    public String getAuthorizationId()
    {
        return authorizationId;
    }

    /**
     * Returns when the token would have expired.
     *
     * @return the time
     */
    public Instant getExpiresAt()
    {
        return expiresAt;
    }
}
