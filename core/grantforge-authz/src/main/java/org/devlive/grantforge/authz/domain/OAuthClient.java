// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * An OAuth client of an application: how the application signs users in and obtains tokens. Confidential clients keep a
 * secret, stored only as a hash; after a rotation the previous secret keeps working until its grace period ends. Clients
 * are shared by all tenants, like the applications they belong to.
 */
@Entity
@Table(name = "gf_oauth_client")
public class OAuthClient
        extends BaseEntity
{
    @Column(name = "application_id", nullable = false, updatable = false)
    private long applicationId;

    @Column(name = "client_id", nullable = false, updatable = false, length = 64)
    private String clientId = "";

    @Column(name = "name", nullable = false, length = 128)
    private String name = "";

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "client_type", nullable = false, updatable = false, length = 16)
    private ClientType type = ClientType.CONFIDENTIAL;

    @Column(name = "secret_hash", length = 255)
    private @Nullable String secretHash;

    @Column(name = "previous_secret_hash", length = 255)
    private @Nullable String previousSecretHash;

    @Column(name = "previous_secret_expires_at")
    private @Nullable Instant previousSecretExpiresAt;

    @Column(name = "secret_rotated_at")
    private @Nullable Instant secretRotatedAt;

    // One row per URI: their total length exceeds the portable column size (multi-database rules).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gf_oauth_client_redirect_uri", joinColumns = @JoinColumn(name = "oauth_client_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "uri", nullable = false, length = 512)
    private List<String> redirectUris = new ArrayList<>();

    @Column(name = "scopes", nullable = false, length = 500)
    private String scopes = "";

    @Column(name = "grant_types", nullable = false, length = 200)
    private String grantTypes = "";

    @Column(name = "access_token_seconds", nullable = false)
    private long accessTokenSeconds;

    @Column(name = "refresh_token_seconds", nullable = false)
    private long refreshTokenSeconds;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** For JPA. */
    protected OAuthClient()
    {
    }

    /**
     * Creates a client; the settings are checked by the caller.
     *
     * @param applicationId the application
     * @param clientId the public identifier
     * @param type confidential or public
     * @param secretHash the hash of the secret of a confidential client, {@code null} for a public one
     * @param now the current time
     * @return the client
     * @throws IllegalArgumentException if a confidential client has no secret or a public one has
     */
    public static OAuthClient create(long applicationId, String clientId, ClientType type, @Nullable String secretHash, Instant now)
    {
        if ((type == ClientType.CONFIDENTIAL) != (secretHash != null)) {
            throw new IllegalArgumentException("confidential clients have a secret, public ones do not");
        }
        OAuthClient client = new OAuthClient();
        client.applicationId = applicationId;
        client.clientId = requireNonNull(clientId, "clientId");
        client.type = type;
        client.secretHash = secretHash;
        client.secretRotatedAt = secretHash == null ? null : requireNonNull(now, "now");
        return client;
    }

    /**
     * Sets what the client may do.
     *
     * @param newName its name
     * @param newRedirectUris where the authorization server may send users back to
     * @param newScopes what the client may ask for
     * @param newGrants how the client may obtain tokens
     * @param accessTokenTtl how long access tokens last
     * @param refreshTokenTtl how long refresh tokens last
     * @param newEnabled whether the client may obtain tokens at all
     */
    public void configure(String newName, List<String> newRedirectUris, Set<String> newScopes, Set<ClientGrant> newGrants,
            Duration accessTokenTtl, Duration refreshTokenTtl, boolean newEnabled)
    {
        this.name = requireNonNull(newName, "newName");
        this.redirectUris.clear();
        this.redirectUris.addAll(newRedirectUris);
        this.scopes = String.join(" ", new LinkedHashSet<>(newScopes));
        this.grantTypes = newGrants.stream().sorted().map(ClientGrant::name).collect(Collectors.joining(" "));
        this.accessTokenSeconds = accessTokenTtl.toSeconds();
        this.refreshTokenSeconds = refreshTokenTtl.toSeconds();
        this.enabled = newEnabled;
    }

    /**
     * Replaces the secret, keeping the current one valid for a while so the application can switch over.
     *
     * @param newSecretHash the hash of the new secret
     * @param now the current time
     * @param grace how long the current secret keeps working; zero ends it at once
     * @throws IllegalStateException if the client is public
     */
    public void rotateSecret(String newSecretHash, Instant now, Duration grace)
    {
        if (type != ClientType.CONFIDENTIAL) {
            throw new IllegalStateException("public clients have no secret");
        }
        boolean keep = !grace.isZero() && !grace.isNegative();
        this.previousSecretHash = keep ? secretHash : null;
        this.previousSecretExpiresAt = keep ? now.plus(grace) : null;
        this.secretHash = requireNonNull(newSecretHash, "newSecretHash");
        this.secretRotatedAt = now;
    }

    /**
     * Returns the application.
     *
     * @return its ID
     */
    public long getApplicationId()
    {
        return applicationId;
    }

    /**
     * Returns the public identifier.
     *
     * @return the client ID
     */
    public String getClientId()
    {
        return clientId;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns whether the client keeps a secret.
     *
     * @return the type
     */
    public ClientType getType()
    {
        return type;
    }

    /**
     * Returns the hash of the secret.
     *
     * @return the hash, or {@code null} for a public client
     */
    public @Nullable String getSecretHash()
    {
        return secretHash;
    }

    /**
     * Returns the hash of the secret before the last rotation, while it still works.
     *
     * @param now the current time
     * @return the hash, or {@code null} if there is none or its grace period has ended
     */
    public @Nullable String previousSecretHash(Instant now)
    {
        Instant until = previousSecretExpiresAt;
        return until != null && now.isBefore(until) ? previousSecretHash : null;
    }

    /**
     * Returns when the previous secret stops working.
     *
     * @return the time, or {@code null} if there is no previous secret
     */
    public @Nullable Instant getPreviousSecretExpiresAt()
    {
        return previousSecretExpiresAt;
    }

    /**
     * Returns when the secret was last set.
     *
     * @return the time, or {@code null} for a public client
     */
    public @Nullable Instant getSecretRotatedAt()
    {
        return secretRotatedAt;
    }

    /**
     * Returns where users may be sent back to.
     *
     * @return the redirect URIs
     */
    public List<String> getRedirectUris()
    {
        return List.copyOf(redirectUris);
    }

    /**
     * Returns what the client may ask for.
     *
     * @return the scopes, in the order they were set
     */
    public Set<String> getScopes()
    {
        return scopes.isEmpty() ? Set.of() : new LinkedHashSet<>(List.of(scopes.split(" ")));
    }

    /**
     * Returns how the client may obtain tokens.
     *
     * @return the grants
     */
    public Set<ClientGrant> getGrants()
    {
        Set<ClientGrant> grants = EnumSet.noneOf(ClientGrant.class);
        if (!grantTypes.isEmpty()) {
            Arrays.stream(grantTypes.split(" ")).map(ClientGrant::valueOf).forEach(grants::add);
        }
        return grants;
    }

    /**
     * Returns how long access tokens last.
     *
     * @return the lifetime
     */
    public Duration getAccessTokenTtl()
    {
        return Duration.ofSeconds(accessTokenSeconds);
    }

    /**
     * Returns how long refresh tokens last.
     *
     * @return the lifetime
     */
    public Duration getRefreshTokenTtl()
    {
        return Duration.ofSeconds(refreshTokenSeconds);
    }

    /**
     * Returns whether the client may obtain tokens.
     *
     * @return {@code true} if enabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }
}
