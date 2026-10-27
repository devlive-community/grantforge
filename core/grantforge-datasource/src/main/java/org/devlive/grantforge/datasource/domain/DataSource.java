// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * DataSource owned by one tenant's organization data source.
 */
@Entity
@Table(name = "gf_data_source")
public class DataSource extends TenantScopedEntity
{
    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code = "";

    @Column(name = "name", nullable = false, length = 128)
    private String name = "";

    @Column(name = "source_type", nullable = false, updatable = false, length = 32)
    private String type = "";

    @Column(name = "identity_source_id", nullable = false)
    private long identitySourceId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "login_updates", nullable = false)
    private boolean loginUpdates = true;

    @Column(name = "settings", nullable = false, length = 4000)
    private String settings = "{}";

    @Column(name = "parent_unit_id")
    private @Nullable Long parentUnitId;

    @Column(name = "secret", length = 1024)
    private @Nullable String secret;

    @Column(name = "sync_interval_minutes")
    private @Nullable Integer syncIntervalMinutes;

    @Column(name = "last_synced_at")
    private @Nullable Instant lastSyncedAt;

    @Column(name = "last_sync_summary", length = 512)
    private @Nullable String lastSyncSummary;

    @Column(name = "directory_scope", length = 64)
    private @Nullable String directoryScope;

    @Column(name = "sync_lease", length = 36)
    private @Nullable String syncLease;

    @Column(name = "sync_lease_until")
    private @Nullable Instant syncLeaseUntil;

    /**
     * For JPA.
     */
    protected DataSource()
    {
    }

    /**
     * Creates a provider configuration.
     *
     * @param code the immutable tenant code
     * @param type the provider type
     * @return the source
     */
    public static DataSource create(String code, String type)
    {
        DataSource source = new DataSource();
        source.code = code;
        source.type = type;
        return source;
    }

    /**
     * Updates configuration; callers validate ownership and immutable settings.
     *
     * @param name         the display name
     * @param identity     the linked OIDC source
     * @param enabled      whether sync is enabled
     * @param loginUpdates whether sign-ins update memberships
     * @param settings     the provider configuration JSON
     * @param parent       the local mount
     * @param interval     the schedule
     */
    public void configure(String name, long identity, boolean enabled, boolean loginUpdates, String settings, @Nullable Long parent, @Nullable Integer interval)
    {
        this.name = name;
        this.identitySourceId = identity;
        this.enabled = enabled;
        this.loginUpdates = loginUpdates;
        this.settings = settings;
        this.parentUnitId = parent;
        this.syncIntervalMinutes = interval;
    }

    /**
     * Stores an encrypted credential.
     *
     * @param secret the encrypted credential
     */
    public void storeSecret(@Nullable String secret)
    {
        this.secret = secret;
    }

    /**
     * Tests the distributed lease.
     *
     * @param now the current instant
     * @return whether another fetch owns the lease
     */
    public boolean syncRunning(Instant now)
    {
        return syncLease != null && syncLeaseUntil != null && syncLeaseUntil.isAfter(now);
    }

    /**
     * Claims a lease while holding the source row lock.
     *
     * @param token the owner
     * @param until the deadline
     */
    public void claimSync(String token, Instant until)
    {
        syncLease = token;
        syncLeaseUntil = until;
    }

    /**
     * Checks the lease fence.
     *
     * @param token the owner
     * @param now   the current instant
     * @return whether the owner can apply its snapshot
     */
    public boolean ownsSync(String token, Instant now)
    {
        return token.equals(syncLease) && syncRunning(now);
    }

    /**
     * Releases only the matching lease.
     *
     * @param token the owner
     */
    public void releaseSync(String token)
    {
        if (token.equals(syncLease)) {
            syncLease = null;
            syncLeaseUntil = null;
        }
    }

    /**
     * Records the last attempt without exposing credentials.
     *
     * @param at      the attempt time
     * @param summary the safe summary
     */
    public void synced(Instant at, String summary)
    {
        lastSyncedAt = at;
        lastSyncSummary = summary.substring(0, Math.min(512, summary.length()));
    }

    /**
     * Remembers the approved authorization scope.
     *
     * @param scope the fingerprint
     */
    public void approveScope(String scope)
    {
        directoryScope = scope;
    }

    /**
     * Returns code.
     *
     * @return the value
     */
    public String getCode()
    {
        return code;
    }

    /**
     * Returns name.
     *
     * @return the value
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns type.
     *
     * @return the value
     */
    public String getType()
    {
        return type;
    }

    /**
     * Returns identitySourceId.
     *
     * @return the value
     */
    public long getIdentitySourceId()
    {
        return identitySourceId;
    }

    /**
     * Returns enabled.
     *
     * @return the value
     */
    public boolean getEnabled()
    {
        return enabled;
    }

    /**
     * Returns loginUpdates.
     *
     * @return the value
     */
    public boolean getLoginUpdates()
    {
        return loginUpdates;
    }

    /**
     * Returns settings.
     *
     * @return the value
     */
    public String getSettings()
    {
        return settings;
    }

    /**
     * Returns parentUnitId.
     *
     * @return the value
     */
    public @Nullable Long getParentUnitId()
    {
        return parentUnitId;
    }

    /**
     * Returns secret.
     *
     * @return the value
     */
    public @Nullable String getSecret()
    {
        return secret;
    }

    /**
     * Returns syncIntervalMinutes.
     *
     * @return the value
     */
    public @Nullable Integer getSyncIntervalMinutes()
    {
        return syncIntervalMinutes;
    }

    /**
     * Returns lastSyncedAt.
     *
     * @return the value
     */
    public @Nullable Instant getLastSyncedAt()
    {
        return lastSyncedAt;
    }

    /**
     * Returns lastSyncSummary.
     *
     * @return the value
     */
    public @Nullable String getLastSyncSummary()
    {
        return lastSyncSummary;
    }

    /**
     * Returns directoryScope.
     *
     * @return the value
     */
    public @Nullable String getDirectoryScope()
    {
        return directoryScope;
    }

    /**
     * Returns syncLease.
     *
     * @return the value
     */
    public @Nullable String getSyncLease()
    {
        return syncLease;
    }

    /**
     * Returns syncLeaseUntil.
     *
     * @return the value
     */
    public @Nullable Instant getSyncLeaseUntil()
    {
        return syncLeaseUntil;
    }
}
