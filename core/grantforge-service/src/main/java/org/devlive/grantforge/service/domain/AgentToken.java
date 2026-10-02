// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A token agents of a service sign in with. Only a hash of the token is stored, with its first characters to tell
 * tokens apart; a revoked or expired token signs no one in.
 */
@Entity
@Table(name = "gf_agent_token")
public class AgentToken
        extends TenantScopedEntity
{
    @Column(name = "service_id", nullable = false, updatable = false)
    private long serviceId;

    @Column(name = "name", nullable = false, length = 64)
    private String name = "";

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash = "";

    @Column(name = "token_hint", nullable = false, updatable = false, length = 16)
    private String tokenHint = "";

    @Column(name = "expires_at", updatable = false)
    private @Nullable Instant expiresAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    @Column(name = "last_used_at")
    private @Nullable Instant lastUsedAt;

    /** For JPA. */
    protected AgentToken()
    {
    }

    /**
     * Creates a token.
     *
     * @param serviceId the service whose agents use it
     * @param name what it is for, such as the cluster it is deployed to
     * @param tokenHash the SHA-256 hash of the token, hexadecimal
     * @param tokenHint the token's first characters
     * @param expiresAt when it stops working, or {@code null} for never
     * @return the token
     */
    public static AgentToken create(long serviceId, String name, String tokenHash, String tokenHint, @Nullable Instant expiresAt)
    {
        AgentToken token = new AgentToken();
        token.serviceId = serviceId;
        token.name = requireNonNull(name, "name");
        token.tokenHash = requireNonNull(tokenHash, "tokenHash");
        token.tokenHint = requireNonNull(tokenHint, "tokenHint");
        token.expiresAt = expiresAt;
        return token;
    }

    /**
     * Returns whether the token signs agents in at a moment.
     *
     * @param now the moment
     * @return {@code true} unless revoked or expired
     */
    public boolean isUsableAt(Instant now)
    {
        return revokedAt == null && (expiresAt == null || now.isBefore(expiresAt));
    }

    /**
     * Revokes the token; revoking again changes nothing.
     *
     * @param now when
     */
    public void revoke(Instant now)
    {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /**
     * Notes that an agent signed in with the token.
     *
     * @param now when
     */
    public void used(Instant now)
    {
        lastUsedAt = now;
    }

    /**
     * Returns the service whose agents use the token.
     *
     * @return the service's id
     */
    public long getServiceId()
    {
        return serviceId;
    }

    /**
     * Returns what the token is for.
     *
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the token's first characters.
     *
     * @return the hint
     */
    public String getTokenHint()
    {
        return tokenHint;
    }

    /**
     * Returns when the token stops working.
     *
     * @return the moment, or {@code null} for never
     */
    public @Nullable Instant getExpiresAt()
    {
        return expiresAt;
    }

    /**
     * Returns when the token was revoked.
     *
     * @return the moment, or {@code null}
     */
    public @Nullable Instant getRevokedAt()
    {
        return revokedAt;
    }

    /**
     * Returns when an agent last signed in with the token.
     *
     * @return the moment, or {@code null} if never
     */
    public @Nullable Instant getLastUsedAt()
    {
        return lastUsedAt;
    }
}
